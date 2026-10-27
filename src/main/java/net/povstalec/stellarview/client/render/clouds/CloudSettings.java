package net.povstalec.stellarview.client.render.clouds;

/**
 * Every user-facing option of the cloud renderer, with its default and its valid range.
 * <p>
 * All values are integers (percentages or blocks) so they map one to one onto Stellar View's
 * boolean and int config values. The renderer reads this class every frame, so a change applies on the next frame.
 * The setters clamp to the valid range, so a config file or screen may pass anything.
 * Only call the setters from the render thread (the client thread).
 * <p>
 * Options marked "rebuild" make the generator rebuild the cloud boxes on its background thread. The old clouds stay
 * visible until the new ones are ready. Every other option is a shader uniform and costs nothing to change.
 */
public final class CloudSettings
{
	/** Draw Stellar View's clouds. Off: Vanilla (or Sodium) clouds are drawn instead. Live. */
	public static final boolean ENABLED_DEFAULT = true;

	/** Cloud reach in percent of the render distance. Rebuild. Cost grows with the square of this value. */
	public static final int DISTANCE_MIN = 100;
	public static final int DISTANCE_MAX = 350;
	public static final int DISTANCE_DEFAULT = 200;

	/** How much of the sky is covered in clear weather, in percent. Rain and thunder add to it. Live. */
	public static final int COVERAGE_MIN = 0;
	public static final int COVERAGE_MAX = 100;
	public static final int COVERAGE_DEFAULT = 50;

	/** Size of the cloud puffs and of the cloud shapes, in percent. Rebuild. Cost grows with 1 / size squared. */
	public static final int CLOUD_SIZE_MIN = 50;
	public static final int CLOUD_SIZE_MAX = 200;
	public static final int CLOUD_SIZE_DEFAULT = 100;

	/** Extra height of the thickest cloud cores above the thin edges, in blocks. 0 gives flat layers. Live. */
	public static final int THICKNESS_MIN = 0;
	public static final int THICKNESS_MAX = 96;
	public static final int THICKNESS_DEFAULT = 40;

	/** Moves our cloud layer relative to the dimension's cloud height, in blocks. Never changes Vanilla's value. Live. */
	public static final int HEIGHT_OFFSET_MIN = -128;
	public static final int HEIGHT_OFFSET_MAX = 256;
	public static final int HEIGHT_OFFSET_DEFAULT = 0;

	/** Opacity of a single puff, in percent of the default. Overlapping puffs add up. Live. */
	public static final int OPACITY_MIN = 10;
	public static final int OPACITY_MAX = 200;
	public static final int OPACITY_DEFAULT = 100;

	/** Wind speed in percent of Vanilla's cloud speed (100 = 0.6 blocks per second). Live. */
	public static final int DRIFT_SPEED_MIN = 0;
	public static final int DRIFT_SPEED_MAX = 1000;
	public static final int DRIFT_SPEED_DEFAULT = 100;

	/** Overall cloud brightness in percent. Live. */
	public static final int BRIGHTNESS_MIN = 50;
	public static final int BRIGHTNESS_MAX = 150;
	public static final int BRIGHTNESS_DEFAULT = 100;

	/** Strength of the warm sunrise and sunset colours in percent. Live. */
	public static final int SUNSET_GLOW_MIN = 0;
	public static final int SUNSET_GLOW_MAX = 150;
	public static final int SUNSET_GLOW_DEFAULT = 100;

	private static boolean enabled = ENABLED_DEFAULT;
	private static int distance = DISTANCE_DEFAULT;
	private static int coverage = COVERAGE_DEFAULT;
	private static int cloudSize = CLOUD_SIZE_DEFAULT;
	private static int thickness = THICKNESS_DEFAULT;
	private static int heightOffset = HEIGHT_OFFSET_DEFAULT;
	private static int opacity = OPACITY_DEFAULT;
	private static int driftSpeed = DRIFT_SPEED_DEFAULT;
	private static int brightness = BRIGHTNESS_DEFAULT;
	private static int sunsetGlow = SUNSET_GLOW_DEFAULT;

	private CloudSettings() {}

	public static boolean enabled()
	{
		return enabled;
	}

	public static void setEnabled(boolean value)
	{
		enabled = value;
	}

	public static int distance()
	{
		return distance;
	}

	public static void setDistance(int value)
	{
		distance = clamp(value, DISTANCE_MIN, DISTANCE_MAX);
	}

	public static int coverage()
	{
		return coverage;
	}

	public static void setCoverage(int value)
	{
		coverage = clamp(value, COVERAGE_MIN, COVERAGE_MAX);
	}

	public static int cloudSize()
	{
		return cloudSize;
	}

	public static void setCloudSize(int value)
	{
		cloudSize = clamp(value, CLOUD_SIZE_MIN, CLOUD_SIZE_MAX);
	}

	public static int thickness()
	{
		return thickness;
	}

	public static void setThickness(int value)
	{
		thickness = clamp(value, THICKNESS_MIN, THICKNESS_MAX);
	}

	public static int heightOffset()
	{
		return heightOffset;
	}

	public static void setHeightOffset(int value)
	{
		heightOffset = clamp(value, HEIGHT_OFFSET_MIN, HEIGHT_OFFSET_MAX);
	}

	public static int opacity()
	{
		return opacity;
	}

	public static void setOpacity(int value)
	{
		opacity = clamp(value, OPACITY_MIN, OPACITY_MAX);
	}

	public static int driftSpeed()
	{
		return driftSpeed;
	}

	public static void setDriftSpeed(int value)
	{
		driftSpeed = clamp(value, DRIFT_SPEED_MIN, DRIFT_SPEED_MAX);
	}

	public static int brightness()
	{
		return brightness;
	}

	public static void setBrightness(int value)
	{
		brightness = clamp(value, BRIGHTNESS_MIN, BRIGHTNESS_MAX);
	}

	public static int sunsetGlow()
	{
		return sunsetGlow;
	}

	public static void setSunsetGlow(int value)
	{
		sunsetGlow = clamp(value, SUNSET_GLOW_MIN, SUNSET_GLOW_MAX);
	}

	public static void resetToDefaults()
	{
		enabled = ENABLED_DEFAULT;
		distance = DISTANCE_DEFAULT;
		coverage = COVERAGE_DEFAULT;
		cloudSize = CLOUD_SIZE_DEFAULT;
		thickness = THICKNESS_DEFAULT;
		heightOffset = HEIGHT_OFFSET_DEFAULT;
		opacity = OPACITY_DEFAULT;
		driftSpeed = DRIFT_SPEED_DEFAULT;
		brightness = BRIGHTNESS_DEFAULT;
		sunsetGlow = SUNSET_GLOW_DEFAULT;
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
