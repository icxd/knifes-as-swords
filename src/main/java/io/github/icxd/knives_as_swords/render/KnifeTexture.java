package io.github.icxd.knives_as_swords.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Transparency;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.MipmapGenerator;
import net.minecraft.client.renderer.texture.MipmapStrategy;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * A knife's colour texture on the GPU: smoothly filtered and mipmapped, unlike Minecraft's own
 * dynamic textures, because these are large photographic images shown on a small model.
 */
final class KnifeTexture extends AbstractTexture {
	private static final int MAX_MIP_LEVEL = 5;

	/** Uploads the image and closes it. Render thread only. */
	KnifeTexture(Identifier id, NativeImage image) {
		NativeImage[] mips = {image};
		try {
			int smallestSide = Math.min(image.getWidth(), image.getHeight());
			int evenTo = Math.min(Integer.lowestOneBit(image.getWidth()), Integer.lowestOneBit(image.getHeight()));
			int mipLevel = Math.min(MAX_MIP_LEVEL, Mth.log2(Math.min(smallestSide, evenTo)));
			mips = MipmapGenerator.generateMipLevels(id, mips, mipLevel, MipmapStrategy.MEAN, 0f, Transparency.NONE);

			GpuDevice device = RenderSystem.getDevice();
			this.texture = device.createTexture(id::toString, 5, GpuFormat.RGBA8_UNORM, image.getWidth(),
					image.getHeight(), 1, mips.length);
			this.textureView = device.createTextureView(this.texture);
			for (int level = 0; level < mips.length; level++) {
				device.createCommandEncoder().writeToTexture(this.texture, mips[level], level, 0, 0, 0);
			}
			this.sampler = RenderSystem.getSamplerCache().getSampler(AddressMode.REPEAT, AddressMode.REPEAT,
					FilterMode.LINEAR, FilterMode.LINEAR, mips.length > 1);
		} finally {
			for (NativeImage mip : mips) {
				mip.close();
			}
		}
	}
}
