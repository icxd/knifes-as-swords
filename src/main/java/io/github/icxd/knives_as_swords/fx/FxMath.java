package io.github.icxd.knives_as_swords.fx;

/** Small helpers the effects share: repeatable randomness, smooth noise and colour packing. */
public final class FxMath {
	private FxMath() {
	}

	/** Scrambles an integer; the same input always gives the same, unrelated-looking output. */
	public static int hash(int x) {
		x ^= x >>> 16;
		x *= 0x7feb352d;
		x ^= x >>> 15;
		x *= 0x846ca68b;
		x ^= x >>> 16;
		return x;
	}

	public static int hash(int a, int b) {
		return hash(a * 0x9E3779B9 + hash(b));
	}

	public static int hash(int a, int b, int c) {
		return hash(hash(a, b), c);
	}

	/** Turns a hash into a number from 0 (inclusive) to 1 (exclusive). */
	public static float unit(int hash) {
		return (hash >>> 8) * (1f / (1 << 24));
	}

	/** Turns a hash into a number from -1 to 1. */
	public static float signed(int hash) {
		return unit(hash) * 2f - 1f;
	}

	public static float smoothstep(float from, float to, float x) {
		if (from == to) {
			return x < from ? 0f : 1f;
		}
		float t = Math.clamp((x - from) / (to - from), 0f, 1f);
		return t * t * (3f - 2f * t);
	}

	/** Smooth noise along a line, from -1 to 1: wanders without jumps as {@code x} grows. */
	public static float noise(float x, int seed) {
		int cell = (int) Math.floor(x);
		float t = x - cell;
		t = t * t * (3f - 2f * t);
		float a = signed(hash(cell, seed));
		float b = signed(hash(cell + 1, seed));
		return a + (b - a) * t;
	}

	/** Packs a colour the way Minecraft's vertex buffers take it; every part is clamped to 0..1. */
	public static int argb(float red, float green, float blue, float alpha) {
		return (channel(alpha) << 24) | (channel(red) << 16) | (channel(green) << 8) | channel(blue);
	}

	/** The colour with its alpha multiplied by {@code factor}. */
	public static int fade(int argb, float factor) {
		int alpha = Math.clamp(Math.round((argb >>> 24) * factor), 0, 255);
		return (alpha << 24) | (argb & 0x00FFFFFF);
	}

	/** A colour between two others: {@code t} of 0 gives the first, 1 the second. */
	public static int mix(int from, int to, float t) {
		t = Math.clamp(t, 0f, 1f);
		int result = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int a = (from >>> shift) & 0xFF;
			int b = (to >>> shift) & 0xFF;
			result |= Math.round(a + (b - a) * t) << shift;
		}
		return result;
	}

	/** A fully saturated colour of the given hue (0 to 1, wrapping), with alpha 1. */
	public static int hue(float hue) {
		float h = (hue - (float) Math.floor(hue)) * 6f;
		float red = Math.clamp(Math.abs(h - 3f) - 1f, 0f, 1f);
		float green = Math.clamp(2f - Math.abs(h - 2f), 0f, 1f);
		float blue = Math.clamp(2f - Math.abs(h - 4f), 0f, 1f);
		return argb(red, green, blue, 1f);
	}

	private static int channel(float value) {
		return Math.clamp(Math.round(value * 255f), 0, 255);
	}
}
