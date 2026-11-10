package io.github.icxd.knives_as_swords.visuals.hud;

import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.ui.Glow;
import io.github.icxd.knives_as_swords.ui.Theme;
import io.github.icxd.knives_as_swords.visuals.Setting;
import java.util.Locale;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The movement keys and mouse buttons as little keys of dark glass that light up while held: for
 * showing others (or a recording) what the player is pressing.
 */
public final class Keystrokes extends HudWidget {
	private static final int KEY = 20;
	private static final int GAP = 2;
	private static final int WIDTH = KEY * 3 + GAP * 2;
	private static final int MOUSE_HEIGHT = 16;
	private static final int SPACE_HEIGHT = 10;
	private static final int TEXT = 0xFFD8D8E4;

	private final Setting.Toggle mouse = toggle("mouse", true);
	private final Setting.Toggle space = toggle("space", true);

	/** How lit each box is, 0 to 1: forwards, left, back, right, attack, use, jump. */
	private final float[] lit = new float[7];
	private long lastNanos;

	public Keystrokes() {
		super("keystrokes", false, 0.02f, 0.5f);
	}

	@Override
	protected boolean framed() {
		return false;
	}

	@Override
	protected int width(Minecraft client, boolean editing) {
		return WIDTH;
	}

	@Override
	protected int height(Minecraft client, boolean editing) {
		return KEY * 2 + GAP + (mouse.on() ? GAP + MOUSE_HEIGHT : 0) + (space.on() ? GAP + SPACE_HEIGHT : 0);
	}

	@Override
	protected void draw(GuiGraphicsExtractor graphics, Minecraft client, int width, int height, boolean editing) {
		Options options = client.options;
		KeyMapping[] keys = {options.keyUp, options.keyLeft, options.keyDown, options.keyRight, options.keyAttack,
				options.keyUse, options.keyJump};
		long now = System.nanoTime();
		float seconds = Math.clamp((now - lastNanos) / 1e9f, 0f, 0.1f);
		lastNanos = now;
		for (int i = 0; i < keys.length; i++) {
			// Lights up at once, fades out quickly.
			lit[i] = keys[i].isDown() ? 1f : Math.max(lit[i] - seconds * 6f, 0f);
		}

		// Each key takes its colour from where it sits, left to right, so the gradient runs across them.
		key(graphics, client, KEY + GAP, 0, KEY, KEY, lit[0], name(keys[0]), 0.25f);
		int y = KEY + GAP;
		key(graphics, client, 0, y, KEY, KEY, lit[1], name(keys[1]), 0f);
		key(graphics, client, KEY + GAP, y, KEY, KEY, lit[2], name(keys[2]), 0.25f);
		key(graphics, client, (KEY + GAP) * 2, y, KEY, KEY, lit[3], name(keys[3]), 0.5f);
		y += KEY;
		if (mouse.on()) {
			y += GAP;
			int half = (WIDTH - GAP) / 2;
			key(graphics, client, 0, y, half, MOUSE_HEIGHT, lit[4], "LMB", 0.1f);
			key(graphics, client, half + GAP, y, WIDTH - half - GAP, MOUSE_HEIGHT, lit[5], "RMB", 0.4f);
			y += MOUSE_HEIGHT;
		}
		if (space.on()) {
			y += GAP;
			key(graphics, client, 0, y, WIDTH, SPACE_HEIGHT, lit[6], "", 0.25f);
			float middle = y + SPACE_HEIGHT / 2f - 0.5f;
			Glow.rounded(graphics, WIDTH / 4f, middle - 0.75f, WIDTH - WIDTH / 4f, middle + 0.75f, 0.75f,
					FxMath.mix(TEXT, 0xFFFFFFFF, lit[6]));
		}
	}

	/**
	 * One key: dark glass with a line of light along its bottom, flooded with light while held.
	 *
	 * @param along which of the widget's colours it has; see {@link #glowColor}
	 */
	private void key(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, int width, int height, float lit,
			String label, float along) {
		int color = glowColor(along);
		if (background.on()) {
			Glow.rounded(graphics, x, y, x + width, y + height, 3f, Theme.PLATE);
		}
		if (lit > 0f) {
			Glow.halo(graphics, x + 3f, y + 3f, x + width - 3f, y + height - 3f, 9f, FxMath.fade(color, 0.45f * lit));
			Glow.rounded(graphics, x, y, x + width, y + height, 3f, FxMath.fade(color, 0.8f * lit));
		}
		Glow.light(graphics, x + 3f, y + height - 1f, x + width - 3f, y + height, FxMath.fade(color, 0.45f + 0.4f * lit));
		if (!label.isEmpty()) {
			graphics.text(client.font, label, x + (width - client.font.width(label)) / 2,
					y + (height - client.font.lineHeight) / 2 + 1, FxMath.mix(TEXT, 0xFFFFFFFF, lit));
		}
	}

	/** What is written on a key's box: the key it is bound to, kept short. */
	private static String name(KeyMapping key) {
		String name = key.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
		return name.length() > 3 ? name.substring(0, 3) : name;
	}
}
