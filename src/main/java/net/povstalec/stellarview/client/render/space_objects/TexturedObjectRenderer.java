package net.povstalec.stellarview.client.render.space_objects;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.povstalec.stellarview.api.common.space_objects.TexturedObject;
import net.povstalec.stellarview.client.render.LightEffects;
import net.povstalec.stellarview.client.resourcepack.ViewCenter;
import net.povstalec.stellarview.common.util.*;
import net.povstalec.stellarview.compatibility.iris.IrisCompatibility;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;

public abstract class TexturedObjectRenderer<T extends TexturedObject> extends SpaceObjectRenderer<T>
{
	public static final float DEFAULT_DISTANCE = 100.0F;
	
	// Clip coordinates may be this far outside of the view (relative to w) before a layer counts as being off screen
	private static final float OFF_SCREEN_MARGIN = 0.05F;
	
	// Reused by every layer, they hold no value outside of renderOnSphere
	private static final Quaterniond SPHERE_ROTATION = new Quaterniond();
	private static final Quaterniond SPHERE_AXIS_ROTATION = new Quaterniond();
	private static final Vector3f CORNER_00 = new Vector3f();
	private static final Vector3f CORNER_10 = new Vector3f();
	private static final Vector3f CORNER_11 = new Vector3f();
	private static final Vector3f CORNER_01 = new Vector3f();
	private static final Vector4f CLIP_POSITION = new Vector4f();
	
	protected SphericalCoords sphericalCoords = new SphericalCoords();
	
	public TexturedObjectRenderer(T texturedObject)
	{
		super(texturedObject);
	}
	
	//============================================================================================
	//*****************************************Rendering******************************************
	//============================================================================================
	
	@Override
	public void render(ViewCenter viewCenter, ClientLevel level, float partialTicks, Matrix4f modelViewMatrix, Camera camera,
					   Matrix4f projectionMatrix, boolean isFoggy, Runnable setupFog, Tesselator tesselator,
					   Vector3f parentVector, AxisRotation parentRotation)
	{
		Vector3f positionVector = getPosition(viewCenter, parentRotation, viewCenter.ticks(), partialTicks).add(parentVector); // Handles orbits 'n stuff
		
		// Add parent vector to current coords and subtract View Center coords from them to get relative coords
		lastDistance = renderedObject.getCoords().skyPosition(sphericalCoords, positionVector, level, viewCenter, DEFAULT_DISTANCE, partialTicks, true);
		
		double childRenderDistance = renderedObject.getFadeOutHandler().getMaxChildRenderDistance().toKm();
		if(childRenderDistance > lastDistance)
		{
			for(int i = 0; i < children.size(); i++)
			{
				SpaceObjectRenderer<?> child = children.get(i);
				
				// Render child behind the parent, decided only once per frame because rendering updates the distance of the child
				child.renderedBehindParent = child.lastDistance >= this.lastDistance;
				if(child.renderedBehindParent)
					child.render(viewCenter, level, partialTicks, modelViewMatrix, camera, projectionMatrix, isFoggy, setupFog, tesselator, positionVector, axisRotation());
			}
		}
		
		// If the object isn't the same we're viewing everything from and it isn't too far away, render it
		if(!viewCenter.objectEquals(this) && renderedObject.getFadeOutHandler().getFadeOutEndDistance().toKm() > lastDistance)
			renderTextureLayers(viewCenter, level, camera, tesselator, modelViewMatrix, sphericalCoords, viewCenter.ticks(), lastDistance, partialTicks);
		
		if(childRenderDistance > lastDistance)
		{
			for(int i = 0; i < children.size(); i++)
			{
				SpaceObjectRenderer<?> child = children.get(i);
				
				// Render child in front of the parent
				if(!child.renderedBehindParent)
					child.render(viewCenter, level, partialTicks, modelViewMatrix, camera, projectionMatrix, isFoggy, setupFog, tesselator, positionVector, axisRotation());
			}
		}
	}
	
	
	// Rotates a corner of the layer to its place on the sphere and applies the matrix, the same way adding it as a vertex with that matrix would
	private static void setupCorner(Vector3f corner, Matrix4f lastMatrix, float x, float z)
	{
		SPHERE_ROTATION.transform(x, DEFAULT_DISTANCE, z, corner);
		lastMatrix.transformPosition(corner.x, corner.y, corner.z, corner);
	}
	
	private static void setupCorners(Matrix4f lastMatrix, SphericalCoords sphericalCoords, float size, float rotation)
	{
		SPHERE_ROTATION.identity().rotateY(sphericalCoords.theta);
		SPHERE_ROTATION.mul(SPHERE_AXIS_ROTATION.identity().rotateX(sphericalCoords.phi));
		SPHERE_ROTATION.mul(SPHERE_AXIS_ROTATION.identity().rotateY(rotation));
		
		setupCorner(CORNER_00, lastMatrix, size, size);
		setupCorner(CORNER_10, lastMatrix, -size, size);
		setupCorner(CORNER_11, lastMatrix, -size, -size);
		setupCorner(CORNER_01, lastMatrix, size, -size);
	}
	
	// Sets a bit for each side of the view the corner lies outside of, a corner with no bits set may be visible
	private static int offScreenSides(Vector3f corner, Matrix4f modelViewMatrix, Matrix4f projectionMatrix)
	{
		Vector4f position = CLIP_POSITION.set(corner.x, corner.y, corner.z, 1F);
		modelViewMatrix.transform(position);
		projectionMatrix.transform(position);
		
		float margin = Math.abs(position.w) * OFF_SCREEN_MARGIN;
		int sides = 0;
		
		if(position.x < -position.w - margin)
			sides |= 1;
		if(position.x > position.w + margin)
			sides |= 2;
		if(position.y < -position.w - margin)
			sides |= 4;
		if(position.y > position.w + margin)
			sides |= 8;
		if(position.z < -position.w - margin)
			sides |= 16;
		if(position.z > position.w + margin)
			sides |= 32;
		
		return sides;
	}
	
