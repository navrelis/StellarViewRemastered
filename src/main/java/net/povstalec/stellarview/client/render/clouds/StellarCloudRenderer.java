package net.povstalec.stellarview.client.render.clouds;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FluidState;
import net.povstalec.stellarview.StellarView;
import net.povstalec.stellarview.compatibility.iris.IrisCompatibility;
import org.jetbrains.annotations.Nullable;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

import java.util.Locale;

/**
 * Stellar View's cloud renderer: soft, translucent cumulus built from many overlapping instanced boxes.
 * <p>
 * Entry point is {@link #renderClouds}, called at the head of {@code LevelRenderer.renderClouds}.
 * It returns true when Vanilla's (and Sodium's) clouds must be cancelled, which happens only when Stellar View draws,
 * or when the camera is inside a fluid (then nobody draws clouds). In every other case Vanilla runs untouched.
 * <p>
 * Frame path (render thread): gates, take a finished window from the generator, ask for a new window if needed,
 * cull the window's groups, then inside one saved and restored GL state scope: create or resize GPU objects,
 * upload a new window once, accumulate the boxes and resolve them into the destination.
 */
public final class StellarCloudRenderer
{
	private static final String BETTER_CLOUDS_MODID = "betterclouds";

	/** Vanilla's cloud speed in blocks per tick */
	private static final double VANILLA_SPEED = 0.03;

	// Shape at 100 % cloud size
	private static final double BASE_PITCH = 8.0;
	private static final float BASE_HALF_WIDTH = 10.0F;
	private static final double BASE_FEATURE_SCALE = 180.0;
	private static final float BASE_MIN_HEIGHT = 5.0F;
	/** Box scale at the edge of the cloud radius */
	private static final float FAR_SHRINK = 0.35F;
	/** Fraction of the fade distance over which the far clouds fade out */
	private static final float FADE_LENGTH = 0.3F;

	/** Upper bound for the cells of one window, protects against extreme distance and size settings */
	private static final int MAX_CELLS = 200_000;
	private static final double MIN_MARGIN = 48.0;
	private static final double MAX_MARGIN = 192.0;

	private enum Status
	{
		ACTIVE("drawing clouds"),
		DISABLED("turned off in the settings, Vanilla clouds are used"),
		OTHER_MOD("Better Clouds is installed, the clouds are left to it"),
		DIMENSION_EFFECTS("this dimension has no Vanilla style cloud layer, it is left alone"),
		DIMENSION_RENDERER("this dimension registers its own cloud renderer, it is left alone"),
		SHADER_PACK("an Iris shader pack is active, the clouds are left to the pack"),
		UNSUPPORTED("the GPU lacks instanced arrays, Vanilla clouds are used"),
		FAILED("disabled after an error, Vanilla clouds are used for this session"),
		SUBMERGED("the camera is in a fluid or powder snow, no clouds are drawn");

		private final String description;

		Status(String description)
		{
			this.description = description;
		}
	}

	private static final CloudGenerator GENERATOR = new CloudGenerator();
	private static final CloudGpu GPU = new CloudGpu();
	private static final CloudSky SKY = new CloudSky();
	private static final GlStateSnapshot STATE = new GlStateSnapshot();

	private static final Matrix4f VIEW_PROJ = new Matrix4f();
	private static final Matrix4f INVERSE_VIEW_PROJ = new Matrix4f();
	private static final FrustumIntersection FRUSTUM = new FrustumIntersection();

	private static boolean otherCloudMod = false;
	private static Status status = null;

	private static boolean capabilityChecked = false;
	private static boolean capable = false;
	private static boolean arbDivisor = false;
	private static boolean failed = false;
	private static boolean activeLogged = false;

	// World state
	@Nullable
	private static ClientLevel trackedLevel = null;
	private static long seed = 0;
	private static double driftX = 0.0;
	private static double driftZ = 0.0;
	private static double lastClock = Double.NaN;

