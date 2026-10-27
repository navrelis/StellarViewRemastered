package net.povstalec.stellarview.client.render.clouds;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBInstancedArrays;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;

import java.nio.FloatBuffer;

/**
 * Owns every OpenGL object of the cloud renderer and issues the two passes.
 * <p>
 * Pass 1 (volume): all visible boxes are drawn instanced into a private RGBA16F target whose depth attachment is the
 * main framebuffer's depth texture (depth test on, depth writes off, so terrain hides clouds with early depth
 * rejection). Blending is order independent: RGB adds up (shade, weight, distance, each times a weight),
 * alpha multiplies up the transmittance. No sorting is needed and the result does not depend on draw order.
 * <p>
 * Pass 2 (resolve): a full screen triangle turns the sums into colour and opacity and blends them over the
 * destination (the clouds target in Fabulous, else the main target). It runs twice: once for pixels that are at least
 * half opaque, which also write a cloud depth, and once for the faint rest, which writes no depth at all.
 * <p>
 * Every method must be called on the render thread inside a {@link GlStateSnapshot} capture.
 */
final class CloudGpu
{
	/**
	 * A unit box [0, 1]^3 as one 14 vertex triangle strip, counter-clockwise seen from outside on all 12 triangles
	 */
	private static final float[] BOX_STRIP = {
			1, 1, 1,	0, 1, 1,	1, 0, 1,	0, 0, 1,	0, 0, 0,	0, 1, 1,	0, 1, 0,
			1, 1, 1,	1, 1, 0,	1, 0, 1,	1, 0, 0,	0, 0, 0,	1, 1, 0,	0, 1, 0
	};
	private static final int BOX_VERTICES = 14;

	private static final int BOX_ATTRIBUTE = 1;

	private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
	private final FloatBuffer clearValue = BufferUtils.createFloatBuffer(4);

	private boolean created = false;

	private CloudProgram volume;
	private CloudProgram resolve;

	private int boxStripBuffer;
	private int boxBuffer;
	private int volumeArray;
	private int emptyArray;

	private int framebuffer;
	private int accumulationTexture;
	private int textureWidth = -1;
	private int textureHeight = -1;
	private int attachedDepthTexture = -1;

	/** The mesh whose boxes are in the box buffer, compared by identity */
	private CloudMesh uploadedMesh = null;

	// Volume pass uniforms
	private int volumeViewProj;
	private int volumeOrigin;
	private int volumeShape;
	private int volumeCover;
	private int volumeReach;
	private int volumeLight;
	private int volumeTime;

	// Resolve pass uniforms
	private int resolveViewProj;
	private int resolveInverseViewProj;
	private int resolveSun;
	private int resolveSky;
	private int resolveWeather;
	private int resolveGrade;
	private int resolveFog;

	CloudGpu()
	{
		clearValue.put(0, 0.0F).put(1, 0.0F).put(2, 0.0F).put(3, 1.0F);
	}

	/**
	 * Creates missing objects and resizes the accumulation target to the main target.
	 * @throws CloudSetupException if a shader fails, the framebuffer is incomplete or the driver reports an error
	 */
	void ensureReady(RenderTarget mainTarget, boolean arbDivisor) throws CloudSetupException
	{
		if(!created)
			create(arbDivisor);

		if(mainTarget.width != textureWidth || mainTarget.height != textureHeight)
		{
			GlStateManager._bindTexture(accumulationTexture);
			GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, mainTarget.width, mainTarget.height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, null);
			textureWidth = mainTarget.width;
			textureHeight = mainTarget.height;
			attachedDepthTexture = -1; // Check completeness again
		}

		int depthTexture = mainTarget.getDepthTextureId();
		if(depthTexture != attachedDepthTexture)
		{
			GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
			GlStateManager._glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depthTexture, 0);
			int status = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
			if(status != GL30.GL_FRAMEBUFFER_COMPLETE)
				throw new CloudSetupException("The cloud framebuffer is incomplete (status 0x" + Integer.toHexString(status) + ")");

