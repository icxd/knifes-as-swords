package io.github.icxd.knives_as_swords.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.icxd.knives_as_swords.render.FirstPersonKnife;
import io.github.icxd.knives_as_swords.render.KnifeReplacement;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;

/** Lets a sword in the main hand be drawn as an animated knife in first person. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsAndItemsRendererMixin {
	@WrapMethod(method = "submitArmWithItem")
	private void knives$drawAnimatedKnife(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState state,
			float partialTicks, float xRot, InteractionHand hand, float attack, ItemStack itemStack,
			float inverseArmHeight, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords,
			Operation<Void> original) {
		AvatarRenderState avatar = playerState.avatarRenderState;
		if (hand == InteractionHand.MAIN_HAND && !state.isScoping && avatar != null
				&& !(avatar.isUsingItem && avatar.useItemHand == hand)
				&& KnifeReplacement.isSword(itemStack)
				&& FirstPersonKnife.submit(itemStack, avatar.mainArm, poseStack, submitNodeCollector, lightCoords)) {
			return;
		}
		original.call(playerState, state, partialTicks, xRot, hand, attack, itemStack, inverseArmHeight, poseStack,
				submitNodeCollector, lightCoords);
	}
}