	/** The window being drawn. Replaced only on the render thread */
	@Nullable
	private static CloudMesh mesh = null;
	private static int[] runStart = new int[0];
	private static int[] runCount = new int[0];

	private StellarCloudRenderer() {}

	/**
	 * Called once from the client entry point
	 */
	public static void init()
	{
		otherCloudMod = FabricLoader.getInstance().isModLoaded(BETTER_CLOUDS_MODID);
		if(otherCloudMod)
		{
			status = Status.OTHER_MOD;
			StellarView.LOGGER.info("Better Clouds is installed, Stellar View will not draw clouds");
		}
	}

	/**
	 * @return True if Vanilla's cloud rendering must be cancelled
	 */
	public static boolean renderClouds(LevelRenderer levelRenderer, @Nullable ClientLevel level, PoseStack poseStack, Matrix4f modelViewMatrix, Matrix4f projectionMatrix,
									   float partialTick, double camX, double camY, double camZ)
	{
		if(level == null)
			return false;

		Minecraft minecraft = Minecraft.getInstance();
		Camera camera = minecraft.gameRenderer.getMainCamera();

		Status gate = gate(level, camera);
		if(gate != null)
		{
			setStatus(gate);
			return gate == Status.SUBMERGED;
		}

		try
		{
			draw(minecraft, levelRenderer, level, camera, poseStack, modelViewMatrix, projectionMatrix, partialTick, camX, camY, camZ);
			setStatus(Status.ACTIVE);
			return true;
		}
		catch(CloudSetupException e)
		{
			fail("Stellar View clouds could not be set up: " + e.getMessage(), null);
			return false;
		}
		catch(RuntimeException | LinkageError e)
		{
			fail("Stellar View clouds failed while drawing", e);
			return false;
		}
	}

	/**
	 * Called before the level renderer switches to another level (or to none)
	 */
	public static void onLevelChanged()
	{
		resetWorld(null);
		releaseGpu();
	}

	/**
	 * Called after a resource reload. GPU objects are made again on the next frame, the current window is kept.
	 */
	public static void onResourceReload()
	{
		releaseGpu();
	}

	/**
	 * Called when the level renderer is closed
	 */
	public static void onClose()
	{
		resetWorld(null);
		releaseGpu();
	}

	//============================================================================================
	//*******************************************Gating*******************************************
	//============================================================================================

	/**
	 * @return The reason not to draw, or null to draw
	 */
	@Nullable
	private static Status gate(ClientLevel level, Camera camera)
	{
		if(!CloudSettings.enabled())
			return Status.DISABLED;

		if(otherCloudMod)
			return Status.OTHER_MOD;

		if(failed)
			return Status.FAILED;

		// Only Vanilla style overworld effects: dimensions with their own effects class may draw their own clouds
		// through Porting Lib or Sporran handlers on the same method, which must not depend on handler order
		DimensionSpecialEffects effects = level.effects();
		if(!(effects instanceof DimensionSpecialEffects.OverworldEffects) || Float.isNaN(effects.getCloudHeight()))
			return Status.DIMENSION_EFFECTS;

		if(DimensionRenderingRegistry.getCloudRenderer(level.dimension()) != null)
			return Status.DIMENSION_RENDERER;

		if(IrisCompatibility.isShaderPackInUse())
			return Status.SHADER_PACK;

		if(!isCapable())
			return Status.UNSUPPORTED;

		if(isSubmerged(level, camera))
			return Status.SUBMERGED;

		return null;
	}

	private static boolean isCapable()
	{
		if(!capabilityChecked)
		{
			capabilityChecked = true;
			GLCapabilities capabilities = GL.getCapabilities();
			capable = capabilities.OpenGL32 && (capabilities.OpenGL33 || capabilities.GL_ARB_instanced_arrays);
			arbDivisor = !capabilities.OpenGL33;

			if(!capable)
				StellarView.LOGGER.warn("Stellar View clouds need OpenGL 3.3 or ARB_instanced_arrays, Vanilla clouds are used");
		}

		return capable;
	}

