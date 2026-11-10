package io.github.icxd.knives_as_swords.mixin;

import io.github.icxd.knives_as_swords.fx.WorldFx;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the effects when the world starts gathering what to draw, and from where it is seen.
 * Knives drawn from then until the gathering ends are in the world; all others are in menus or
 * in the player's own hand. Reads only.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
	@Inject(method = "submitFeatures", at = @At("HEAD"))
	private void knives$beginEffects(LevelRenderState levelRenderState, SubmitNodeCollector submitNodeCollector,
			boolean renderOutline, CallbackInfo callback) {
		WorldFx.begin(levelRenderState.cameraRenderState);
	}
}
