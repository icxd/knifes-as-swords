package io.github.icxd.knives_as_swords.visuals;

import io.github.icxd.knives_as_swords.fx.Gradient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import org.jspecify.annotations.Nullable;

/**
 * One thing about a visual that the player can set: a switch, a number, a choice, a colour.
 *
 * <p>Every setting can write itself as a short text and read itself back from one, which is how
 * settings are kept in the config file. Text that makes no sense is ignored, leaving the setting
 * as it was.
 */
public abstract sealed class Setting {
	private final String key;
	private BooleanSupplier shown = () -> true;

	private Setting(String key) {
		this.key = key;
	}

	/** The name the setting is saved under, unique within its visual. */
	public String key() {
		return key;
	}

	/** How the setting is written in the config file; {@code null} for one that is not saved. */
	public abstract @Nullable String write();

	/** Takes over what was written; anything that cannot be understood changes nothing. */
	public abstract void read(String text);

	/** Back to what the setting is when nobody has touched it. */
	public abstract void reset();

	/** Offers the setting only while the condition holds, such as another setting being switched on. */
	public Setting onlyWhen(BooleanSupplier condition) {
		shown = condition;
		return this;
	}

	public boolean shown() {
		return shown.getAsBoolean();
	}

	/** On or off. */
	public static final class Toggle extends Setting {
		private final boolean initial;
		private boolean on;

		public Toggle(String key, boolean initial) {
			super(key);
			this.initial = initial;
			this.on = initial;
		}

		public boolean on() {
			return on;
		}

		public void set(boolean on) {
			this.on = on;
		}

		@Override
		public String write() {
			return Boolean.toString(on);
		}

		@Override
		public void read(String text) {
			if (text.equals("true") || text.equals("false")) {
				on = text.equals("true");
			}
		}

		@Override
		public void reset() {
			on = initial;
		}
	}

	/** A number within limits, in steps. */
	public static final class Slider extends Setting {
		private final float initial;
		private final float min;
		private final float max;
		private final float step;
		private float value;

		/**
		 * @param step the distance between values that can be picked; decides how many decimals are shown
		 */
		public Slider(String key, float initial, float min, float max, float step) {
			super(key);
			this.min = min;
			this.max = Math.max(max, min);
			this.step = step > 0f ? step : 0.01f;
			this.initial = snap(initial);
			this.value = this.initial;
		}

		public float get() {
			return value;
		}

		public float min() {
			return min;
		}

		public float max() {
			return max;
		}

		/** Where the value is between the limits, 0 to 1. */
		public float fraction() {
			return max > min ? (value - min) / (max - min) : 0f;
		}

		public void set(float value) {
			if (!Float.isNaN(value)) {
				this.value = snap(value);
			}
		}

		public void setFraction(float fraction) {
			set(min + Math.clamp(fraction, 0f, 1f) * (max - min));
		}

		private float snap(float value) {
			float steps = Math.round((Math.clamp(value, min, max) - min) / step);
			return Math.clamp(min + steps * step, min, max);
		}

		/** The value as a player reads it: with as many decimals as the step calls for. */
		public String text() {
			int decimals = step >= 1f ? 0 : step >= 0.1f ? 1 : 2;
			return String.format(Locale.ROOT, "%." + decimals + "f", value);
		}

		@Override
		public String write() {
			return Float.toString(value);
		}

		@Override
		public void read(String text) {
			try {
				set(Float.parseFloat(text));
			} catch (NumberFormatException e) {
				// Not a number: keep what there is.
			}
		}

		@Override
		public void reset() {
			value = initial;
		}
	}

	/** One of a few named options. */
	public static final class Choice extends Setting {
		private final List<String> options;
		private int index;

		/**
		 * @param options the names of the options; the first is what the setting starts as
		 */
		public Choice(String key, String... options) {
			super(key);
			if (options.length == 0) {
				throw new IllegalArgumentException("a choice needs options");
			}
			this.options = List.of(options);
		}

		public String get() {
			return options.get(index);
		}

		public boolean is(String option) {
			return get().equals(option);
		}

		public List<String> options() {
			return options;
		}

		/** Moves on to the next option, or back to the one before; round and round. */
		public void step(boolean forwards) {
			index = Math.floorMod(index + (forwards ? 1 : -1), options.size());
		}