	/**
	 * Allocation free version of the camera fluid test: water and lava at the camera's block, and powder snow
	 */
	private static boolean isSubmerged(ClientLevel level, Camera camera)
	{
		BlockPos position = camera.getBlockPosition();
		double eyeY = camera.getPosition().y;
		FluidState fluid = level.getFluidState(position);

		if(fluid.is(FluidTags.WATER))
			return eyeY < position.getY() + fluid.getHeight(level, position);

		if(fluid.is(FluidTags.LAVA))
			return eyeY <= position.getY() + fluid.getHeight(level, position);

		return level.getBlockState(position).is(Blocks.POWDER_SNOW);
	}

	private static void setStatus(Status next)
	{
		if(next == status)
			return;

		boolean quiet = next == Status.SUBMERGED || (status == Status.SUBMERGED && next == Status.ACTIVE);
		status = next;

		// Stepping aside for longer (shader pack, settings, dimension): free the GPU memory, keep the window for a quick return
		if(next != Status.ACTIVE && next != Status.SUBMERGED)
			releaseGpu();

		if(quiet)
			StellarView.LOGGER.debug("Stellar View clouds: {}", next.description);
		else
			StellarView.LOGGER.info("Stellar View clouds: {}", next.description);
	}

	private static void fail(String message, @Nullable Throwable error)
	{
		failed = true;
		if(error != null)
			StellarView.LOGGER.error(message + ", Vanilla clouds are used for this session", error);
		else
			StellarView.LOGGER.error(message + ", Vanilla clouds are used for this session");

		resetWorld(null);
		releaseGpu();
	}

	//============================================================================================
	//****************************************World state*****************************************
	//============================================================================================

	private static void resetWorld(@Nullable ClientLevel level)
	{
		GENERATOR.invalidate();
		mesh = null;
		trackedLevel = level;
		seed = level != null ? worldSeed(level) : 0;
		driftX = 0.0;
		driftZ = 0.0;
		lastClock = Double.NaN;
		activeLogged = false;
	}

	private static void releaseGpu()
	{
		if(RenderSystem.isOnRenderThread())
			GPU.delete();
		else
			RenderSystem.recordRenderCall(GPU::delete);
	}

	/**
	 * A seed that every client of the same world or server sees, without waiting for any server data:
	 * the world seed in singleplayer, the server address in multiplayer, mixed with the dimension id.
	 */
	private static long worldSeed(ClientLevel level)
	{
		Minecraft minecraft = Minecraft.getInstance();
		String identity;

		IntegratedServer server = minecraft.getSingleplayerServer();
		ServerData serverData = minecraft.getCurrentServer();
		if(server != null)
			identity = "world:" + server.getWorldData().worldGenOptions().seed();
		else if(serverData != null)
			identity = "server:" + serverData.ip.trim().toLowerCase(Locale.ROOT);
		else
			identity = "unknown";

		return CloudNoise.hash(CloudNoise.hashString(identity), CloudNoise.hashString(level.dimension().location().toString()), 0x2545F4914F6CDD1DL);
	}

	/**
	 * Integrates the wind. Uses the world's game time (world age), which neither freezes with the daylight cycle
	 * nor jumps when the time of day is set. Speed changes never make the clouds jump.
	 */
	private static void updateDrift(ClientLevel level, float partialTick)
	{
		double clock = level.getGameTime() + (double) partialTick;
		double elapsed = Double.isNaN(lastClock) ? 0.0 : clock - lastClock;
		lastClock = clock;

		if(elapsed < 0.0 || elapsed > 100.0) // Pause, lag spike or a game time reset: just carry on from here
			elapsed = 0.0;

		// Mostly along +X in cloud space (so clouds move toward -X like Vanilla's), with a slow meander
		double heading = 0.35 * Math.sin(clock * (Math.PI * 2.0 / 24000.0)) + 0.2 * Math.sin(clock * (Math.PI * 2.0 / 8700.0) + 1.3);
		double distance = VANILLA_SPEED * CloudSettings.driftSpeed() / 100.0 * elapsed;
		driftX += distance * Math.cos(heading);
		driftZ += distance * Math.sin(heading);
	}

