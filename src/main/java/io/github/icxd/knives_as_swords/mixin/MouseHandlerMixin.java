package io.github.icxd.knives_as_swords.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.icxd.knives_as_swords.visuals.hud.ClickCounter;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Counts the player's own mouse clicks for the clicks-per-second display. Reads only: the click
 * goes on to the game exactly as it came.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	/** What the game calls a button going down, as opposed to coming up. */
	private static final int PRESS = 1;

	@Inject(method = "onButton", at = @At("HEAD"))
	private void knives$countClick(long handle, MouseButtonInfo rawButtonInfo, int action, CallbackInfo callback) {
		if (action != PRESS) {
			return;
		}
		if (rawButtonInfo.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			ClickCounter.pressed(true);
		} else if (rawButtonInfo.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			ClickCounter.pressed(false);
		}
	}
}
