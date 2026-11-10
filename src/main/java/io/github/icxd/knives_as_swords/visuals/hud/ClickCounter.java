package io.github.icxd.knives_as_swords.visuals.hud;

import java.util.ArrayDeque;

/**
 * Counts the player's own mouse clicks, so that the heads-up display can say how many there were
 * in the last second. Only counts; a click goes on to the game untouched.
 */
public final class ClickCounter {
	private static final long WINDOW_NANOS = 1_000_000_000L;
	private static final ArrayDeque<Long> LEFT = new ArrayDeque<>();
	private static final ArrayDeque<Long> RIGHT = new ArrayDeque<>();

	private ClickCounter() {
	}

	/** A mouse button went down. */
	public static void pressed(boolean left) {
		(left ? LEFT : RIGHT).addLast(System.nanoTime());
	}

	/** Left clicks within the last second. */
	public static int left() {
		return count(LEFT, System.nanoTime());
	}

	/** Right clicks within the last second. */
	public static int right() {
		return count(RIGHT, System.nanoTime());
	}

	static int count(ArrayDeque<Long> clicks, long now) {
		while (!clicks.isEmpty() && now - clicks.peekFirst() > WINDOW_NANOS) {
			clicks.removeFirst();
		}
		return clicks.size();
	}
}