	//============================================================================================
	//*******************************************Drawing******************************************
	//============================================================================================

	private static void draw(Minecraft minecraft, LevelRenderer levelRenderer, ClientLevel level, Camera camera, PoseStack poseStack, Matrix4f modelViewMatrix, Matrix4f projectionMatrix,
							 float partialTick, double camX, double camY, double camZ) throws CloudSetupException
	{
		if(level != trackedLevel)
			resetWorld(level);

		CloudMesh finished = GENERATOR.poll();
		if(finished != null)
		{
			if(finished.failed)
				throw new CloudSetupException("the cloud generator failed (see the error above)");

			acceptMesh(finished);
		}

		boolean flat = minecraft.options.getCloudsType() == CloudStatus.FAST;
		float renderDistance = minecraft.gameRenderer.getRenderDistance();
		float depthFar = minecraft.gameRenderer.getDepthFar();

		double size = CloudSettings.cloudSize() / 100.0;
		double pitch = BASE_PITCH * size;
		double reach = Math.min(renderDistance * CloudSettings.distance() / 100.0, depthFar * 0.9);

		updateDrift(level, partialTick);
		double cloudX = camX + driftX;
		double cloudZ = camZ + driftZ;

		requestWindow(size, pitch, reach, cloudX, cloudZ);

		CloudMesh current = mesh;
		if(current == null)
			return; // The first window is still being made: no clouds for a frame or two, Vanilla stays cancelled

		CloudRequest window = current.request;
		float meshSize = (float) (window.pitch() / BASE_PITCH);
		float halfWidth = BASE_HALF_WIDTH * meshSize;
		float minHeight = flat ? 0.0F : BASE_MIN_HEIGHT * meshSize;
		float thickness = flat ? 0.0F : CloudSettings.thickness();

		float originX = (float) (window.originX() - cloudX);
		float originZ = (float) (window.originZ() - cloudZ);
		// The dimension's cloud height already includes Sodium Extra's and Polytone's changes; our offset is added once, here
		float originY = (float) (level.effects().getCloudHeight() + CloudSettings.heightOffset() - camY);
		float top = (minHeight + thickness) * 1.05F + 4.0F;

		VIEW_PROJ.set(projectionMatrix).mul(poseStack.last().pose()).mul(modelViewMatrix);
		FRUSTUM.set(VIEW_PROJ, false);

		int runs = collectRuns(current, originX, originY, originZ, top);
		if(runs == 0)
			return; // Nothing in view: no GL work at all

		SKY.update(level, camera, partialTick, renderDistance);
		VIEW_PROJ.invert(INVERSE_VIEW_PROJ);

		float visibleReach = (float) window.visibleReach();
		float fadeEnd = Math.min(visibleReach * SKY.fogReach, depthFar * 0.92F);
		float fadeStart = fadeEnd * (1.0F - FADE_LENGTH);
		float haze = 0.55F + 0.35F * (1.0F - SKY.fogReach);
		float time = (float) (((level.getGameTime() % 24000L) + partialTick) / 20.0);

		RenderTarget mainTarget = minecraft.getMainRenderTarget();
		RenderTarget cloudsTarget = levelRenderer.getCloudsTarget();
		int destination = cloudsTarget != null ? cloudsTarget.frameBufferId : mainTarget.frameBufferId;

		STATE.capture();
		try
		{
			GPU.ensureReady(mainTarget, arbDivisor);
			GPU.upload(current);
			GPU.drawVolume(STATE, VIEW_PROJ, originX, originY, originZ, halfWidth, minHeight, thickness, flat,
					SKY, visibleReach, fadeStart, fadeEnd, FAR_SHRINK, time, runStart, runCount, runs);
			GPU.drawResolve(destination, cloudsTarget != null, VIEW_PROJ, INVERSE_VIEW_PROJ, SKY, visibleReach, haze);
		}
		finally
		{
			STATE.restore();
		}

		if(!activeLogged)
		{
			activeLogged = true;
			StellarView.LOGGER.info("Stellar View clouds active: {} boxes in {} groups, {} mode, GL path: {} instanced boxes, RGBA16F order independent accumulation, {} target, about {} KiB GPU memory",
					current.boxCount, current.groupCount, flat ? "flat (Fast)" : "volumetric (Fancy)", arbDivisor ? "ARB" : "GL 3.3",
					cloudsTarget != null ? "Fabulous clouds" : "main", GPU.memoryBytes() / 1024L);
		}
	}

