package io.github.icxd.knives_as_swords.mixin;

import io.github.icxd.knives_as_swords.render.FirstPersonKnife;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells the first-person knife that the swing about to start lands on an entity. Reads only. */
@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
	@Inject(method = "attack", at = @At("HEAD"))
	private void knives$onAttack(Player player, Entity entity, CallbackInfo callback) {
		// Read before the attack resets it. Nothing here changes what the attack does.
		FirstPersonKnife.onAttackEntity(player.getAttackStrengthScale(0.5f) > 0.9f);
	}
}
