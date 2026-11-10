package io.github.icxd.knives_as_swords;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** The client config file, {@code config/knives_as_swords.json}. */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

	/** Highest pattern seed the game hands out. */
	public static final int MAX_PATTERN = 1000;

	/** CS2 install folder (the one containing {@code game/}); empty to find it through Steam. */
	public String cs2Dir = "";
	/** Path to an own copy of Source2Viewer-CLI; empty to use the one the mod downloads. */
	public String extractorPath = "";
	/** Set once the player has agreed in game to downloading the extractor. */
	public boolean extractorDownloadAllowed = false;
	/** Master switch: when off, every sword keeps its vanilla look. */
	public boolean enabled = true;
	/** Play the knife's own first-person animations instead of the vanilla sword swing. */
	public boolean firstPersonAnimations = true;
	/** Draw knives with reflections, so that metal looks like metal; off draws them like any other item. */
	public boolean shine = true;
	/** Longest side of knife textures in pixels; larger source images are scaled down. */
	public int textureSize = 1024;
	/** What each sword looks like, keyed by item id such as {@code minecraft:diamond_sword}. */
	public Map<String, SwordSetting> swords = new LinkedHashMap<>();

	/** How one kind of sword is shown. */
	public static final class SwordSetting {
		/** Off shows the vanilla sword. */
		public boolean enabled = true;
		/** Knife model id such as {@code karambit}; empty for the mod's default for this sword. */
		public String knife = "";
		/** Paint kit name such as {@code aa_fade}; empty for the knife's own, unpainted look. */
		public String finish = "";
		/** Pattern seed, 0 to {@value ModConfig#MAX_PATTERN}. */
		public int pattern = 0;
		/** Wear, 0 (factory new) to 1 (battle-scarred). */
		public float wear = 0f;

		public SwordSetting copy() {
			SwordSetting copy = new SwordSetting();
			copy.enabled = enabled;
			copy.knife = knife;
			copy.finish = finish;
			copy.pattern = pattern;
			copy.wear = wear;
			return copy;
		}

		void sanitize() {
			knife = knife == null ? "" : knife.trim();
			finish = finish == null ? "" : finish.trim();
			pattern = Math.clamp(pattern, 0, MAX_PATTERN);
			wear = Float.isNaN(wear) ? 0f : Math.clamp(wear, 0f, 1f);
		}
	}

	/** The stored setting for a sword, or defaults when the player never changed it. */
	public SwordSetting sword(String itemId) {
		SwordSetting setting = swords.get(itemId);
		return setting != null ? setting : new SwordSetting();
	}

	public static ModConfig load(Path file) {
		if (Files.isRegularFile(file)) {
			try {
				ModConfig config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ModConfig.class);
				if (config != null) {
					config.sanitize();
					return config;
				}
			} catch (IOException | JsonParseException e) {
				KnivesAsSwordsClient.LOGGER.warn("Could not read {}; using defaults", file, e);
			}
		}
		return new ModConfig();
	}

	private void sanitize() {
		cs2Dir = cs2Dir == null ? "" : cs2Dir;
		extractorPath = extractorPath == null ? "" : extractorPath;
		textureSize = Math.clamp(Integer.highestOneBit(Math.max(textureSize, 1)), 128, 4096);

		Map<String, SwordSetting> clean = new LinkedHashMap<>();
		if (swords != null) {
			swords.forEach((item, setting) -> {
				if (item != null && setting != null) {
					setting.sanitize();
					clean.put(item, setting);
				}
			});
		}
		swords = clean;
	}

	public void save(Path file) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException e) {
			KnivesAsSwordsClient.LOGGER.warn("Could not save {}", file, e);
		}
	}
}
