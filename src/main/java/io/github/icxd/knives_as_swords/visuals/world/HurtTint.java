package io.github.icxd.knives_as_swords.visuals.world;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.icxd.knives_as_swords.mixin.OverlayTextureAccessor;
import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * The colour mobs and players flash in when they are hurt: any colour instead of red.
 *
 * <p>The flash is the game's; only its colour changes.
 */
public final class HurtTint extends Visual {
	/** The game's own: red, mixed in at about a third. */
	private static final int VANILLA_RGB = 0xFF0000;
	/** How much of the entity's own colour is kept; the rest is the flash. As the game has it. */
	private static final int KEPT = 0xB2;

	private final Setting.Color color = color("color", 0x3FC8FF);
	/** How strong the flash is: 0.3 is the game's own strength. */
	private final Setting.Slider strength = slider("strength", 0.3f, 0.1f, 0.8f, 0.05f);

	/** What the texture holds right now, so that it is only written when that changes. */
	private int applied = VANILLA_RGB << 8 | KEPT;

	public HurtTint() {
		super("hurt_tint", Category.WORLD, false);
	}

	@Override
	protected void switched() {
		if (!on()) {
			apply(VANILLA_RGB, KEPT);
		}
	}

	@Override
	public void tick(Minecraft client) {
		apply(color.rgb(), Math.clamp(Math.round((1f - strength.get()) * 255f), 0, 255));
	}

	/** Rewrites the part of the game's overlay texture that the hurt flash is read from. */
	private void apply(int rgb, int kept) {
		int wanted = rgb << 8 | kept;
		if (wanted == applied) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.gameRenderer == null) {
			return;
		}
		DynamicTexture texture = ((OverlayTextureAccessor) client.gameRenderer.overlayTexture()).knives$getTexture();
		NativeImage pixels = texture.getPixels();
		if (pixels == null) {
			return;
		}
		// The upper half of the texture is the flash; the lower half (the white of a creeper about to blow) stays.
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) {
				pixels.setPixel(x, y, kept << 24 | rgb);
			}
		}
		texture.upload();
		applied = wanted;
	}
}
