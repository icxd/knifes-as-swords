package io.github.icxd.knives_as_swords.render;

import io.github.icxd.knives_as_swords.KnifeRuntime.State;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/** Which knife a sword shows as long as the player has not picked one. */
public final class SwordDefaults {
	/** Plainer knives for the cheaper swords, fancier ones for the better. */
	private static final Map<String, String> BY_SWORD = Map.of(
			"minecraft:wooden_sword", "default_t",
			"minecraft:stone_sword", "default_ct",
			"minecraft:copper_sword", "gut",
			"minecraft:iron_sword", "bayonet",
			"minecraft:golden_sword", "karambit",
			"minecraft:diamond_sword", "butterfly",
			"minecraft:netherite_sword", "m9");
	/** Tried in order for swords not listed above, and when a listed knife is not installed. */
	private static final List<String> FALLBACKS = List.of("bayonet", "default_ct", "default_t");

	private SwordDefaults() {
	}

	/** A knife from {@code state} for the given sword. {@code state} must hold at least one knife. */
	public static String knifeFor(Identifier sword, State state) {
		String preferred = BY_SWORD.get(sword.toString());
		if (preferred != null && state.knife(preferred).isPresent()) {
			return preferred;
		}
		for (String fallback : FALLBACKS) {
			if (state.knife(fallback).isPresent()) {
				return fallback;
			}
		}
		return state.knives().getFirst().id();
	}
}
