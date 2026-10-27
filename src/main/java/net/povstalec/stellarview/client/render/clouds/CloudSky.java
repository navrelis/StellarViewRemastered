package net.povstalec.stellarview.client.render.clouds;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * Per frame inputs of the cloud colour model and of the coverage, computed on the render thread.
 * <p>
 * Lighting is analytic: the shader mixes a few base colours by sun height (day, twilight, night), by the angle
 * between the view ray and the sun (warm glow toward the sun, violet away from it) and by weather.
 * Nothing here allocates.
 */
final class CloudSky
{
	/** Opacity of one box at 100 % opacity */
	private static final float BOX_ALPHA = 0.22F;

	/** Visible fraction of cells reached in full rain (or the clear weather value, if that is higher) */
	private static final float RAIN_COVERAGE = 0.75F;

	// Sun direction in world space, unit length
	float sunX;
	float sunY;
	float sunZ;

	// Direction the box faces are lit from: the sun by day, the moon by night
	float lightX;
	float lightY;
	float lightZ;
	/** How directional the face lighting is (0 = flat, overcast) */
	float lightStrength;

	float day;
	float twilight;
	float moonLight;
	float nightVision;

	float rain;
	float thunder;
	float flash;

	/** Density threshold for the current coverage and weather */
	float threshold;
	float boxAlpha;

	float fogRed;
	float fogGreen;
	float fogBlue;
	/** Part of the cloud radius the current fog lets through: 1 normally, less in thick fog, blindness or darkness */
	float fogReach;

	float brightness;
	float glow;

	void update(ClientLevel level, Camera camera, float partialTick, float renderDistance)
	{
		// Same sun path as Vanilla's sky: rises in the east (+X), sets in the west (-X)
		float angle = level.getSunAngle(partialTick);
		sunX = (float) -Math.sin(angle);
		sunY = (float) Math.cos(angle);
		sunZ = 0.0F;

		day = smoothstep(-0.12F, 0.3F, sunY);
		float horizon = clamp(1.0F - Math.abs(sunY + 0.04F) / 0.34F, 0.0F, 1.0F);
		twilight = horizon * horizon * (3.0F - 2.0F * horizon);
		moonLight = level.getMoonBrightness();

		rain = level.getRainLevel(partialTick);
		thunder = level.getThunderLevel(partialTick);

		int flashTime = level.getSkyFlashTime();
		flash = flashTime > 0 ? Math.min(flashTime - partialTick, 1.0F) * 0.45F : 0.0F;

		boolean sunUp = sunY > -0.08F;
		lightX = sunUp ? sunX : -sunX;
		lightY = sunUp ? sunY : -sunY;
		lightZ = sunUp ? sunZ : -sunZ;
		lightStrength = (sunUp ? 1.0F : 0.5F * moonLight) * (1.0F - 0.7F * rain);

		nightVision = 0.0F;
		Entity entity = camera.getEntity();
		if(entity instanceof LivingEntity living && living.hasEffect(MobEffects.NIGHT_VISION))
			nightVision = GameRenderer.getNightVisionScale(living, partialTick);

		// Weather raises the coverage smoothly (rain and thunder levels already fade with partial ticks)
		float clear = 0.05F + 0.65F * CloudSettings.coverage() / 100.0F;
		float wet = clear + (Math.max(clear, RAIN_COVERAGE) - clear) * rain;
		float stormy = wet + Math.max(0.0F, CloudGenerator.MAX_COVERAGE - wet) * thunder;
		threshold = CloudGenerator.thresholdForCoverage(stormy);
		boxAlpha = BOX_ALPHA * CloudSettings.opacity() / 100.0F * (1.0F + 0.35F * rain);

		// Vanilla's cloud pass applies the level fog colour first as well. The array is live, so copy the values
		FogRenderer.levelFogColor();
		float[] fogColor = RenderSystem.getShaderFogColor();
		fogRed = fogColor[0];
		fogGreen = fogColor[1];
		fogBlue = fogColor[2];

		float fogStart = RenderSystem.getShaderFogStart();
		float fogEnd = RenderSystem.getShaderFogEnd();
		fogReach = fogStart >= fogEnd ? 1.0F : clamp(fogEnd / Math.max(renderDistance, 1.0F), 0.0F, 1.0F);

		brightness = CloudSettings.brightness() / 100.0F;
		glow = CloudSettings.sunsetGlow() / 100.0F;
	}

	private static float smoothstep(float edge0, float edge1, float value)
	{
		float t = clamp((value - edge0) / (edge1 - edge0), 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}

	private static float clamp(float value, float min, float max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
