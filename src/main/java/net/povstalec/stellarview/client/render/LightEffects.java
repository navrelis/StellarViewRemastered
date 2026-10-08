package net.povstalec.stellarview.client.render;

import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.povstalec.stellarview.client.resourcepack.ViewCenter;
import net.povstalec.stellarview.common.config.GeneralConfig;

public class LightEffects
{
	// Brightness per second, the same speed the fades had at 60 FPS in the default Overworld sky back when they moved by a fixed step on every call
	public static final float STAR_FADE_PER_SECOND = 46.2F;
	public static final float DUST_CLOUD_FADE_PER_SECOND = 0.78F;
	
	// The longest time a single frame can fade for, otherwise the fades would jump after the sky wasn't rendered for a while
	private static final long MAX_FADE_NANOS = 50000000L;
	
	private static float starBrightness = 0F;
	private static float dustCloudBrightness = 0F;
	
	private static boolean frameStarted = false;
	private static long frameNanos = 0L;
	private static float fadeSeconds = 0F;
	
	// Looked up by whatever asks first after a frame has started and kept for the rest of that frame
	private static boolean hasConfig = false;
	private static float starBrightnessConfig;
	private static float dustCloudBrightnessConfig;
	private static boolean lightPollution;
	
	private static boolean hasLightLevel = false;
	private static int lightLevel;
	
	private static boolean starsFaded = false;
	private static boolean dustCloudsFaded = false;
	
	/**
	 * Starts a new frame, called once each time a View Center renders the sky.
	 * The light level, config values and fades are updated once per frame at most, everything asking for them during the same frame gets the same values
	 */
	public static void beginFrame()
	{
		beginFrame(Util.getNanos());
	}
	
	private static void beginFrame(long nanos)
	{
		long elapsedNanos = frameStarted ? nanos - frameNanos : 0L; // The fades only start moving after the very first frame
		
		if(elapsedNanos > MAX_FADE_NANOS)
			elapsedNanos = MAX_FADE_NANOS;
		else if(elapsedNanos < 0L)
			elapsedNanos = 0L;
		
		frameStarted = true;
		frameNanos = nanos;
		fadeSeconds = elapsedNanos / 1000000000F;
		
		hasConfig = false;
		hasLightLevel = false;
		starsFaded = false;
		dustCloudsFaded = false;
	}
	
	// Keeps the values updating for anything that renders without a View Center ever starting a frame
	private static void updateFrame()
	{
		long nanos = Util.getNanos();
		
		if(!frameStarted || nanos - frameNanos > MAX_FADE_NANOS)
			beginFrame(nanos);
	}
	
	private static void updateConfig()
	{
		if(hasConfig)
			return;
		
		starBrightnessConfig = GeneralConfig.star_brightness.get() / 100F;
		dustCloudBrightnessConfig = GeneralConfig.dust_cloud_brightness.get() / 100F;
		lightPollution = GeneralConfig.light_pollution.get();
		
		hasConfig = true;
	}
	
	private static int lightLevel(ClientLevel level, Camera camera)
	{
		if(!hasLightLevel)
		{
			// Brightness of the position where the player is standing, 15 is subtracted from the ambient skylight, that way only block light is accounted for
			lightLevel = level.getLightEngine().getRawBrightness(camera.getEntity().getOnPos().above(), 15);
			hasLightLevel = true;
		}
		
		return lightLevel;
	}
	
	private static float fade(float current, float target, float step)
	{
		if(current < target)
		{
			current += step;
			
			if(current > target)
				current = target;
		}
		else if(current > target)
		{
			current -= step;
			
			if(current < target)
				current = target;
		}
		
		return current;
	}
	
	private static float starDimming(ClientLevel level, Camera camera)
	{
		if(!starsFaded)
		{
			float brightness = 0.5F + 1.5F * ((15F - lightLevel(level, camera)) / 15F);
			
			starBrightness = fade(starBrightness, brightness, STAR_FADE_PER_SECOND * fadeSeconds);
			starsFaded = true;
		}
		
		return starBrightness;
	}
	
	private static float dustCloudDimming(ClientLevel level, Camera camera)
	{
		if(!dustCloudsFaded)
		{
			float brightness = 2F * ((7F - lightLevel(level, camera)) / 7F);
			
			if(brightness < 0)
				brightness = 0;
			
			dustCloudBrightness = fade(dustCloudBrightness, brightness, DUST_CLOUD_FADE_PER_SECOND * fadeSeconds);
			dustCloudsFaded = true;
		}
		
		return dustCloudBrightness;
	}
	
	public static float lightSourceStarDimming(ClientLevel level, Camera camera)
	{
		updateFrame();
		
		return starDimming(level, camera);
	}
	
	public static float lightSourceDustCloudDimming(ClientLevel level, Camera camera)
	{
		updateFrame();
		
		return dustCloudDimming(level, camera);
	}
	
	public static float rainDimming(ClientLevel level, float partialTicks)
	{
		return 1F - level.getRainLevel(partialTicks);
	}
	
