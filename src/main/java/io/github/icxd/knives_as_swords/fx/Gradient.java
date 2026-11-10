package io.github.icxd.knives_as_swords.fx;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * A run of colours: a few colours at chosen places between 0 and 1, blending into each other in
 * between. Effects take their colours from one, from fresh (0) to fading (1).
 *
 * <p>Never changes once made.
 */
public final class Gradient {
	/** More colours than this in one gradient are dropped. */
	public static final int MAX_STOPS = 8;

	/**
	 * One colour of a gradient.
	 *
	 * @param at where it sits, from 0 to 1
	 * @param rgb the colour as {@code 0xRRGGBB}
	 */
	public record Stop(float at, int rgb) {
	}

	private final float[] places;
	private final int[] colors;

	private Gradient(float[] places, int[] colors) {
		this.places = places;
		this.colors = colors;
	}

	/**
	 * A gradient through the given colours, in the order of their places whatever order they are
	 * given in. Without any colours it is plain white.
	 */
	public static Gradient of(List<Stop> stops) {
		List<Stop> sorted = new ArrayList<>();
		for (Stop stop : stops) {
			if (sorted.size() < MAX_STOPS && stop != null) {
				float at = Float.isNaN(stop.at()) ? 0f : Math.clamp(stop.at(), 0f, 1f);
				sorted.add(new Stop(at, stop.rgb() & 0xFFFFFF));
			}
		}
		if (sorted.isEmpty()) {
			sorted.add(new Stop(0f, 0xFFFFFF));
		}
		// Stable, so colours sharing a place keep the order they were given in.
		sorted.sort(Comparator.comparingDouble(Stop::at));
		float[] places = new float[sorted.size()];
		int[] colors = new int[sorted.size()];
		for (int i = 0; i < places.length; i++) {
			places[i] = sorted.get(i).at();
			colors[i] = 0xFF000000 | sorted.get(i).rgb();
		}
		return new Gradient(places, colors);
	}

	/** A gradient through the given colours ({@code 0xRRGGBB}), spread evenly from 0 to 1. */
	public static Gradient evenly(int... rgb) {
		List<Stop> stops = new ArrayList<>();
		for (int i = 0; i < rgb.length; i++) {
			stops.add(new Stop(rgb.length > 1 ? i / (float) (rgb.length - 1) : 0f, rgb[i]));
		}
		return of(stops);
	}

	/** The colours, in the order of their places. */
	public List<Stop> stops() {
		List<Stop> stops = new ArrayList<>(places.length);
		for (int i = 0; i < places.length; i++) {
			stops.add(new Stop(places[i], colors[i] & 0xFFFFFF));
		}
		return stops;
	}

	public int size() {
		return places.length;
	}

	/** The colour at a place, fully opaque. Before the first colour and after the last, theirs. */
	public int at(float t) {
		if (!(t > places[0])) {
			return colors[0];
		}
		int last = places.length - 1;
		if (t >= places[last]) {
			return colors[last];
		}
		int next = 1;
		while (places[next] < t) {
			next++;
		}
		float span = places[next] - places[next - 1];
		return FxMath.mix(colors[next - 1], colors[next], span > 0f ? (t - places[next - 1]) / span : 1f);
	}

	/**
	 * The colour at a place that may run on beyond 1: there the gradient is gone through
	 * backwards, then forwards again, and so on, so that it never jumps.
	 */
	public int backAndForth(float t) {
		float folded = Math.abs(t) % 2f;
		return at(folded > 1f ? 2f - folded : folded);
	}

	/**
	 * The colour for something that is {@code t} of the way from fresh (0) to fading (1), when the
	 * colours may also run through the gradient by themselves as time passes.
	 *
	 * @param time seconds
	 * @param cycle how often per second the colours run through the gradient; 0 for not at all
	 */
	public int at(float t, float time, float cycle) {
		if (cycle == 0f) {
			return at(t);
		}
		// While they cycle, one effect from start to end spans a good part of the gradient, not all of it.
		return backAndForth(time * cycle + t * 0.75f);
	}

	/** {@code #RRGGBB}, as colours are written in the config file. */
	public static String hex(int rgb) {
		return String.format(Locale.ROOT, "#%06X", rgb & 0xFFFFFF);
	}

	/**
	 * Reads a colour written as {@code #RRGGBB} or {@code RRGGBB}.
	 *
	 * @return the colour as {@code 0xRRGGBB}, or -1 if the text is not one
	 */
	public static int parse(String text) {
		if (text == null) {
			return -1;
		}
		String digits = text.strip();
		if (digits.startsWith("#")) {
			digits = digits.substring(1);
		}
		if (digits.length() != 6) {
			return -1;
		}
		int value = 0;
		for (int i = 0; i < 6; i++) {
			int digit = Character.digit(digits.charAt(i), 16);
			if (digit < 0) {
				return -1;
			}
			value = value << 4 | digit;
		}
		return value;
	}

	/**
	 * A colour from hue, saturation and brightness.
	 *
	 * @param hue once round the colour wheel is 0 to 1; wraps
	 * @param saturation 0 (grey) to 1 (pure colour)
	 * @param brightness 0 (black) to 1 (full)
	 * @return the colour as {@code 0xRRGGBB}
	 */
	public static int fromHsv(float hue, float saturation, float brightness) {
		int pure = FxMath.hue(hue);
		saturation = Math.clamp(saturation, 0f, 1f);
		brightness = Math.clamp(brightness, 0f, 1f);
		int result = 0;
		for (int shift = 0; shift <= 16; shift += 8) {
			float channel = ((pure >>> shift) & 0xFF) / 255f;
			channel = (1f + (channel - 1f) * saturation) * brightness;
			result |= Math.round(channel * 255f) << shift;
		}
		return result;
	}

	/** Splits a colour ({@code 0xRRGGBB}) into hue, saturation and brightness, each 0 to 1. */
	public static float[] toHsv(int rgb) {
		float red = ((rgb >>> 16) & 0xFF) / 255f;
		float green = ((rgb >>> 8) & 0xFF) / 255f;
		float blue = (rgb & 0xFF) / 255f;
		float max = Math.max(red, Math.max(green, blue));
		float min = Math.min(red, Math.min(green, blue));
		float range = max - min;
		float hue = 0f;
		if (range > 0f) {
			if (max == red) {
				hue = ((green - blue) / range + 6f) % 6f;
			} else if (max == green) {
				hue = (blue - red) / range + 2f;
			} else {
				hue = (red - green) / range + 4f;
			}
			hue /= 6f;
		}
		return new float[] {hue, max > 0f ? range / max : 0f, max};
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Gradient gradient && java.util.Arrays.equals(places, gradient.places)
				&& java.util.Arrays.equals(colors, gradient.colors);
	}

	@Override
	public int hashCode() {
		return java.util.Arrays.hashCode(places) * 31 + java.util.Arrays.hashCode(colors);
	}

	@Override
	public String toString() {
		return "Gradient" + stops();
	}
}
