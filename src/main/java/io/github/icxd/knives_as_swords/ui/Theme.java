package io.github.icxd.knives_as_swords.ui;

import io.github.icxd.knives_as_swords.fx.Gradient;
import io.github.icxd.knives_as_swords.visuals.Visual;

/**
 * The look the mod's menus share: dark glass, and light in the colours of the effects.
 */
public final class Theme {
	/** The glass panels and buttons are made of: nearly black, letting a little of the world through. */
	public static final int GLASS = 0xE40A0A12;
	/** A lighter glass, for what lies in a panel: rows of settings, lists. */
	public static final int INSET = 0xC014141F;
	/** A thinner glass for the heads-up display, where the world has to stay in view behind it. */
	public static final int PLATE = 0xA408080E;
	/** What a slider's, a switch's or a bar's track is made of where there is no light in it. */
	public static final int TRACK = 0xFF272733;
	public static final int TEXT = 0xFFEDEDF5;
	public static final int DIM = 0xFF8C8CA2;
	public static final int FAINT = 0xFF55556A;
	public static final int WARNING = 0xFFFFC857;

	/** The colours of whatever has no colours of its own: violet into cyan into mint, like the menu's title. */
	public static final Gradient ACCENT = Gradient.evenly(0x9B5CFF, 0x3FC8FF, 0x5CFFB0);

	private static final Gradient COMBAT = Gradient.evenly(0xFF6A3D, 0xFF2D7A);
	private static final Gradient PLAYER = Gradient.evenly(0x4DF2FF, 0x8C66FF);
	private static final Gradient WORLD = Gradient.evenly(0x5CFFB0, 0x3FC8FF);
	private static final Gradient HUD = Gradient.evenly(0xFFD14D, 0xFF8C26);

	private static final long STARTED = System.nanoTime();

	private Theme() {
	}

	/** The clock the menus' lights go by: seconds, running on whether or not the game is paused. */
	public static float seconds() {
		// Starting over every few hours keeps fractions of a second exact.
		return (float) ((System.nanoTime() - STARTED) % 16_384_000_000_000L / 1e9);
	}

	/** Each category of visuals has colours of its own, as each look of the effects has. */
	public static Gradient colors(Visual.Category category) {
		return switch (category) {
			case COMBAT -> COMBAT;
			case PLAYER -> PLAYER;
			case WORLD -> WORLD;
			case HUD -> HUD;
		};
	}

	/**
	 * A gradient's colour for this moment: the menus' lights wander slowly back and forth through
	 * their colours.
	 *
	 * @param time seconds
	 * @param offset moves this light along the gradient, so that neighbours differ
	 * @return the colour, fully opaque
	 */
	public static int now(Gradient colors, float time, float offset) {
		return colors.backAndForth(time * 0.12f + offset);
	}

	/** A gradient's colour for this moment, by the menus' own clock. */
	public static int now(Gradient colors, float offset) {
		return now(colors, seconds(), offset);
	}
}
