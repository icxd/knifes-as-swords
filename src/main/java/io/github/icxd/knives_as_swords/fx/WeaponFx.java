package io.github.icxd.knives_as_swords.fx;

import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.ModConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * The way into the effects: which look a sword's knife has, if any.
 *
 * <p>Effects are drawing only, like everything else in this mod. Client thread only.
 */
public final class WeaponFx {
	/** The look a sword has as long as the player has not picked one: plain for the cheap, wild for the precious. */
	private static final Map<String, String> BY_SWORD = Map.of(
			"minecraft:copper_sword", "inferno",
			"minecraft:iron_sword", "storm",
			"minecraft:golden_sword", "solar",
			"minecraft:diamond_sword", "phantom",
			"minecraft:netherite_sword", "void");

	private static final Map<Item, Optional<FxStyle>> CHOICES = new HashMap<>();
	private static int seenConfigVersion = -1;

	private WeaponFx() {
	}

	/** Has to be called while the mod initializes; see {@link FxPipelines#init()}. */
	public static void init() {
		FxPipelines.init();
		WorldFx.init();
	}

	/** The look of this sword's effects with the current settings, or {@code null} for none. */
	public static @Nullable FxStyle styleFor(Item item) {
		KnifeRuntime runtime = KnivesAsSwordsClient.runtime();
		if (runtime == null) {
			return null;
		}
		int configVersion = runtime.configVersion();
		if (configVersion != seenConfigVersion) {
			seenConfigVersion = configVersion;
			CHOICES.clear();
		}
		return CHOICES.computeIfAbsent(item, key -> choose(runtime.config(), key)).orElse(null);
	}

	private static Optional<FxStyle> choose(ModConfig config, Item item) {
		if (!config.effects) {
			return Optional.empty();
		}
		String sword = BuiltInRegistries.ITEM.getKey(item).toString();
		ModConfig.SwordSetting setting = config.sword(sword);
		String effect = effectId(sword, setting.effect);
		if (effect.equals(FxStyle.CUSTOM)) {
			return Optional.ofNullable(setting.customEffect).map(CustomEffect::toStyle);
		}
		return FxStyle.byId(effect);
	}

	/**
	 * What a sword's setting comes down to.
	 *
	 * @param sword the sword's item id, e.g. {@code minecraft:diamond_sword}
	 * @param setting what the config says: empty for "whatever suits this sword"
	 * @return the name of a ready-made look, {@link FxStyle#CUSTOM} or {@link FxStyle#NONE}
	 */
	public static String effectId(String sword, String setting) {
		if (setting.isEmpty()) {
			return BY_SWORD.getOrDefault(sword, FxStyle.NONE);
		}
		return setting;
	}
}
