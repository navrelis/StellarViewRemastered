package net.povstalec.stellarview.client.resourcepack.effects;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.*;
import net.povstalec.stellarview.client.render.LightEffects;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.povstalec.stellarview.client.resourcepack.ViewCenter;
import net.povstalec.stellarview.common.config.GeneralConfig;
import net.povstalec.stellarview.common.util.Color;
import net.povstalec.stellarview.common.util.SphericalCoords;
import net.povstalec.stellarview.common.util.StellarCoordinates;
import net.povstalec.stellarview.common.util.TextureLayer;
import net.povstalec.stellarview.common.util.UV;

public abstract class MeteorEffect
{
	public static final UV.Quad UV = new UV.Quad(false);
	public static final float DEFAULT_DISTANCE = 100.0F;
	public static final SphericalCoords SPHERICAL_START = new SphericalCoords(DEFAULT_DISTANCE, 0, 0);
	
	// Indices of the independent values derived from a single seed
	protected static final int SEED_APPEARANCE = 1;
	protected static final int SEED_METEOR_TYPE = 2;
	protected static final int SEED_START = 3;
	protected static final int SEED_X_ROTATION = 4;
	protected static final int SEED_Y_ROTATION = 5;
	protected static final int SEED_Z_ROTATION = 6;
	
	protected final ArrayList<MeteorType> meteorTypes;
	protected int totalWeight = 0;
	
	protected double rarity;
	
	protected Color.FloatRGBA meteorColor = Color.FloatRGBA.white();
	
	public MeteorEffect(List<MeteorType> meteorTypes, double rarity)
	{
		this.meteorTypes = new ArrayList<MeteorType>(meteorTypes);
		this.rarity = rarity;
		
		for(MeteorType meteorType : meteorTypes)
		{
			this.totalWeight += meteorType.getWeight();
		}
	}
	
	public List<MeteorType> getMeteorTypes()
	{
		return meteorTypes;
	}
	
	public boolean canRender(ViewCenter viewCenter)
	{
		return getRarity(viewCenter) > 0 && meteorTypes.size() > 0;
	}
	
	public abstract double getRarity(ViewCenter viewCenter);
	
	public double getRarity()
	{
		return rarity;
	}
	
	/**
	 * Consecutive seeds give almost the same first value when they are used for a new Random, so the seed is mixed instead (SplitMix64)
	 * @param seed Seed of whatever is being decided, for example the number of the current day
	 * @param index Which of the values derived from the seed to return
	 * @return Returns a value that shares no visible pattern with the values of neighboring seeds and indices
	 */
	protected static long seededLong(long seed, int index)
	{
		long value = seed + index * 0x9E3779B97F4A7C15L;
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		
		return value ^ (value >>> 31);
	}
	
	/**
	 * @return Returns a value from 0 (inclusive) to 1 (exclusive)
	 */
	protected static double seededDouble(long seed, int index)
	{
		return (seededLong(seed, index) >>> 11) * 0x1.0p-53;
	}
	
	/**
	 * @return Returns a value from origin (inclusive) to bound (exclusive)
	 */
	protected static int seededInt(long seed, int index, int origin, int bound)
	{
		return origin + (int) Math.floorMod(seededLong(seed, index), (long) (bound - origin));
	}
	
	protected boolean shouldAppear(ViewCenter viewCenter, long seed)
	{
		// Rarity is a chance in percent, 0 never appears and 100 always does
		return seededDouble(seed, SEED_APPEARANCE) * 100 < getRarity(viewCenter);
	}
	
	protected MeteorType getRandomMeteorType(long seed)
	{
		int i = 0;
		
		for(int weight = seededInt(seed, SEED_METEOR_TYPE, 0, totalWeight); i < meteorTypes.size() - 1; i++)
		{
			weight -= meteorTypes.get(i).getWeight();
			
			if(weight < 0)
				break;
		}
		
		return meteorTypes.get(i);
	}
	
	public Color.FloatRGBA rgba(ViewCenter viewCenter, ClientLevel level, Camera camera, long ticks, float partialTicks)
	{
		float brightness = LightEffects.getStarBrightness(viewCenter, level, camera, partialTicks) / 2F;
		
		brightness *= LightEffects.rainDimming(level, partialTicks);
		meteorColor.setAlpha(brightness);
		
		return meteorColor;
	}
	
	public abstract void render(ViewCenter viewCenter, ClientLevel level, Camera camera, float partialTicks, Matrix4f modelViewMatrix, Tesselator tesselator);
	