	public static float getStarBrightness(ViewCenter viewCenter, ClientLevel level, Camera camera, float partialTicks)
	{
		updateFrame();
		updateConfig();
		
		float brightness = starBrightnessConfig;
		
		if(!viewCenter.stars().duringDay())
			brightness *= level.getStarBrightness(partialTicks);
		else
			brightness *= 0.5F;
		
		if(lightPollution)
			brightness *= starDimming(level, camera);
		else
			brightness *= 2F;
		
		return brightness;
	}
	
	public static float getDustBrightness(ViewCenter viewCenter, ClientLevel level, Camera camera, float partialTicks)
	{
		updateFrame();
		updateConfig();
		
		float brightness = dustCloudBrightnessConfig;
		
		if(!viewCenter.stars().duringDay())
			brightness *= level.getStarBrightness(partialTicks);
		else
			brightness *= 0.5F;
		
		if(lightPollution)
			brightness *= dustCloudDimming(level, camera);
		else
			brightness *= 2F;
		
		return brightness;
	}
	
	
	
	public static float dayBrightness(ViewCenter viewCenter, float size, long ticks, ClientLevel level, Camera camera, float partialTicks)
	{
		float brightness = getStarBrightness(viewCenter, level, camera, partialTicks);
		
		if(brightness < viewCenter.dayBlending().dayMaxBrightness() && size > viewCenter.dayBlending().dayMinVisibleSize())
		{
			float aboveSize = size >= viewCenter.dayBlending().dayMaxVisibleSize() ? viewCenter.dayBlending().dayVisibleRange() : size - viewCenter.dayBlending().dayMinVisibleSize();
			float brightnessPercentage = aboveSize / viewCenter.dayBlending().dayVisibleRange();
			float minBrightness = brightnessPercentage * viewCenter.dayBlending().dayMaxBrightness();
			
			if(brightness < minBrightness)
				brightness = minBrightness;
		}
		
		return viewCenter.stars().ignoreRain() ? brightness : brightness * LightEffects.rainDimming(level, partialTicks);
	}
	
	public static float starDayBrightness(ViewCenter viewCenter, float size, long ticks, ClientLevel level, Camera camera, float partialTicks)
	{
		float brightness = getStarBrightness(viewCenter, level, camera, partialTicks);
		
		if(brightness < viewCenter.sunDayBlending().dayMaxBrightness() && size > viewCenter.sunDayBlending().dayMinVisibleSize())
		{
			float aboveSize = size >= viewCenter.sunDayBlending().dayMaxVisibleSize() ? viewCenter.sunDayBlending().dayVisibleRange() : size - viewCenter.sunDayBlending().dayMinVisibleSize();
			float brightnessPercentage = aboveSize / viewCenter.sunDayBlending().dayVisibleRange();
			float minBrightness = brightnessPercentage * viewCenter.sunDayBlending().dayMaxBrightness();
			
			if(brightness < minBrightness)
				brightness = minBrightness;
		}
		
		return viewCenter.stars().ignoreRain() ? brightness : brightness * LightEffects.rainDimming(level, partialTicks);
	}
	
	public static float dustCloudBrightness(ViewCenter viewCenter, ClientLevel level, Camera camera, float partialTicks)
	{
		float brightness = getDustBrightness(viewCenter, level, camera, partialTicks);
		
		return viewCenter.stars().ignoreRain() ? brightness : brightness * LightEffects.rainDimming(level, partialTicks);
	}
	
	public static float nebulaBrightness(ViewCenter viewCenter, float size, long ticks, ClientLevel level, Camera camera, float partialTicks)
	{
		float brightness = getDustBrightness(viewCenter, level, camera, partialTicks);
		
		if(brightness < viewCenter.dayBlending().dayMaxBrightness() && size > viewCenter.dayBlending().dayMinVisibleSize())
		{
			float aboveSize = size >= viewCenter.dayBlending().dayMaxVisibleSize() ? viewCenter.dayBlending().dayVisibleRange() : size - viewCenter.dayBlending().dayMinVisibleSize();
			float brightnessPercentage = aboveSize / viewCenter.dayBlending().dayVisibleRange();
			float minBrightness = brightnessPercentage * viewCenter.dayBlending().dayMaxBrightness();
			
			if(brightness < minBrightness)
				brightness = minBrightness;
		}
		
		return viewCenter.stars().ignoreRain() ? brightness : brightness * LightEffects.rainDimming(level, partialTicks);
	}
	
	/**
	 * Returns the brightness of stars in the current Player location
	 * @param level The Level the Player is currently in
	 * @param camera Player Camera
	 * @param partialTicks
	 * @return
	 */
	public static float starBrightness(ViewCenter viewCenter, ClientLevel level, Camera camera, float partialTicks)
	{
		float brightness = getStarBrightness(viewCenter, level, camera, partialTicks);
		
		return viewCenter.stars().ignoreRain() ? brightness : brightness * LightEffects.rainDimming(level, partialTicks);
	}
}
