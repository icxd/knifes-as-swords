package io.github.icxd.knives_as_swords.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
	@Accessor("activeLayerCount")
	int knives$getActiveLayerCount();

	@Accessor("activeLayerCount")
	void knives$setActiveLayerCount(int count);

	@Accessor("layers")
	ItemStackRenderState.LayerRenderState[] knives$getLayers();
}
