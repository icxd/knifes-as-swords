package io.github.icxd.knives_as_swords;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.icxd.knives_as_swords.fx.CustomEffect;
import io.github.icxd.knives_as_swords.fx.FxStyle;
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
		assertTrue(config.effects);
		assertEquals(1024, config.textureSize);
		assertTrue(config.swords.isEmpty());
		// A sword nobody configured is shown as its default knife, unpainted.
		assertTrue(config.sword("minecraft:iron_sword").enabled);
		assertEquals("", config.sword("minecraft:iron_sword").knife);
		// ...with whatever effects suit it.
		assertEquals("", config.sword("minecraft:iron_sword").effect);
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
		sword.effect = "storm";
		config.effects = false;
		config.swords.put("minecraft:diamond_sword", sword);

		config.save(file);
		ModConfig loaded = ModConfig.load(file);

		assertFalse(loaded.firstPersonAnimations);
		ModConfig.SwordSetting loadedSword = loaded.sword("minecraft:diamond_sword");
		assertEquals("karambit", loadedSword.knife);
		assertEquals("aa_fade", loadedSword.finish);
		assertEquals(661, loadedSword.pattern);
		assertEquals(0.03f, loadedSword.wear);
		assertEquals("storm", loadedSword.effect);
		assertEquals("storm", loadedSword.copy().effect);
		assertFalse(loaded.effects);
	}

	@Test
	void aLookOfThePlayersOwnIsSavedWithItsColours() {
		Path file = temp.resolve("knives_as_swords.json");
		ModConfig config = new ModConfig();
		ModConfig.SwordSetting sword = new ModConfig.SwordSetting();
		sword.effect = FxStyle.CUSTOM;
		sword.customEffect = new CustomEffect();
		sword.customEffect.bolts = 0.25f;
		sword.customEffect.sparkDrift = -0.5f;
		sword.customEffect.colors.add(new CustomEffect.ColorStop(0f, "#FF0000"));
		sword.customEffect.colors.add(new CustomEffect.ColorStop(0.4f, "#00FF00"));
		sword.customEffect.colors.add(new CustomEffect.ColorStop(1f, "#0000FF"));
		config.swords.put("minecraft:iron_sword", sword);

		config.save(file);
		ModConfig.SwordSetting loaded = ModConfig.load(file).sword("minecraft:iron_sword");

		assertEquals(FxStyle.CUSTOM, loaded.effect);
		assertEquals(0.25f, loaded.customEffect.bolts);
		assertEquals(-0.5f, loaded.customEffect.sparkDrift);
		assertEquals(sword.customEffect.toStyle(), loaded.customEffect.toStyle());
		assertEquals(0xFF00FF00, loaded.customEffect.toStyle().color(0f, 0.4f));

		// A copy of the setting has a look of its own to change.
		ModConfig.SwordSetting copy = loaded.copy();
		assertNotSame(loaded.customEffect, copy.customEffect);
		assertEquals(loaded.customEffect.toStyle(), copy.customEffect.toStyle());
		// A sword nobody made a look for has none, and copying that is fine too.
		assertNull(new ModConfig().sword("minecraft:stone_sword").customEffect);
		assertNull(new ModConfig.SwordSetting().copy().customEffect);
	}

	@Test
	void oddValuesInTheFileAreTidiedUp() throws IOException {
		Path file = temp.resolve("knives_as_swords.json");
		Files.writeString(file, """
				{
					"cs2Dir": null,
					"textureSize": 3000,
					"swords": {
						"minecraft:stone_sword": {"knife": null, "finish": " aa_fade ", "pattern": 5000, "wear": -2,
							"effect": null, "customEffect": {"trail": 12, "colors": [{"at": 5, "color": "#abcdef"}, null]}},
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
		assertEquals("", sword.effect);
		assertEquals(1f, sword.customEffect.trail);
		assertEquals(1, sword.customEffect.colors.size());
		assertEquals("#ABCDEF", sword.customEffect.colors.get(0).color);
		assertEquals(1f, sword.customEffect.colors.get(0).at);
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
