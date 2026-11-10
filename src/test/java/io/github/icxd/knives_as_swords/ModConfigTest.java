package io.github.icxd.knives_as_swords;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModConfigTest {
	@TempDir
	Path temp;

	@Test
	void aMissingFileMeansDefaults() {
		ModConfig config = ModConfig.load(temp.resolve("none.json"));

		assertTrue(config.enabled);
		assertTrue(config.firstPersonAnimations);
		assertTrue(config.shine);
		assertEquals(1024, config.textureSize);
		assertTrue(config.swords.isEmpty());
		// A sword nobody configured is shown as its default knife, unpainted.
		assertTrue(config.sword("minecraft:iron_sword").enabled);
		assertEquals("", config.sword("minecraft:iron_sword").knife);
	}

	@Test
	void whatIsSavedComesBack() {
		Path file = temp.resolve("config/knives_as_swords.json");
		ModConfig config = new ModConfig();
		config.firstPersonAnimations = false;
		ModConfig.SwordSetting sword = new ModConfig.SwordSetting();
		sword.knife = "karambit";
		sword.finish = "aa_fade";
		sword.pattern = 661;
		sword.wear = 0.03f;
		config.swords.put("minecraft:diamond_sword", sword);

		config.save(file);
		ModConfig loaded = ModConfig.load(file);

		assertFalse(loaded.firstPersonAnimations);
		ModConfig.SwordSetting loadedSword = loaded.sword("minecraft:diamond_sword");
		assertEquals("karambit", loadedSword.knife);
		assertEquals("aa_fade", loadedSword.finish);
		assertEquals(661, loadedSword.pattern);
		assertEquals(0.03f, loadedSword.wear);
	}

	@Test
	void oddValuesInTheFileAreTidiedUp() throws IOException {
		Path file = temp.resolve("knives_as_swords.json");
		Files.writeString(file, """
				{
					"cs2Dir": null,
					"textureSize": 3000,
					"swords": {
						"minecraft:stone_sword": {"knife": null, "finish": " aa_fade ", "pattern": 5000, "wear": -2},
						"minecraft:iron_sword": null
					}
				}
				""");

		ModConfig config = ModConfig.load(file);

		assertEquals("", config.cs2Dir);
		// Rounded down to a power of two, which textures have to be.
		assertEquals(2048, config.textureSize);
		ModConfig.SwordSetting sword = config.sword("minecraft:stone_sword");
		assertEquals("", sword.knife);
		assertEquals("aa_fade", sword.finish);
		assertEquals(ModConfig.MAX_PATTERN, sword.pattern);
		assertEquals(0f, sword.wear);
		assertFalse(config.swords.containsKey("minecraft:iron_sword"));
	}

	@Test
	void aBrokenFileMeansDefaults() throws IOException {
		Path file = temp.resolve("knives_as_swords.json");
		Files.writeString(file, "{ this is not json");

		assertTrue(ModConfig.load(file).swords.isEmpty());
	}

	@Test
	void aCopyIsIndependent() {
		ModConfig.SwordSetting original = new ModConfig.SwordSetting();
		original.knife = "m9";

		ModConfig.SwordSetting copy = original.copy();
		copy.knife = "bayonet";

		assertEquals("m9", original.knife);
	}
}
