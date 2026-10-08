package net.povstalec.stellarview.client.render.space_objects.distinct;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.povstalec.stellarview.api.common.space_objects.SpaceObject;
import net.povstalec.stellarview.api.common.space_objects.distinct.Luna;
import net.povstalec.stellarview.client.render.space_objects.OrbitingObjectRenderer;
import net.povstalec.stellarview.client.render.space_objects.resourcepack.MoonRenderer;
import net.povstalec.stellarview.client.resourcepack.ViewCenter;
import net.povstalec.stellarview.common.config.OverworldConfig;
import net.povstalec.stellarview.common.util.AxisRotation;
import net.povstalec.stellarview.common.util.Color;
import net.povstalec.stellarview.common.util.SphericalCoords;
import net.povstalec.stellarview.common.util.TextureLayer;
import net.povstalec.stellarview.common.util.UV;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;

public class LunaRenderer extends MoonRenderer<Luna>
{
	public static final ResourceLocation MOON_LOCATION = ResourceLocation.withDefaultNamespace("textures/environment/moon_phases.png");
	
	public static final UV.Quad MOON_QUAD = new UV.Quad(new UV.PhaseHandler(24000, 0, 4, 2), true);
	public static final TextureLayer MOON_TEXTURE_LAYER = new TextureLayer(MOON_LOCATION, Color.FloatRGBA.WHITE,
			true, 7697847.735118539, 0.15, true, Double.MAX_VALUE, false, 90, MOON_QUAD);
	
	public LunaRenderer(Luna luna)
	{
		super(luna);
	}
	
	/**
	 * With the Vanilla Moon cycle, Luna viewed from its parent is kept exactly opposite the object its parent orbits
	 */
	@Override
	public Vector3f getPosition(ViewCenter viewCenter, AxisRotation parentRotation, long ticks, float partialTicks)
	{
		if(OverworldConfig.vanilla_moon_cycle.get() && parent instanceof OrbitingObjectRenderer<?> orbitingParent
				&& orbitingParent.orbitInfo() != null && viewCenter.objectEquals(orbitingParent))
		{
			SpaceObject grandparent = orbitingParent.renderedObject().getParent().orElse(null);
			
			if(grandparent != null)
			{
				// The same vector the parent itself is positioned with, pointing from the object it orbits to the parent
				Vector3f antisolarVector = orbitingParent.getPosition(viewCenter, grandparent.getAxisRotation(), ticks, partialTicks);
				float parentDistance = antisolarVector.length();
				
				// The parent's vector is already in the frame the result gets added in, so parentRotation must not be applied to it
				if(parentDistance > 0 && Float.isFinite(parentDistance))
					return antisolarVector.mul(getPosition(viewCenter, ticks, partialTicks).length() / parentDistance);
			}
		}
		
		return super.getPosition(viewCenter, parentRotation, ticks, partialTicks);
	}
	
	//============================================================================================
	//*****************************************Rendering******************************************
	//============================================================================================
	
	@Override
	protected void renderTextureLayers(ViewCenter viewCenter, ClientLevel level, Camera camera, Tesselator tesselator, Matrix4f lastMatrix, SphericalCoords sphericalCoords, long ticks, double distance, float partialTicks)
	{
		double fade = renderedObject.fadeOut(distance);
		
		if(fade <= 0)
			return;
		
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		
		if(OverworldConfig.vanilla_moon.get())
			renderTextureLayer(MOON_TEXTURE_LAYER, viewCenter, level, camera, tesselator, lastMatrix, sphericalCoords, fade, ticks, distance, partialTicks);
		else
		{
			ArrayList<TextureLayer> textureLayers = renderedObject.getTextureLayers();
			for(int i = 0; i < textureLayers.size(); i++)
			{
				renderTextureLayer(textureLayers.get(i), viewCenter, level, camera, tesselator, lastMatrix, sphericalCoords, fade, ticks, distance, partialTicks);
			}
		}
	}
}