	private static void acceptMesh(CloudMesh next)
	{
		mesh = next;
		if(runStart.length < next.groupCount)
		{
			runStart = new int[next.groupCount];
			runCount = new int[next.groupCount];
		}
	}

	/**
	 * Submits a new window when there is none, when the shape changed or when the camera left the current one.
	 * Waits (keeps drawing the current window) while a job is in flight.
	 */
	private static void requestWindow(double size, double pitch, double reach, double cloudX, double cloudZ)
	{
		if(GENERATOR.inFlight() != null)
			return;

		CloudMesh current = mesh;
		if(current != null && current.request.sameShape(seed, pitch, reach) && current.request.covers(cloudX, cloudZ))
			return;

		float boxHalfWidth = (float) (BASE_HALF_WIDTH * size * 1.25); // Largest random size and breathing
		double margin = Math.max(MIN_MARGIN, Math.min(MAX_MARGIN, reach * 0.12));
		double extent = reach + margin + boxHalfWidth * 2.0;
		double maxExtent = Math.sqrt(MAX_CELLS) * pitch * 0.5;
		if(extent > maxExtent)
			extent = maxExtent;
		double visibleReach = Math.max(pitch, extent - margin - boxHalfWidth * 2.0);

		int group = CloudGenerator.GROUP_SIZE;
		long groupMinX = Math.floorDiv((long) Math.floor((cloudX - extent) / pitch), group);
		long groupMinZ = Math.floorDiv((long) Math.floor((cloudZ - extent) / pitch), group);
		long groupMaxX = Math.floorDiv((long) Math.floor((cloudX + extent) / pitch), group);
		long groupMaxZ = Math.floorDiv((long) Math.floor((cloudZ + extent) / pitch), group);

		CloudRequest request = new CloudRequest(GENERATOR.nextId(), GENERATOR.epoch(), seed, pitch, BASE_FEATURE_SCALE * size, boxHalfWidth,
				groupMinX * group, groupMinZ * group, (int) (groupMaxX - groupMinX + 1), (int) (groupMaxZ - groupMinZ + 1),
				cloudX, cloudZ, reach, visibleReach, extent, margin);
		GENERATOR.submit(request);
	}

	/**
	 * Frustum culls the groups and merges neighbouring visible groups into runs of consecutive boxes
	 * @return Number of runs
	 */
	private static int collectRuns(CloudMesh current, float originX, float originY, float originZ, float top)
	{
		int runs = 0;
		int openStart = -1;
		int openEnd = -1;
		float[] bounds = current.groupBounds;

		for(int group = 0; group < current.groupCount; group++)
		{
			int b = group * CloudMesh.FLOATS_PER_BOUNDS;
			boolean visible = FRUSTUM.testAab(bounds[b] + originX, originY, bounds[b + 1] + originZ,
					bounds[b + 2] + originX, originY + top, bounds[b + 3] + originZ);

			if(!visible)
				continue;

			int start = current.groupStart[group];
			int end = start + current.groupSize[group];
			if(start == openEnd)
			{
				openEnd = end;
			}
			else
			{
				if(openStart >= 0)
				{
					runStart[runs] = openStart;
					runCount[runs] = openEnd - openStart;
					runs++;
				}
				openStart = start;
				openEnd = end;
			}
		}

		if(openStart >= 0)
		{
			runStart[runs] = openStart;
			runCount[runs] = openEnd - openStart;
			runs++;
		}

		return runs;
	}
}
