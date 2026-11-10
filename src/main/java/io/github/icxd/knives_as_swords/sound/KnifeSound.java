package io.github.icxd.knives_as_swords.sound;

import java.util.Locale;
import java.util.Optional;

/**
 * The moments of handling a knife that have a sound, and which of the game's sound files belong
 * to each. The game has several takes of every one; a different one is played each time.
 */
public enum KnifeSound {
	/** Taking the knife out. */
	DRAW("knife_draw_med_clean_"),
	/** A quick slash through the air. */
	SWING("knife_swish_light_"),
	/** The heavy stab through the air. */
	SWING_HEAVY("knife_swish_heavy_"),
	/** A slash landing on something alive. */
	HIT("knife_hit_light_flesh_"),
	/** The heavy stab landing. */
	HIT_HEAVY("heavy_hit_flesh_"),
	/** Turning the knife over to look at it. */
	INSPECT("knife_inspect_turn_");

	private final String prefix;

	KnifeSound(String prefix) {
		this.prefix = prefix;
	}

	/**
	 * Which moment a converted sound file is a take of, going by its name: the moment's prefix,
	 * then a number, then {@code .mp3}. Empty for the many sounds no moment uses.
	 */
	public static Optional<KnifeSound> of(String fileName) {
		String name = fileName.toLowerCase(Locale.ROOT);
		if (!name.endsWith(".mp3")) {
			return Optional.empty();
		}
		name = name.substring(0, name.length() - ".mp3".length());
		for (KnifeSound sound : values()) {
			if (name.startsWith(sound.prefix)) {
				String take = name.substring(sound.prefix.length());
				if (!take.isEmpty() && take.chars().allMatch(Character::isDigit)) {
					return Optional.of(sound);
				}
			}
		}
		return Optional.empty();
	}
}
