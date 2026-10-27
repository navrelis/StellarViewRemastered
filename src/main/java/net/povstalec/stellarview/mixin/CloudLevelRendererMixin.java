package net.povstalec.stellarview.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.povstalec.stellarview.client.render.clouds.StellarCloudRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks Stellar View's cloud renderer into Vanilla's cloud pass and its lifecycle.
 * <p>
 * The cloud hook is order independent against the other HEAD handlers on {@code renderClouds}
 * (Fabric API's dimension cloud renderers, Porting Lib, Sporran, Sodium Extra): it only cancels in dimensions none of
 * them claims, and it returns without cancelling in every other case.
 */
@Mixin(LevelRenderer.class)
public class CloudLevelRendererMixin
{
	@Shadow
	@Nullable
	private ClientLevel level;

	@Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
	private void renderStellarViewClouds(PoseStack poseStack, Matrix4f modelViewMatrix, Matrix4f projectionMatrix, float partialTick, double camX, double camY, double camZ, CallbackInfo info)
	{
		if(StellarCloudRenderer.renderClouds((LevelRenderer) (Object) this, level, poseStack, modelViewMatrix, projectionMatrix, partialTick, camX, camY, camZ))
			info.cancel();
	}

	@Inject(method = "setLevel", at = @At("HEAD"))
	private void resetStellarViewClouds(@Nullable ClientLevel newLevel, CallbackInfo info)
	{
		StellarCloudRenderer.onLevelChanged();
	}

	@Inject(method = "onResourceManagerReload", at = @At("RETURN"))
	private void reloadStellarViewClouds(ResourceManager resourceManager, CallbackInfo info)
	{
		StellarCloudRenderer.onResourceReload();
	}

	@Inject(method = "close", at = @At("HEAD"))
	private void closeStellarViewClouds(CallbackInfo info)
	{
		StellarCloudRenderer.onClose();
	}
}
