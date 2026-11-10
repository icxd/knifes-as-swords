package io.github.icxd.knives_as_swords.fx;

import net.minecraft.client.Minecraft;

/**
 * The time effects go by: seconds that pass as they do for the player, and stand still while the
 * game is paused. Client thread only.
 */
public final class FxClock {
	/** No single look at the clock moves it further than this: after a hitch, effects pick up where they were. */
	private static final float MAX_STEP_SECONDS = 0.1f;
	/**
	 * The clock starts over after this many seconds (a little over an hour), which keeps fractions
	 * of a second exact however long the game runs.
	 */
	private static final double PERIOD_SECONDS = 4096.0;

	private static long lastNanos = System.nanoTime();
	private static double seconds;
	private static int restarts;

	private FxClock() {
	}

	/** Seconds since the clock last started over. */
	public static float seconds() {
		long now = System.nanoTime();
		float step = Math.clamp((now - lastNanos) / 1e9f, 0f, MAX_STEP_SECONDS);
		lastNanos = now;
		if (!Minecraft.getInstance().isPaused()) {
			seconds += step;
		}
		if (seconds >= PERIOD_SECONDS) {
			seconds -= PERIOD_SECONDS;
			restarts++;
		}
		return (float) seconds;
	}

	/** How often the clock has started over. Whoever remembers times has to forget them when this changes. */
	public static int restarts() {
		return restarts;
	}
}
