package io.github.icxd.knives_as_swords.mixin;

import io.github.icxd.knives_as_swords.render.FirstPersonKnife;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Tells the first-person knife when the player's own main hand starts a swing. Reads only. */
@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z",
			at = @At("RETURN"))
	private void knives$onSwing(InteractionHand hand, SwingAnimation animation, boolean sendToSwingingEntity,
			CallbackInfoReturnable<Boolean> callback) {
		if (hand == InteractionHand.MAIN_HAND && callback.getReturnValue()
				&& (Object) this == Minecraft.getInstance().player) {
			FirstPersonKnife.onSwing();
		}
	}
}
