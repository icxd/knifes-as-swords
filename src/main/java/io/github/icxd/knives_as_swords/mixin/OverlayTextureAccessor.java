package io.github.icxd.knives_as_swords.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The small texture the colour of the hurt flash is read from; the hurt colour visual repaints it. */
@Mixin(OverlayTexture.class)
public interface OverlayTextureAccessor {
	@Accessor("texture")
	DynamicTexture knives$getTexture();
}
