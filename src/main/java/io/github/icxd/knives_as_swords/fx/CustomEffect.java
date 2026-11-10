package io.github.icxd.knives_as_swords.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * A look for a knife's effects that the player put together: how much of each effect, and a
 * gradient of their own for the colours. This is how it is written in the config file; the
 * effects themselves work with the {@link FxStyle} made from it.
 *
 * <p>Amounts run from 0 (none) to 1 (as much as the effect has to give).
 */
public final class CustomEffect {
	/** Fastest the colours may cycle, in times per second. */
	public static final float MAX_COLOR_CYCLE = 1f;

	/** Strongest pull on sparks, in metres per second squared: upwards for embers, downwards for drops. */
	private static final float RISE = 3f;
	private static final float FALL = 7f;

	/** The band of light a moving blade leaves behind. */
	public float trail = 0.9f;
	/** Afterimages of the whole knife during fast moves. */
	public float afterimages = 0.8f;
	/** Bolts crawling over the blade. */
	public float bolts = 0.4f;
	/** Points of light shed in motion and thrown off by hits. */
	public float sparks = 0.7f;
	/** The light on and around the knife itself. */
	public float glow = 0.9f;
	/** How white-hot the brightest parts burn. */
	public float heat = 0.6f;
	/** How ragged and flickering shapes are, as opposed to clean. */
	public float raggedness = 0.5f;
	/** What sparks do once in the air: -1 fall like drops, 0 hang and wander, 1 rise like embers. */
	public float sparkDrift = 0f;
	/** How often per second the colours run through the gradient by themselves; 0 for not at all. */
	public float colorCycle = 0f;
	/** The gradient: effects are coloured from its start (fresh) to its end (fading). */
	public List<ColorStop> colors = new ArrayList<>();

	/** One colour of the gradient. */
	public static final class ColorStop {
		/** Where the colour sits in the gradient, 0 to 1. */
		public float at;
		/** The colour, as {@code #RRGGBB}. */
		public String color = "#FFFFFF";

		public ColorStop() {
		}

		public ColorStop(float at, String color) {
			this.at = at;
			this.color = color;
		}
	}

	/** A custom look that starts out as a copy of a ready-made one. */
	public static CustomEffect from(FxStyle style) {
		CustomEffect effect = new CustomEffect();
		effect.trail = style.trail();
		effect.afterimages = style.echo();
		effect.bolts = style.arcs();
		effect.sparks = style.sparks();
		effect.glow = style.glow();
		effect.heat = style.heat();
		effect.raggedness = style.roughness();
		effect.sparkDrift = style.sparkGravity() >= 0f ? style.sparkGravity() / RISE : style.sparkGravity() / FALL;
		effect.colorCycle = style.cycle();
		effect.setGradient(style.colors());
		effect.sanitize();
		return effect;
	}

	public CustomEffect copy() {
		CustomEffect copy = new CustomEffect();
		copy.trail = trail;
		copy.afterimages = afterimages;
		copy.bolts = bolts;
		copy.sparks = sparks;
		copy.glow = glow;
		copy.heat = heat;
		copy.raggedness = raggedness;
		copy.sparkDrift = sparkDrift;
		copy.colorCycle = colorCycle;
		for (ColorStop stop : colors) {
			copy.colors.add(new ColorStop(stop.at, stop.color));
		}
		return copy;
	}

	/** Brings whatever a hand-edited file may hold back into what the effects can work with. */
	public void sanitize() {
		trail = amount(trail);
		afterimages = amount(afterimages);
		bolts = amount(bolts);
		sparks = amount(sparks);
		glow = amount(glow);
		heat = amount(heat);
		raggedness = amount(raggedness);
		sparkDrift = Float.isNaN(sparkDrift) ? 0f : Math.clamp(sparkDrift, -1f, 1f);
		colorCycle = Float.isNaN(colorCycle) ? 0f : Math.clamp(colorCycle, 0f, MAX_COLOR_CYCLE);
		// Through a gradient and back: drops what is not a colour, sorts, and limits how many there are.
		setGradient(gradient());
	}

	private static float amount(float value) {
		return Float.isNaN(value) ? 0f : Math.clamp(value, 0f, 1f);
	}

	/** The colours as a gradient. Entries that are not colours are left out. */
	public Gradient gradient() {
		List<Gradient.Stop> stops = new ArrayList<>();
		if (colors != null) {
			for (ColorStop stop : colors) {
				int rgb = stop != null ? Gradient.parse(stop.color) : -1;
				if (rgb >= 0) {
					stops.add(new Gradient.Stop(stop.at, rgb));
				}
			}
		}
		return Gradient.of(stops);
	}

	public void setGradient(Gradient gradient) {
		List<ColorStop> stops = new ArrayList<>();
		for (Gradient.Stop stop : gradient.stops()) {
			stops.add(new ColorStop(stop.at(), Gradient.hex(stop.rgb())));
		}
		colors = stops;
	}

	/** What the effects work with. */
	public FxStyle toStyle() {
		float drift = Float.isNaN(sparkDrift) ? 0f : Math.clamp(sparkDrift, -1f, 1f);
		float gravity = drift >= 0f ? drift * RISE : drift * FALL;
		// Rising embers and hanging wisps are slowed by the air and pushed about; falling drops hardly.
		float drag = 1f + Math.max(drift, -0.5f);
		float swirl = 0.4f + 2f * (1f - Math.abs(drift)) + Math.max(drift, 0f);
		return new FxStyle(FxStyle.CUSTOM, gradient(), Float.isNaN(colorCycle) ? 0f : Math.clamp(colorCycle, 0f, MAX_COLOR_CYCLE),
				amount(trail), amount(afterimages), amount(bolts), amount(sparks), amount(glow), amount(heat),
				amount(raggedness), gravity, drag, swirl);
	}
}
