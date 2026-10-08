package net.povstalec.stellarview.client.resourcepack;

import com.mojang.blaze3d.vertex.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.povstalec.stellarview.common.util.Color;
import net.povstalec.stellarview.common.util.UV;

public class Skybox
{
	public static final float DEFAULT_DISTANCE = 150.0F;
	
	public static final Vector3f[][] BOX_COORDS =
			{
					{
							new Vector3f(-DEFAULT_DISTANCE, DEFAULT_DISTANCE, DEFAULT_DISTANCE),
							new Vector3f(-DEFAULT_DISTANCE, DEFAULT_DISTANCE, -DEFAULT_DISTANCE),
							new Vector3f(DEFAULT_DISTANCE, DEFAULT_DISTANCE, -DEFAULT_DISTANCE),
							new Vector3f(DEFAULT_DISTANCE, DEFAULT_DISTANCE, DEFAULT_DISTANCE)
					},
					{
							new Vector3f(DEFAULT_DISTANCE, DEFAULT_DISTANCE, DEFAULT_DISTANCE),
							new Vector3f(DEFAULT_DISTANCE, -DEFAULT_DISTANCE, DEFAULT_DISTANCE),
							new Vector3f(-DEFAULT_DISTANCE, -DEFAULT_DISTANCE, DEFAULT_DISTANCE),
							new Vector3f(-DEFAULT_DISTANCE, DEFAULT_DISTANCE, DEFAULT_DISTANCE)
					},
					{
							new Vector3f(-DEFAULT_DISTANCE, DEFAULT_DISTANCE, DEFAULT_DISTANCE),
							new Vector3f(-DEFAULT_DISTANCE, -DEFAULT_DISTANCE, DEFAULT_DISTANCE),
							new Vector3f(-DEFAULT_DISTANCE, -DEFAULT_DISTANCE, -DEFAULT_DISTANCE),
							new Vector3f(-DEFAULT_DISTANCE, DEFAULT_DISTANCE, -DEFAULT_DISTANCE)
					},
					{
							new Vector3f(-DEFAULT_DISTANCE, DEFAULT_DISTANCE, -DEFAULT_DISTANCE),
							new Vector3f(-DEFAULT_DISTANCE, -DEFAULT_DISTANCE, -DEFAULT_DISTANCE),
							new Vector3f(DEFAULT_DISTANCE, -DEFAULT_DISTANCE, -DEFAULT_DISTANCE),
							new Vector3f(DEFAULT_DISTANCE, DEFAULT_DISTANCE, -DEFAULT_DISTANCE)
					},
					{
							new Vector3f(DEFAULT_DISTANCE, DEFAULT_DISTANCE, -DEFAULT_DISTANCE),
							new Vector3f(DEFAULT_DISTANCE, -DEFAULT_DISTANCE, -DEFAULT_DISTANCE),
							new Vector3f(DEFAULT_DISTANCE, -DEFAULT_DISTANCE, DEFAULT_DISTANCE),
							new Vector3f(DEFAULT_DISTANCE, DEFAULT_DISTANCE, DEFAULT_DISTANCE)
					},
					{
							new Vector3f(-DEFAULT_DISTANCE, -DEFAULT_DISTANCE, -DEFAULT_DISTANCE),
							new Vector3f(-DEFAULT_DISTANCE, -DEFAULT_DISTANCE, DEFAULT_DISTANCE),
							new Vector3f(DEFAULT_DISTANCE, -DEFAULT_DISTANCE, DEFAULT_DISTANCE),
							new Vector3f(DEFAULT_DISTANCE, -DEFAULT_DISTANCE, -DEFAULT_DISTANCE)
					}
			};
	
	// Reused by every facade vertex, it holds no value past the line after the one it is written in
	private static final Vector3f VERTEX = new Vector3f();
	
	// Reused every frame, each Skybox has its own because it is handed to renderFacade
	private final Matrix4f facadeModelView = new Matrix4f();
	
	private SkyboxFacade[] facades = new SkyboxFacade[6];
	