			attachedDepthTexture = depthTexture;
		}
	}

	/**
	 * Uploads the boxes of a mesh unless they already are in the buffer
	 */
	void upload(CloudMesh mesh)
	{
		if(uploadedMesh == mesh)
			return;

		GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, boxBuffer);
		// Respecifying the whole store lets the driver orphan the old one instead of waiting for frames still using it
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, mesh.boxes, GL15.GL_STATIC_DRAW);
		uploadedMesh = mesh;
	}

	/**
	 * Pass 1: accumulates the visible box runs
	 */
	void drawVolume(GlStateSnapshot state, Matrix4f viewProj, float originX, float originY, float originZ,
					float halfWidth, float minHeight, float thickness, boolean flat,
					CloudSky sky, float reach, float fadeStart, float fadeEnd, float farShrink, float time,
					int[] runStart, int[] runCount, int runs)
	{
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
		GlStateManager._colorMask(true, true, true, true);
		GL30.glClearBufferfv(GL11.GL_COLOR, 0, clearValue);

		GlStateManager._enableDepthTest();
		GlStateManager._depthFunc(GL11.GL_LESS);
		GlStateManager._depthMask(false);
		GlStateManager._enableCull();
		GlStateManager._enableBlend();
		GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_ALPHA);
		if(state.needsBlendEquationChange())
			GL14.glBlendEquation(GL14.GL_FUNC_ADD);

		GlStateManager._glUseProgram(volume.id());
		viewProj.get(matrixBuffer);
		GL20.glUniformMatrix4fv(volumeViewProj, false, matrixBuffer);
		GL20.glUniform3f(volumeOrigin, originX, originY, originZ);
		GL20.glUniform4f(volumeShape, halfWidth, minHeight, thickness, flat ? 1.0F : 0.0F);
		GL20.glUniform4f(volumeCover, sky.threshold, 0.07F, CloudGenerator.EDGE_FUZZ, sky.boxAlpha);
		GL20.glUniform4f(volumeReach, reach, fadeStart, fadeEnd, farShrink);
		GL20.glUniform4f(volumeLight, sky.lightX, sky.lightY, sky.lightZ, sky.lightStrength);
		GL20.glUniform1f(volumeTime, time);

		GlStateManager._glBindVertexArray(volumeArray);
		GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, boxBuffer);
		for(int run = 0; run < runs; run++)
		{
			// Without base instance support (GL 4.2) the instance attribute is pointed at the run's first box instead
			GlStateManager._vertexAttribPointer(BOX_ATTRIBUTE, CloudMesh.FLOATS_PER_BOX, GL11.GL_FLOAT, false, CloudMesh.BYTES_PER_BOX, (long) runStart[run] * CloudMesh.BYTES_PER_BOX);
			GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, BOX_VERTICES, runCount[run]);
		}
	}

	/**
	 * Pass 2: composites the accumulated clouds into the destination framebuffer
	 * @param sortedLayer	True for Fabulous' clouds target. Its depth only sorts the layer in the transparency
	 *                      post pass (it occludes nothing), so every cloud pixel writes depth there.
	 *                      Otherwise only mostly opaque pixels write depth into the main target.
	 */
	void drawResolve(int destination, boolean sortedLayer, Matrix4f viewProj, Matrix4f inverseViewProj, CloudSky sky, float reach, float haze)
	{
		GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination);
		GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
		// Depth stays enabled so the opaque part can write depth, but the test always passes:
		// the cloud depth is already known to be in front of the scene
		GlStateManager._depthFunc(GL11.GL_ALWAYS);

		GlStateManager._glUseProgram(resolve.id());
		viewProj.get(matrixBuffer);
		GL20.glUniformMatrix4fv(resolveViewProj, false, matrixBuffer);
		inverseViewProj.get(matrixBuffer);
		GL20.glUniformMatrix4fv(resolveInverseViewProj, false, matrixBuffer);
		GL20.glUniform3f(resolveSun, sky.sunX, sky.sunY, sky.sunZ);
		GL20.glUniform4f(resolveSky, sky.day, sky.twilight * sky.glow, sky.moonLight, sky.nightVision);
		GL20.glUniform4f(resolveWeather, sky.rain, sky.thunder, sky.flash, 0.0F);
		GL20.glUniform3f(resolveFog, sky.fogRed, sky.fogGreen, sky.fogBlue);

		GlStateManager._activeTexture(GlStateSnapshot.TEXTURE_UNIT);
		GlStateManager._bindTexture(accumulationTexture);
		GlStateManager._glBindVertexArray(emptyArray);

		GlStateManager._depthMask(true);
		if(sortedLayer)
		{
			// Every pixel: colour and depth
			GL20.glUniform4f(resolveGrade, sky.brightness, haze, reach, 2.0F);
			GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
			return;
		}

		// Mostly opaque pixels: colour and depth
		GL20.glUniform4f(resolveGrade, sky.brightness, haze, reach, 1.0F);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);

		// Faint pixels: colour only, so rain and the world border are not hidden behind thin cloud fringes
		GlStateManager._depthMask(false);
		GL20.glUniform4f(resolveGrade, sky.brightness, haze, reach, 0.0F);
		GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
	}

	/**
	 * Deletes every object. The next {@link #ensureReady} creates them again.
	 * The objects are never bound outside a draw, so deleting them changes no binding.
	 */
	void delete()
	{
		if(volume != null)
			volume.delete();
		if(resolve != null)
			resolve.delete();
		volume = null;
		resolve = null;

		if(boxStripBuffer != 0)
			GlStateManager._glDeleteBuffers(boxStripBuffer);
		if(boxBuffer != 0)
			GlStateManager._glDeleteBuffers(boxBuffer);
		if(volumeArray != 0)
			GlStateManager._glDeleteVertexArrays(volumeArray);
		if(emptyArray != 0)
			GlStateManager._glDeleteVertexArrays(emptyArray);
		if(framebuffer != 0)
			GlStateManager._glDeleteFramebuffers(framebuffer);
		if(accumulationTexture != 0)
			GlStateManager._deleteTexture(accumulationTexture);

		boxStripBuffer = 0;
		boxBuffer = 0;
		volumeArray = 0;
		emptyArray = 0;
		framebuffer = 0;
		accumulationTexture = 0;
		textureWidth = -1;
		textureHeight = -1;
		attachedDepthTexture = -1;
		uploadedMesh = null;
		created = false;
	}

	/**
	 * @return Bytes of GPU memory held (accumulation target and box buffer), for the log
	 */
	long memoryBytes()
	{
		long target = textureWidth > 0 ? (long) textureWidth * textureHeight * 8L : 0L;
		long boxes = uploadedMesh != null ? (long) uploadedMesh.boxes.length * Float.BYTES : 0L;
		return target + boxes + BOX_STRIP.length * Float.BYTES;
	}

	private void create(boolean arbDivisor) throws CloudSetupException
	{
		// Errors raised by somebody else before us must not be blamed on the clouds
		for(int i = 0; i < 16 && GL11.glGetError() != GL11.GL_NO_ERROR; i++) {}

		try
		{
			volume = CloudProgram.create("cloud_volume", "Corner", "Box");
			resolve = CloudProgram.create("cloud_resolve");

			volumeViewProj = volume.uniform("ViewProj");
			volumeOrigin = volume.uniform("Origin");
			volumeShape = volume.uniform("Shape");
			volumeCover = volume.uniform("Cover");
			volumeReach = volume.uniform("Reach");
			volumeLight = volume.uniform("Light");
			volumeTime = volume.uniform("Time");

			resolveViewProj = resolve.uniform("ViewProj");
			resolveInverseViewProj = resolve.uniform("InverseViewProj");
			resolveSun = resolve.uniform("SunDir");
			resolveSky = resolve.uniform("Sky");
			resolveWeather = resolve.uniform("Weather");
			resolveGrade = resolve.uniform("Grade");
			resolveFog = resolve.uniform("FogColor");

			GlStateManager._glUseProgram(resolve.id());
			GL20.glUniform1i(resolve.uniform("Accumulation"), GlStateSnapshot.TEXTURE_UNIT - GL13.GL_TEXTURE0);

			// Box geometry and per box data
			volumeArray = GlStateManager._glGenVertexArrays();
			GlStateManager._glBindVertexArray(volumeArray);

			boxStripBuffer = GlStateManager._glGenBuffers();
			GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, boxStripBuffer);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, BOX_STRIP, GL15.GL_STATIC_DRAW);
			GlStateManager._enableVertexAttribArray(0);
			GlStateManager._vertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 3 * Float.BYTES, 0L);

			boxBuffer = GlStateManager._glGenBuffers();
			GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, boxBuffer);
			GL15.glBufferData(GL15.GL_ARRAY_BUFFER, 0L, GL15.GL_STATIC_DRAW);
			GlStateManager._enableVertexAttribArray(BOX_ATTRIBUTE);
			GlStateManager._vertexAttribPointer(BOX_ATTRIBUTE, CloudMesh.FLOATS_PER_BOX, GL11.GL_FLOAT, false, CloudMesh.BYTES_PER_BOX, 0L);
			if(arbDivisor)
				ARBInstancedArrays.glVertexAttribDivisorARB(BOX_ATTRIBUTE, 1);
			else
				GL33.glVertexAttribDivisor(BOX_ATTRIBUTE, 1);

			// The resolve pass generates its triangle from gl_VertexID, but core profiles still need a vertex array bound
			emptyArray = GlStateManager._glGenVertexArrays();

			// Accumulation target, sized by ensureReady
			accumulationTexture = GlStateManager._genTexture();
			GlStateManager._bindTexture(accumulationTexture);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
			GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
			GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, 1, 1, 0, GL11.GL_RGBA, GL11.GL_FLOAT, null);
			textureWidth = 1;
			textureHeight = 1;

			framebuffer = GlStateManager.glGenFramebuffers();
			GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
			GlStateManager._glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, accumulationTexture, 0);

			int error = GL11.glGetError();
			if(error != GL11.GL_NO_ERROR)
				throw new CloudSetupException("OpenGL error 0x" + Integer.toHexString(error) + " while creating the cloud resources");

			created = true;
		}
		finally
		{
			if(!created)
				delete();
		}
	}
}