	// A layer is certain to be off screen once all of its corners lie outside of the same side of the view
	private static boolean cornersOffScreen(Matrix4f modelViewMatrix, Matrix4f projectionMatrix)
	{
		return (offScreenSides(CORNER_00, modelViewMatrix, projectionMatrix) & offScreenSides(CORNER_10, modelViewMatrix, projectionMatrix)
				& offScreenSides(CORNER_11, modelViewMatrix, projectionMatrix) & offScreenSides(CORNER_01, modelViewMatrix, projectionMatrix)) != 0;
	}
	
	private static boolean isOffScreen()
	{
		// Where the vertices end up is only known for the Vanilla shader, a shader pack is free to place them somewhere else
		if(RenderSystem.getShader() != GameRenderer.getPositionTexShader() || IrisCompatibility.isShaderPackInUse())
			return false;
		
		return cornersOffScreen(RenderSystem.getModelViewMatrix(), RenderSystem.getProjectionMatrix());
	}
	
	public static void renderOnSphere(Color.FloatRGBA rgba, Color.FloatRGBA secondaryRGBA, ResourceLocation texture, UV.Quad uv,
									  ClientLevel level, Camera camera, Tesselator tesselator, Matrix4f lastMatrix, SphericalCoords sphericalCoords,
									  long ticks, double distance, float partialTicks, float brightness, float size, float rotation, boolean shouldBlend)
	{
		float alpha = brightness * rgba.alpha() * secondaryRGBA.alpha();
		
		RenderSystem.setShaderColor(rgba.red() * secondaryRGBA.red(), rgba.green() * secondaryRGBA.green(), rgba.blue() * secondaryRGBA.blue(), alpha);
		
		RenderSystem.setShaderTexture(0, texture);
		
		// A layer without any alpha leaves the colors on screen as they are in both blend modes, so only the state it would leave behind gets set
		if(alpha <= 0)
		{
			RenderSystem.defaultBlendFunc();
			return;
		}
		
		setupCorners(lastMatrix, sphericalCoords, size, rotation);
		
		// The same goes for a layer none of which can end up on screen
		if(isOffScreen())
		{
			RenderSystem.defaultBlendFunc();
			return;
		}
		
		if(shouldBlend)
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
		else
			RenderSystem.defaultBlendFunc();
		
		final var bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		
		bufferbuilder.addVertex(CORNER_00.x, CORNER_00.y, CORNER_00.z).setUv(uv.topRight().u(ticks), uv.topRight().v(ticks));
		bufferbuilder.addVertex(CORNER_10.x, CORNER_10.y, CORNER_10.z).setUv(uv.bottomRight().u(ticks), uv.bottomRight().v(ticks));
		bufferbuilder.addVertex(CORNER_11.x, CORNER_11.y, CORNER_11.z).setUv(uv.bottomLeft().u(ticks), uv.bottomLeft().v(ticks));
		bufferbuilder.addVertex(CORNER_01.x, CORNER_01.y, CORNER_01.z).setUv(uv.topLeft().u(ticks), uv.topLeft().v(ticks));
		
		BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
		
		RenderSystem.defaultBlendFunc();
	}
	
	/**
	 * Method for rendering an individual texture layer, override to change details of how this object's texture layers are rendered
	 * @param textureLayer
	 * @param level
	 * @param bufferbuilder
	 * @param lastMatrix
	 * @param sphericalCoords
	 * @param ticks
	 * @param distance
	 * @param partialTicks
	 */
	protected void renderTextureLayer(TextureLayer textureLayer, ViewCenter viewCenter, ClientLevel level, Camera camera, Tesselator tesselator,
									  Matrix4f lastMatrix,SphericalCoords sphericalCoords, double fade, long ticks, double distance, float partialTicks)
	{
		if(textureLayer.rgba().alpha() <= 0)
			return;
		
		float size = (float) textureLayer.mulSize(renderedObject.distanceSize(distance));

		if(size < textureLayer.minSize()) {
			if (textureLayer.clampAtMinSize())
				size = (float) textureLayer.minSize();
			else
				return;
		}
		else if(size > textureLayer.maxSize()) {
			if (textureLayer.clampAtMaxSize())
				size = (float) textureLayer.maxSize();
			else
				return;
		}
		
		renderOnSphere(textureLayer.rgba(), Color.FloatRGBA.WHITE, textureLayer.texture(), textureLayer.uv(),
				level, camera, tesselator, lastMatrix, sphericalCoords,
				ticks, distance, partialTicks, LightEffects.dayBrightness(viewCenter, size, ticks, level, camera, partialTicks) * (float) fade, size, (float) textureLayer.rotation(), textureLayer.shoulBlend());
	}
	
	protected void renderTextureLayers(ViewCenter viewCenter, ClientLevel level, Camera camera, Tesselator tesselator, Matrix4f lastMatrix, SphericalCoords sphericalCoords, long ticks, double distance, float partialTicks)
	{
		double fade = renderedObject.fadeOut(distance);
		
		if(fade <= 0)
			return;
		
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		
		ArrayList<TextureLayer> textureLayers = renderedObject.getTextureLayers();
		for(int i = 0; i < textureLayers.size(); i++)
		{
			renderTextureLayer(textureLayers.get(i), viewCenter, level, camera, tesselator, lastMatrix, sphericalCoords, fade, ticks, distance, partialTicks);
		}
	}
}
