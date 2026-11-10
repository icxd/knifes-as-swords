package io.github.icxd.knives_as_swords.ui;

import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.fx.Gradient;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * What lies behind the mod's menus: a shade that is darkest at the top and bottom, light welling
 * up from below in the menu's colours, and embers drifting up through it.
 */
public final class Backdrop {
	private static final int DARK = 0xFF040408;
	private static final Embers EMBERS = new Embers(44, 7);

	private Backdrop() {
	}

	/**
	 * @param colors the menu's colours
	 * @param shade how dark it gets, 0 to 1: 1 for a screen that stands by itself, less for one
	 *     that has to leave the world in view
	 */
	public static void draw(GuiGraphicsExtractor graphics, int width, int height, Gradient colors, float shade) {
		int top = FxMath.fade(DARK, 0.84f * shade);
		int middle = FxMath.fade(DARK, 0.48f * shade);
		int bottom = FxMath.fade(DARK, 0.9f * shade);
		Glow.down(graphics, 0, 0, width, height / 2f, top, middle);
		Glow.down(graphics, 0, height / 2f, width, height, middle, bottom);

		int left = FxMath.fade(Theme.now(colors, 0f), 0.16f);
		int right = FxMath.fade(Theme.now(colors, 0.5f), 0.16f);
		Glow.light(graphics, 0, height * 0.5f, width, height, left & 0xFFFFFF, right & 0xFFFFFF, left, right);
		EMBERS.draw(graphics, width, height, Theme.seconds(), colors, 1f);
	}
}
