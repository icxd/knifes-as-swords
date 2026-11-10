package io.github.icxd.knives_as_swords.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
	@Accessor("itemTransform")
	ItemTransform knives$getItemTransform();
}
