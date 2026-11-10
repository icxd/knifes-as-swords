package io.github.icxd.knives_as_swords.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.icxd.knives_as_swords.render.KnifeReplacement;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Every item drawn anywhere (hands, inventories, the ground, item frames) gets its render layers
 * here. Swords are let through as usual and then have their layers swapped for a knife.
 */
@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverMixin {
	@WrapMethod(method = "appendItemLayers")
	private void knives$drawSwordAsKnife(ItemStackRenderState output, ItemStack item, ItemDisplayContext displayContext,
			@Nullable Level level, @Nullable ItemOwner owner, int seed, Operation<Void> original) {
		if (!KnifeReplacement.isSword(item)) {
			original.call(output, item, displayContext, level, owner, seed);
			return;
		}
		int firstLayer = ((ItemStackRenderStateAccessor) output).knives$getActiveLayerCount();
		original.call(output, item, displayContext, level, owner, seed);
		KnifeReplacement.replaceLayers(output, firstLayer, item, displayContext);
	}
}