		@Override
		public String write() {
			return get();
		}

		@Override
		public void read(String text) {
			int found = options.indexOf(text);
			if (found >= 0) {
				index = found;
			}
		}

		@Override
		public void reset() {
			index = 0;
		}
	}

	/** One colour. */
	public static final class Color extends Setting {
		private final int initial;
		private int rgb;
		/** Kept alongside, so that sliding a colour to grey or black does not forget its hue. */
		private final float[] hsv;

		/**
		 * @param initial the colour as {@code 0xRRGGBB}
		 */
		public Color(String key, int initial) {
			super(key);
			this.initial = initial & 0xFFFFFF;
			this.rgb = this.initial;
			this.hsv = Gradient.toHsv(this.rgb);
		}

		/** The colour as {@code 0xRRGGBB}. */
		public int rgb() {
			return rgb;
		}

		/** The colour with the given opacity (0 to 1), ready for drawing. */
		public int argb(float alpha) {
			return (Math.clamp(Math.round(alpha * 255f), 0, 255) << 24) | rgb;
		}

		public void set(int rgb) {
			this.rgb = rgb & 0xFFFFFF;
			float[] fresh = Gradient.toHsv(this.rgb);
			// Grey has no hue and black no saturation: keep the old ones rather than jumping to 0.
			if (fresh[1] > 0f && fresh[2] > 0f) {
				hsv[0] = fresh[0];
			}
			if (fresh[2] > 0f) {
				hsv[1] = fresh[1];
			}
			hsv[2] = fresh[2];
		}

		/** Hue (0), saturation (1) or brightness (2), each from 0 to 1. */
		public float part(int which) {
			return hsv[which];
		}

		public void setPart(int which, float value) {
			hsv[which] = Math.clamp(value, 0f, 1f);
			rgb = Gradient.fromHsv(hsv[0], hsv[1], hsv[2]);
		}

		@Override
		public String write() {
			return Gradient.hex(rgb);
		}

		@Override
		public void read(String text) {
			int parsed = Gradient.parse(text);
			if (parsed >= 0) {
				set(parsed);
			}
		}

		@Override
		public void reset() {
			set(initial);
		}
	}

	/** A run of colours, for effects whose colour changes along the way. */
	public static final class Colors extends Setting {
		private final Gradient initial;
		private Gradient gradient;

		public Colors(String key, Gradient initial) {
			super(key);
			this.initial = initial;
			this.gradient = initial;
		}

		public Gradient get() {
			return gradient;
		}

		public void set(Gradient gradient) {
			this.gradient = gradient;
		}

		/** For example {@code 0.0:#FF0000,1.0:#0000FF}. */
		@Override
		public String write() {
			StringBuilder text = new StringBuilder();
			for (Gradient.Stop stop : gradient.stops()) {
				if (!text.isEmpty()) {
					text.append(',');
				}
				text.append(stop.at()).append(':').append(Gradient.hex(stop.rgb()));
			}
			return text.toString();
		}

		@Override
		public void read(String text) {
			List<Gradient.Stop> stops = new ArrayList<>();
			for (String part : text.split(",")) {
				int colon = part.indexOf(':');
				if (colon < 0) {
					return;
				}
				int rgb = Gradient.parse(part.substring(colon + 1));
				float at;
				try {
					at = Float.parseFloat(part.substring(0, colon).strip());
				} catch (NumberFormatException e) {
					return;
				}
				if (rgb < 0 || Float.isNaN(at)) {
					return;
				}
				stops.add(new Gradient.Stop(at, rgb));
			}
			if (!stops.isEmpty()) {
				gradient = Gradient.of(stops);
			}
		}

		@Override
		public void reset() {
			gradient = initial;
		}
	}

	/** Not a setting but something to do, shown among the settings: opening another screen, say. */
	public static final class Action extends Setting {
		private final Runnable action;

		public Action(String key, Runnable action) {
			super(key);
			this.action = action;
		}

		public void run() {
			action.run();
		}

		@Override
		public @Nullable String write() {
			return null;
		}

		@Override
		public void read(String text) {
		}

		@Override
		public void reset() {
		}
	}
}
