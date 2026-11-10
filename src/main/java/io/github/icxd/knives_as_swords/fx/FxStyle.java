package io.github.icxd.knives_as_swords.fx;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One look for a knife's effects: its colours, and how much of each effect it has.
 *
 * <p>The amounts run from 0 (none) to 1 (as much as the effect has to give).
 *
 * @param id the name the look is saved under
 * @param colors the look's colours, from fresh (the head of a trail, bolts, the glow) to fading
 *     (the tail of a trail, older afterimages); sparks take theirs from anywhere in between
 * @param cycle how fast the colours run through {@code colors} by themselves, in times per
 *     second; 0 keeps every part of an effect at its own colour
 * @param trail the band of light a moving blade leaves behind
 * @param echo afterimages of the whole knife during fast moves
 * @param arcs bolts crawling over the blade
 * @param sparks points of light shed in motion and thrown off by hits
 * @param glow the light on and around the knife itself
 * @param heat how white-hot the brightest parts burn
 * @param roughness how ragged and flickering shapes are, as opposed to clean
 * @param sparkGravity metres per second squared along "up": negative lets sparks fall, positive lets them rise
 * @param sparkDrag how quickly sparks lose their speed
 * @param sparkSwirl how strongly sparks wander sideways
 */
public record FxStyle(String id, Gradient colors, float cycle, float trail, float echo, float arcs, float sparks,
		float glow, float heat, float roughness, float sparkGravity, float sparkDrag, float sparkSwirl) {
	/** In the config: no effects for this sword. */
	public static final String NONE = "none";
	/** In the config: the look the player put together for this sword; see {@link CustomEffect}. */
	public static final String CUSTOM = "custom";
	private static final Map<String, FxStyle> STYLES = new LinkedHashMap<>();

	static {
		// A ghostly blade: afterimages and slow, rising wisps.
		add(new FxStyle("phantom", Gradient.evenly(0x4DF2FF, 0x8C66FF), 0f,
				0.9f, 1.0f, 0.25f, 0.55f, 0.9f, 0.6f, 0.5f, 0.5f, 2.5f, 1.4f));
		// Crackling with electricity: many bolts, hard white sparks that fall.
		add(new FxStyle("storm", Gradient.evenly(0x66ADFF, 0xD9F2FF), 0f,
				0.7f, 0.35f, 1.0f, 0.9f, 0.7f, 1.0f, 0.35f, -6.0f, 0.8f, 0.4f));
		// A blade fresh from the forge: a fiery, ragged trail and embers drifting upwards.
		add(new FxStyle("inferno", Gradient.evenly(0xFF7314, 0xFF1F05), 0f,
				1.0f, 0.5f, 0.0f, 1.0f, 0.8f, 0.8f, 0.9f, 1.6f, 1.8f, 2.2f));
		// Dark magic: violet into pink, heavy afterimages.
		add(new FxStyle("void", Gradient.evenly(0xB838FF, 0xFF33A6), 0f,
				1.0f, 1.0f, 0.5f, 0.6f, 1.0f, 0.45f, 0.7f, 0.3f, 2.0f, 2.6f));
		// Dripping with poison: acid green, drops that fall.
		add(new FxStyle("venom", Gradient.evenly(0x73FF33, 0xE6FF40), 0f,
				0.8f, 0.5f, 0.15f, 0.8f, 0.8f, 0.5f, 0.8f, -4.0f, 1.2f, 0.6f));
		// Sunlight on gold: clean, bright and warm.
		add(new FxStyle("solar", Gradient.evenly(0xFFD14D, 0xFF8C26), 0f,
				0.9f, 0.4f, 0.2f, 0.8f, 1.0f, 1.0f, 0.3f, -2.0f, 1.5f, 1.0f));
		// Everything at once, in every colour.
		add(new FxStyle("prism", Gradient.evenly(0xFF0000, 0xFFFF00, 0x00FF00, 0x00FFFF, 0x0000FF, 0xFF00FF), 0.35f,
				1.0f, 1.0f, 0.7f, 1.0f, 1.0f, 0.7f, 0.5f, -1.5f, 1.2f, 1.8f));
	}

	private static void add(FxStyle style) {
		STYLES.put(style.id, style);
	}

	/** Every ready-made look there is, in the order they are offered. */
	public static List<FxStyle> all() {
		return List.copyOf(STYLES.values());
	}

	/** A ready-made look by its name. {@link #NONE} and {@link #CUSTOM} are not among them. */
	public static Optional<FxStyle> byId(String id) {
		return Optional.ofNullable(STYLES.get(id));
	}

	/**
	 * The look's colour for something at a moment in time.
	 *
	 * @param time seconds; only matters for looks whose colours cycle
	 * @param t where the thing is between fresh (0) and fading (1)
	 */
	public int color(float time, float t) {
		return colors.at(t, time, cycle);
	}
}