	public static final Codec<Skybox> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			SkyboxFacade.CODEC.fieldOf("top_facade").forGetter(Skybox::topFacade),
			SkyboxFacade.CODEC.fieldOf("north_facade").forGetter(Skybox::northFacade),
			SkyboxFacade.CODEC.fieldOf("east_facade").forGetter(Skybox::eastFacade),
			SkyboxFacade.CODEC.fieldOf("south_facade").forGetter(Skybox::southFacade),
			SkyboxFacade.CODEC.fieldOf("west_facade").forGetter(Skybox::westFacade),
			SkyboxFacade.CODEC.fieldOf("bottom_facade").forGetter(Skybox::bottomFacade)
	).apply(instance, Skybox::new));
	
	public Skybox(SkyboxFacade topFacade, SkyboxFacade northFacade, SkyboxFacade eastFacade, SkyboxFacade southFacade, SkyboxFacade westFacade, SkyboxFacade bottomFacade)
	{
		facades[0] = topFacade;
		facades[1] = northFacade;
		facades[2] = eastFacade;
		facades[3] = southFacade;
		facades[4] = westFacade;
		facades[5] = bottomFacade;
	}
	
	public SkyboxFacade topFacade()
	{
		return facades[0];
	}
	
	public SkyboxFacade northFacade()
	{
		return facades[1];
	}
	
	public SkyboxFacade eastFacade()
	{
		return facades[2];
	}
	
	public SkyboxFacade southFacade()
	{
		return facades[3];
	}
	
	public SkyboxFacade westFacade()
	{
		return facades[4];
	}
	
	public SkyboxFacade bottomFacade()
	{
		return facades[5];
	}
	
	public void render(ClientLevel level, float partialTicks, Matrix4f modelViewMatrix, Tesselator tesselator)
	{
		final var transformeModelView = facadeModelView.set(modelViewMatrix);
		//stack.mulPose(Axis.YP.rotationDegrees(skyXAngle));
		//stack.mulPose(Axis.ZP.rotationDegrees(skyYAngle));
		//stack.mulPose(Axis.XP.rotationDegrees(skyZAngle));
		
		Matrix4f lastMatrix = transformeModelView;
		
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
		RenderSystem.defaultBlendFunc();
		
		for(int i = 0; i < 6; i++)
		{
			this.renderFacade(tesselator, lastMatrix, facades[i], i);
		}
		
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.defaultBlendFunc();
	}
	
	protected void renderFacade(Tesselator tesselator, Matrix4f lastMatrix, SkyboxFacade facade, int i)
	{
		UV.Quad uv = facade.uv();
		Color.IntRGBA rgba = facade.rgba();
		
		RenderSystem.setShaderTexture(0, facade.texture());
		
		if(rgba.alpha() <= 0) // The shader discards everything a facade without any alpha would draw
			return;
		
		final var bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
		addVertex(bufferbuilder, lastMatrix, BOX_COORDS[i][0]).setUv(uv.topLeft().u(), uv.topLeft().v()).setColor(rgba.red(), rgba.green(), rgba.blue(), rgba.alpha());
		addVertex(bufferbuilder, lastMatrix, BOX_COORDS[i][1]).setUv(uv.bottomLeft().u(), uv.bottomLeft().v()).setColor(rgba.red(), rgba.green(), rgba.blue(), rgba.alpha());
		addVertex(bufferbuilder, lastMatrix, BOX_COORDS[i][2]).setUv(uv.bottomRight().u(), uv.bottomRight().v()).setColor(rgba.red(), rgba.green(), rgba.blue(), rgba.alpha());
		addVertex(bufferbuilder, lastMatrix, BOX_COORDS[i][3]).setUv(uv.topRight().u(), uv.topRight().v()).setColor(rgba.red(), rgba.green(), rgba.blue(), rgba.alpha());
		BufferUploader.drawWithShader(bufferbuilder.build());
	}
	
	// Same as adding the vertex with the matrix, which would create a new vector for each vertex
	private static VertexConsumer addVertex(VertexConsumer consumer, Matrix4f matrix, Vector3f position)
	{
		matrix.transformPosition(position.x, position.y, position.z, VERTEX);
		
		return consumer.addVertex(VERTEX.x, VERTEX.y, VERTEX.z);
	}
	
	
	
	public static class SkyboxFacade
	{
		private final ResourceLocation texture;
		private final UV.Quad uv;
		private final Color.IntRGBA rgba;
		
		public static final Codec<SkyboxFacade> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				ResourceLocation.CODEC.fieldOf("texture").forGetter(SkyboxFacade::texture),
				UV.Quad.CODEC.fieldOf("uv").forGetter(SkyboxFacade::uv),
				Color.IntRGBA.CODEC.fieldOf("rgba").forGetter(SkyboxFacade::rgba)
		).apply(instance, SkyboxFacade::new));
		
		public SkyboxFacade(ResourceLocation texture, UV.Quad uv, Color.IntRGBA rgba)
		{
			this.texture = ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), "textures/" + texture.getPath());
			this.uv = uv;
			this.rgba = rgba;
		}
		
		public ResourceLocation texture()
		{
			return texture;
		}
		
		public UV.Quad uv()
		{
			return uv;
		}
		
		public Color.IntRGBA rgba()
		{
			return rgba;
		}
	}
}
