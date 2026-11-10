package io.github.icxd.knives_as_swords;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** The client config file, {@code config/knives_as_swords.json}. */
public final class ModConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

	/** CS2 install folder (the one containing {@code game/}); empty to find it through Steam. */
	public String cs2Dir = "";
	/** Path to an own copy of Source2Viewer-CLI; empty to use the one the mod downloads. */
	public String extractorPath = "";
	/** Set once the player has agreed in game to downloading the extractor. */
	public boolean extractorDownloadAllowed = false;

	public static ModConfig load(Path file) {
		if (Files.isRegularFile(file)) {
			try {
				ModConfig config = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), ModConfig.class);
				if (config != null) {
					config.cs2Dir = config.cs2Dir == null ? "" : config.cs2Dir;
					config.extractorPath = config.extractorPath == null ? "" : config.extractorPath;
					return config;
				}
			} catch (IOException | JsonParseException e) {
				KnivesAsSwordsClient.LOGGER.warn("Could not read {}; using defaults", file, e);
			}
		}
		return new ModConfig();
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
