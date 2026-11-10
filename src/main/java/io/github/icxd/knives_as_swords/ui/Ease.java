package io.github.icxd.knives_as_swords.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Numbers that glide to where they should be instead of jumping there: a switch sliding over, a
 * row lighting up under the mouse, a panel unfolding.
 *
 * <p>Each number is known by a key of the caller's choosing and remembered between frames.
 * Client thread only.
 */
public final class Ease {
	private static final Map<Object, float[]> VALUES = new HashMap<>();
	private static long lastNanos;
	private static float seconds;

	private Ease() {
	}

	/** A new frame begins: measures how much time has passed since the last. Once per frame drawn. */
	public static void frame() {
		long now = System.nanoTime();
		seconds = Math.clamp((now - lastNanos) / 1e9f, 0f, 0.1f);
		lastNanos = now;
	}

	/** Seconds between the last two frames, never more than a tenth. */
	public static float seconds() {
		return seconds;
	}

	/**
	 * Where the number known by {@code key} is now, on its way to {@code target}. The first time a
	 * key is asked for, it is at its target already.
	 *
	 * @param rate how quickly it gets there: 10 is leisurely, 30 is snappy
	 */
	public static float toward(Object key, float target, float rate) {
		float[] value = VALUES.computeIfAbsent(key, fresh -> new float[] {target});
		value[0] = step(value[0], target, rate, seconds);
		return value[0];
	}

	/** Where the number known by {@code key} is now, without moving it. */
	public static float at(Object key, float fallback) {
		float[] value = VALUES.get(key);
		return value != null ? value[0] : fallback;
	}

	/** Puts the number known by {@code key} somewhere at once, to glide on from there. */
	public static void set(Object key, float value) {
		VALUES.computeIfAbsent(key, fresh -> new float[1])[0] = value;
	}

	/** Forgets every number, as when the screen they belonged to is closed. */
	public static void forget() {
		VALUES.clear();
	}

	/**
	 * One step of gliding: each moment the number covers the same share of what is left, so it
	 * starts fast and settles gently, whatever the frame rate.
	 */
	public static float step(float value, float target, float rate, float seconds) {
		float moved = value + (target - value) * (1f - (float) Math.exp(-rate * seconds));
		return Math.abs(target - moved) < 0.002f ? target : moved;
	}

	/** Makes a steady 0-to-1 into one that starts fast and settles gently. */
	public static float out(float t) {
		float left = 1f - Math.clamp(t, 0f, 1f);
		return 1f - left * left * left;
	}
}
