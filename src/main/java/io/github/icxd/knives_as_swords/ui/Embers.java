package io.github.icxd.knives_as_swords.ui;

import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.fx.Gradient;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small lights drifting up behind a menu, like the sparks a knife sheds: the backdrop that ties
 * the menus to the effects.
 *
 * <p>Each one keeps to its own lane, rising at its own pace, swaying a little and twinkling; at
 * the top it starts again from below. Where one is follows from the time alone.
 */
public final class Embers {
	private final int count;
	private final int seed;

	public Embers(int count, int seed) {
		this.count = count;
		this.seed = seed;
	}

	/**
	 * @param time seconds, on any steadily running clock
	 * @param strength how bright they are overall, 0 to 1
	 */
	public void draw(GuiGraphicsExtractor graphics, int width, int height, float time, Gradient colors,
			float strength) {
		for (int i = 0; i < count; i++) {
			float lane = FxMath.unit(FxMath.hash(i, seed, 1));
			float pace = 0.025f + 0.06f * FxMath.unit(FxMath.hash(i, seed, 2));
			float phase = FxMath.unit(FxMath.hash(i, seed, 3));
			float size = 2f + 5f * FxMath.unit(FxMath.hash(i, seed, 4));
			// How far up it is, 0 at the bottom to 1 at the top, round and round.
			float rise = (time * pace + phase) % 1f;
			float x = (lane + 0.02f * (float) Math.sin(time * 0.7f + phase * 40f)) * width;
			float y = height + 10f - rise * (height + 20f);
			float twinkle = 0.55f + 0.45f * (float) Math.sin(time * (1.5f + 2f * phase) + phase * 17f);
			// Fading in from below and out towards the top.
			float fade = Math.min(rise * 6f, 1f) * (1f - rise * rise);
			int color = colors.at(FxMath.unit(FxMath.hash(i, seed, 5)));
			Glow.dot(graphics, x, y, size * 2.2f, FxMath.fade(color, 0.22f * strength * fade * twinkle));
			Glow.dot(graphics, x, y, size * 0.7f, FxMath.fade(color, 0.8f * strength * fade * twinkle));
		}
	}

	/** Where the n-th light is at a moment, as shares of the screen's width and height from its top left. */
	float[] place(int n, float time) {
		float lane = FxMath.unit(FxMath.hash(n, seed, 1));
		float pace = 0.025f + 0.06f * FxMath.unit(FxMath.hash(n, seed, 2));
		float phase = FxMath.unit(FxMath.hash(n, seed, 3));
		float rise = (time * pace + phase) % 1f;
		return new float[] {lane + 0.02f * (float) Math.sin(time * 0.7f + phase * 40f), 1f - rise};
	}
}