	public void render(ViewCenter viewCenter, ClientLevel level, Camera camera, float partialTicks, Matrix4f modelViewMatrix, Tesselator tesselator,
					   float xRotation, float yRotation, float zRotation,
					   MeteorType meteorType, float mulSize, float addRotation)
	{
		final var transformedModelView = new Matrix4f(modelViewMatrix);
		
		transformedModelView.rotate(Axis.YP.rotationDegrees(yRotation));
		transformedModelView.rotate(Axis.ZP.rotationDegrees(zRotation));
		transformedModelView.rotate(Axis.XP.rotationDegrees(xRotation));
		
		meteorType.render(tesselator, transformedModelView, SPHERICAL_START, rgba(viewCenter, level, camera, viewCenter.ticks(), partialTicks), viewCenter.ticks(), mulSize, addRotation);
	}
	
	public static class MeteorType
	{
		private final ArrayList<TextureLayer> textureLayers;
		private final int weight;
		
		public static final Codec<MeteorType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				TextureLayer.CODEC.listOf().fieldOf("texture_layers").forGetter(MeteorType::getTextureLayers),
				Codec.intRange(1, Integer.MAX_VALUE).fieldOf("weight").forGetter(MeteorType::getWeight)
		).apply(instance, MeteorType::new));
		
		public MeteorType(List<TextureLayer> textureLayers, int weight)
		{
			this.textureLayers = new ArrayList<TextureLayer>(textureLayers);
			this.weight = weight;
		}
		
		public ArrayList<TextureLayer> getTextureLayers()
		{
			return textureLayers;
		}
		
		public int getWeight()
		{
			return weight;
		}
		
		protected void renderTextureLayer(TextureLayer textureLayer, Tesselator tesselator, Matrix4f lastMatrix, SphericalCoords sphericalCoords, Color.FloatRGBA rgba, long ticks, float mulSize, float addRotation)
		{
			if(rgba.alpha() <= 0.0F || textureLayer.rgba().alpha() <= 0)
				return;
			
			float size = (float) textureLayer.mulSize(mulSize);

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
			
			float rotation = (float) textureLayer.rotation();
			
			Vector3f corner00 = StellarCoordinates.placeOnSphere(-size, -size, sphericalCoords, rotation);
			Vector3f corner10 = StellarCoordinates.placeOnSphere(size, -size, sphericalCoords, rotation);
			Vector3f corner11 = StellarCoordinates.placeOnSphere(size, size, sphericalCoords, rotation);
			Vector3f corner01 = StellarCoordinates.placeOnSphere(-size, size, sphericalCoords, rotation);
			
			
			if(textureLayer.shoulBlend())
				RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
			
			RenderSystem.setShaderColor(rgba.red() * textureLayer.rgba().red(), rgba.green() * textureLayer.rgba().green(), rgba.blue() * textureLayer.rgba().blue(), rgba.alpha() * textureLayer.rgba().alpha());
			
			RenderSystem.setShaderTexture(0, textureLayer.texture());
			final var bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
			
			bufferbuilder.addVertex(lastMatrix, corner00.x, corner00.y, corner00.z).setUv(textureLayer.uv().topRight().u(ticks), textureLayer.uv().topRight().v(ticks));
			bufferbuilder.addVertex(lastMatrix, corner10.x, corner10.y, corner10.z).setUv(textureLayer.uv().bottomRight().u(ticks), textureLayer.uv().bottomRight().v(ticks));
			bufferbuilder.addVertex(lastMatrix, corner11.x, corner11.y, corner11.z).setUv(textureLayer.uv().bottomLeft().u(ticks), textureLayer.uv().bottomLeft().v(ticks));
			bufferbuilder.addVertex(lastMatrix, corner01.x, corner01.y, corner01.z).setUv(textureLayer.uv().topLeft().u(ticks), textureLayer.uv().topLeft().v(ticks));
			
			BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
			
			RenderSystem.defaultBlendFunc();
		}
		
		public final void render(Tesselator tesselator, Matrix4f lastMatrix, SphericalCoords sphericalCoords, Color.FloatRGBA rgba, long ticks, float mulSize, float addRotation)
		{
			for(TextureLayer textureLayer : textureLayers)
			{
				renderTextureLayer(textureLayer, tesselator, lastMatrix, sphericalCoords, rgba, ticks, mulSize, addRotation);
			}
		}
	}
	
	
	
	public static class ShootingStar extends MeteorEffect
	{
		protected static final int TICKS = 1000;
		protected static final float MAX_SIZE = 1;
		protected static final int DURATION = 20;
		
		public static final Codec<ShootingStar> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				MeteorType.CODEC.listOf().fieldOf("meteor_types").forGetter(ShootingStar::getMeteorTypes),
				Codec.DOUBLE.fieldOf("probability").forGetter(ShootingStar::getRarity)
		).apply(instance, ShootingStar::new));
		
		public ShootingStar(List<MeteorType> meteorTypes, double rarity)
		{
			super(meteorTypes, rarity);
		}
		
		public ShootingStar()
		{
			this(new ArrayList<MeteorType>(), 0);
		}
		
		public double getRarity(ViewCenter viewCenter)
		{
			if(!viewCenter.overrideMeteorEffects())
				return rarity;
			
			return viewCenter.overrideShootingStarRarity();
		}
		
		@Override
		public final void render(ViewCenter viewCenter, ClientLevel level, Camera camera, float partialTicks, Matrix4f modelViewMatrix, Tesselator tesselator)
		{
			if(!canRender(viewCenter))
				return;
			
			long tickSeed = viewCenter.ticks() / TICKS;
			int specificTime = (int) (viewCenter.ticks() % TICKS);
			
			int randomStart = seededInt(tickSeed, SEED_START, 0, TICKS - DURATION);
			
			if(shouldAppear(viewCenter, tickSeed) && specificTime >= randomStart && specificTime < randomStart + DURATION)
			{
				// Counted from the start of the flight and seeded by its period, so the shooting star keeps a single direction for the whole flight
				double position = specificTime - randomStart;
				
				float xRotation = (float) (seededInt(tickSeed, SEED_X_ROTATION, 0, 45) + Math.PI * Mth.lerp(partialTicks, position - 1, position));
				float yRotation = seededInt(tickSeed, SEED_Y_ROTATION, 0, 360);
				float zRotation = seededInt(tickSeed, SEED_Z_ROTATION, -70, 70);
				
				MeteorType meteorType = getRandomMeteorType(tickSeed);
				
				float rotation = (float) (Math.PI * position / 4);
				float size = (float) (Math.sin(Math.PI * position / DURATION));
				
				this.render(viewCenter, level, camera, partialTicks, modelViewMatrix, tesselator, xRotation, yRotation, zRotation, meteorType, size, rotation);
			}
		}
	}
	
	
	
	public static class MeteorShower extends MeteorEffect
	{
		protected static final int TICKS = 1000;
		protected static final float MAX_SIZE = 1;
		protected static final int DURATION = 20;
		
		public static final Codec<MeteorShower> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				MeteorType.CODEC.listOf().fieldOf("meteor_types").forGetter(MeteorShower::getMeteorTypes),
				Codec.DOUBLE.fieldOf("probability").forGetter(MeteorShower::getRarity)
		).apply(instance, MeteorShower::new));
		
		public MeteorShower(List<MeteorType> meteorTypes, double rarity)
		{
			super(meteorTypes, rarity);
		}
		
		public MeteorShower()
		{
			this(new ArrayList<MeteorType>(), 0);
		}
		
		public double getRarity(ViewCenter viewCenter)
		{
			if(!viewCenter.overrideMeteorEffects())
				return rarity;
			
			return viewCenter.overrideMeteorShowerRarity();
		}
		
		@Override
		public final void render(ViewCenter viewCenter, ClientLevel level, Camera camera, float partialTicks, Matrix4f modelViewMatrix, Tesselator tesselator)
		{
			if(!canRender(viewCenter))
				return;
			
			// Seeds are inverted, that way a day or meteor doesn't share its values with the shooting star period of the same number
			long dailySeed = ~(viewCenter.ticks() / (viewCenter.getRotationPeriod() == 0 ? 24000L : viewCenter.getRotationPeriod()));
			
			if(shouldAppear(viewCenter, dailySeed))
			{
				double position = viewCenter.ticks() % DURATION;
				
				long meteorSeed = ~(viewCenter.ticks() / DURATION);
				
				float xRotation = (float) (seededInt(meteorSeed, SEED_X_ROTATION, 0, 45) + Math.PI * Mth.lerp(partialTicks, position - 1, position));
				float yRotation = seededInt(meteorSeed, SEED_Y_ROTATION, 0, 360);
				float zRotation = seededInt(meteorSeed, SEED_Z_ROTATION, -70, 70);
				
				MeteorType meteorType = getRandomMeteorType(dailySeed);
				
				float rotation = (float) (Math.PI * position / 4);
				float size = (float) (Math.sin(Math.PI * position / DURATION));
				
				this.render(viewCenter, level, camera, partialTicks, modelViewMatrix, tesselator, xRotation, yRotation, zRotation, meteorType, size, rotation);
			}
		}
	}
}
