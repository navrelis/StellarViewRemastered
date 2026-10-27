package net.povstalec.stellarview.common.config;

import net.minecraft.client.Minecraft;
import net.povstalec.stellarview.client.render.clouds.CloudSettings;

public class CloudsConfig
{
	public static final String PREFIX = "client.clouds";
	
	public static StellarViewConfigValue.BooleanValue enabled;
	public static StellarViewConfigValue.IntValue distance;
	public static StellarViewConfigValue.IntValue coverage;
	public static StellarViewConfigValue.IntValue cloud_size;
	public static StellarViewConfigValue.IntValue thickness;
	public static StellarViewConfigValue.IntValue height_offset;
	public static StellarViewConfigValue.IntValue opacity;
	public static StellarViewConfigValue.IntValue drift_speed;
	public static StellarViewConfigValue.IntValue brightness;
	public static StellarViewConfigValue.IntValue sunset_glow;
	
	public static void init(StellarViewConfigSpec.Builder client)
	{
		enabled = new StellarViewConfigValue.BooleanValue(client, PREFIX + ".enabled",
				CloudSettings.ENABLED_DEFAULT,
				"Draws Stellar View's own clouds | False - Vanilla (or Sodium) clouds are drawn instead");
		
		distance = new StellarViewConfigValue.IntValue(client, PREFIX + ".distance",
				CloudSettings.DISTANCE_DEFAULT, CloudSettings.DISTANCE_MIN, CloudSettings.DISTANCE_MAX,
				"How far clouds reach, in percent of your render distance (the cost grows with the square of this value)");
		
		coverage = new StellarViewConfigValue.IntValue(client, PREFIX + ".coverage",
				CloudSettings.COVERAGE_DEFAULT, CloudSettings.COVERAGE_MIN, CloudSettings.COVERAGE_MAX,
				"How much of the sky is covered in clear weather, in percent (rain and thunder add to it)");
		
		cloud_size = new StellarViewConfigValue.IntValue(client, PREFIX + ".cloud_size",
				CloudSettings.CLOUD_SIZE_DEFAULT, CloudSettings.CLOUD_SIZE_MIN, CloudSettings.CLOUD_SIZE_MAX,
				"Size of the cloud puffs and cloud shapes, in percent (small clouds cost more performance)");
		
		thickness = new StellarViewConfigValue.IntValue(client, PREFIX + ".thickness",
				CloudSettings.THICKNESS_DEFAULT, CloudSettings.THICKNESS_MIN, CloudSettings.THICKNESS_MAX,
				"Extra height of the thickest cloud cores above the thin edges, in blocks (0 gives flat layers)");
		
		height_offset = new StellarViewConfigValue.IntValue(client, PREFIX + ".height_offset",
				CloudSettings.HEIGHT_OFFSET_DEFAULT, CloudSettings.HEIGHT_OFFSET_MIN, CloudSettings.HEIGHT_OFFSET_MAX,
				"Moves the cloud layer up (positive) or down (negative) relative to the dimension's cloud height, in blocks");
		
		opacity = new StellarViewConfigValue.IntValue(client, PREFIX + ".opacity",
				CloudSettings.OPACITY_DEFAULT, CloudSettings.OPACITY_MIN, CloudSettings.OPACITY_MAX,
				"Opacity of a single cloud puff, in percent of the default (overlapping puffs add up)");
		
		drift_speed = new StellarViewConfigValue.IntValue(client, PREFIX + ".drift_speed",
				CloudSettings.DRIFT_SPEED_DEFAULT, CloudSettings.DRIFT_SPEED_MIN, CloudSettings.DRIFT_SPEED_MAX,
				"Speed at which the wind moves the clouds, in percent of Vanilla's cloud speed (0 stops them)");
		
		brightness = new StellarViewConfigValue.IntValue(client, PREFIX + ".brightness",
				CloudSettings.BRIGHTNESS_DEFAULT, CloudSettings.BRIGHTNESS_MIN, CloudSettings.BRIGHTNESS_MAX,
				"Overall brightness of the clouds, in percent");
		
		sunset_glow = new StellarViewConfigValue.IntValue(client, PREFIX + ".sunset_glow",
				CloudSettings.SUNSET_GLOW_DEFAULT, CloudSettings.SUNSET_GLOW_MIN, CloudSettings.SUNSET_GLOW_MAX,
				"Strength of the warm sunrise and sunset colours on the clouds, in percent");
	}
	
	/**
	 * Copies the config values into the cloud renderer's settings. The settings must only be written on the render
	 * thread, so when this is called from another thread the work is handed to the client thread instead.
	 * Call it after the config file has been read and whenever a value is changed.
	 */
	public static void apply()
	{
		Minecraft minecraft = Minecraft.getInstance();
		
		if(minecraft.isSameThread())
			applyNow();
		else
			minecraft.execute(CloudsConfig::applyNow);
	}
	
	private static void applyNow()
	{
		CloudSettings.setEnabled(enabled.get());
		CloudSettings.setDistance(distance.get());
		CloudSettings.setCoverage(coverage.get());
		CloudSettings.setCloudSize(cloud_size.get());
		CloudSettings.setThickness(thickness.get());
		CloudSettings.setHeightOffset(height_offset.get());
		CloudSettings.setOpacity(opacity.get());
		CloudSettings.setDriftSpeed(drift_speed.get());
		CloudSettings.setBrightness(brightness.get());
		CloudSettings.setSunsetGlow(sunset_glow.get());
	}
}
