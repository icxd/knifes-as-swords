package io.github.icxd.knives_as_swords.render;

/**
 * Everything that decides what a knife looks like; the key for cached models and textures.
 *
 * @param knife knife model id, e.g. {@code karambit}
 * @param finish paint kit name, or empty for the unpainted knife
 * @param pattern pattern seed
 * @param wear wear in thousandths, 0 to 1000; whole numbers so equal looks share one texture
 */
public record Appearance(String knife, String finish, int pattern, int wear) {
	public static Appearance of(String knife, String finish, int pattern, float wear) {
		boolean painted = !finish.isEmpty();
		// An unpainted knife has neither a pattern nor wear.
		return new Appearance(knife, finish, painted ? pattern : 0, painted ? Math.round(wear * 1000f) : 0);
	}

	public float wearFraction() {
		return wear / 1000f;
	}
}
