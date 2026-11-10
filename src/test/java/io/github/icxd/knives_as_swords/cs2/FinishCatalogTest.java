package io.github.icxd.knives_as_swords.cs2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FinishCatalogTest {
	/** Laid out like the game's item definitions, including sections that occur more than once. */
	private static final String ITEMS = """
			"items_game"
			{
				"items"
				{
					"500"
					{
						"name"		"weapon_bayonet"
						"item_name"		"#SFUI_WPNHUD_KnifeBayonet"
						"model_player"		"weapons/models/knife/knife_bayonet/weapon_knife_bayonet.vmdl"
					}
					"42"
					{
						"name"		"weapon_knife"
						"item_name"		"#SFUI_WPNHUD_Knife"
						"model_player"		"weapons/models/knife/knife_default_ct/weapon_knife_default_ct.vmdl"
					}
				}
				"paint_kits"
				{
					"0"
					{
						"name"		"default"
						"wear_remap_min"		"0.060000"
						"wear_remap_max"		"0.800000"
					}
					"38"
					{
						"name"		"aa_fade"
						"description_tag"		"#PaintKit_aa_fade_Tag"
						"wear_remap_min"		"0.000000"
						"wear_remap_max"		"0.080000"
					}
					"418"
					{
						"name"		"am_doppler_phase1"
						"description_tag"		"#PaintKit_am_marbleized_Tag"
					}
				}
				"items"
				{
					"507"
					{
						"name"		"weapon_knife_karambit"
						"item_name"		"#SFUI_WPNHUD_KnifeKaram"
						"model_player"		"weapons/models/knife/knife_karambit/weapon_knife_karambit.vmdl"
					}
				}
				"paint_kits"
				{
					"419"
					{
						"name"		"am_doppler_phase2"
						"description_tag"		"#paintkit_AM_MARBLEIZED_tag"
					}
					"1200"
					{
						"name"		"new_thing"
						"description_tag"		"#PaintKit_new_thing_Tag"
						"composite_material_path"		"weapons/paints/community/set_9/new_thing.vcompmat"
					}
				}
			}
			""";

	private static final String TEXTS = """
			"lang"
			{
				"Language"		"English"
				"Tokens"
				{
					"SFUI_WPNHUD_KnifeBayonet"		"Bayonet"
					"SFUI_WPNHUD_KnifeKaram"				"Karambit"
					"SFUI_WPNHUD_Knife"		"Knife"
					"PaintKit_aa_fade_Tag"		"Fade"
					"PaintKit_am_marbleized_Tag"		"Doppler"
					"PaintKit_new_thing_Tag"		"New \\"Thing\\""
					"Unrelated_Long"		"%s"
				}
			}
			""".formatted("x".repeat(20_000));

	@TempDir
	Path temp;

	private static final List<KnifeModel> KNIVES = List.of(
			new KnifeModel("bayonet", "weapons/models/knife/knife_bayonet/weapon_knife_bayonet.vmdl_c"),
			new KnifeModel("default_ct", "weapons/models/knife/knife_default_ct/weapon_knife_default_ct.vmdl_c"),
			new KnifeModel("karambit", "weapons/models/knife/knife_karambit/weapon_knife_karambit.vmdl_c"),
			new KnifeModel("mystery", "weapons/models/knife/knife_mystery/weapon_knife_mystery.vmdl_c"));

	private FinishCatalog load() throws IOException {
		String icons = "panorama/images/econ/default_generated/";
		new TestVpk()
				.content("scripts/items/items_game.txt", ITEMS.getBytes(StandardCharsets.UTF_8))
				.content("resource/csgo_english.txt", TEXTS.getBytes(StandardCharsets.UTF_8))
				.file(icons + "weapon_knife_karambit_aa_fade_light_png.vtex_c")
				.file(icons + "weapon_knife_karambit_aa_fade_heavy_png.vtex_c")
				.file(icons + "weapon_knife_karambit_am_doppler_phase1_light_png.vtex_c")
				.file(icons + "weapon_knife_karambit_am_doppler_phase2_light_png.vtex_c")
				.file(icons + "weapon_knife_karambit_new_thing_light_png.vtex_c")
				// A finish the game shows but no longer has a recipe for.
				.file(icons + "weapon_knife_karambit_gone_light_png.vtex_c")
				.file(icons + "weapon_bayonet_aa_fade_light_png.vtex_c")
				.file(icons + "weapon_ak47_aa_fade_light_png.vtex_c")
				.file("weapons/paints/legacy/aa_fade.vcompmat_c")
				.file("weapons/paints/legacy/am_doppler_phase1.vcompmat_c")
				.file("weapons/paints/legacy/am_doppler_phase2.vcompmat_c")
				.file("weapons/paints/community/set_9/new_thing.vcompmat_c")
				.write(temp.resolve("game/csgo/pak01_dir.vpk"));
		return FinishCatalog.load(new Cs2Install(temp, "1"), KNIVES);
	}

	@Test
	void listsEachKnifesFinishesByDisplayName() throws IOException {
		FinishCatalog catalog = load();

		assertEquals(List.of("am_doppler_phase1", "am_doppler_phase2", "aa_fade", "new_thing"),
				catalog.finishes("karambit").stream().map(FinishCatalog.Finish::name).toList());
		assertEquals(List.of("aa_fade"), catalog.finishes("bayonet").stream().map(FinishCatalog.Finish::name).toList());
		// "weapon_knife_" also starts the other knives' pictures, but none of those are this knife's.
		assertTrue(catalog.finishes("default_ct").isEmpty());
		assertTrue(catalog.finishes("mystery").isEmpty());
		assertTrue(catalog.finishes("no such knife").isEmpty());
	}

	@Test
	void tellsApartFinishesThatShareAName() throws IOException {
		FinishCatalog catalog = load();

		assertEquals("Fade", catalog.finish("karambit", "aa_fade").orElseThrow().displayName());
		assertEquals("Doppler (am_doppler_phase1)",
				catalog.finish("karambit", "am_doppler_phase1").orElseThrow().displayName());
		// Translation tokens are matched whatever their case.
		assertEquals("Doppler (am_doppler_phase2)",
				catalog.finish("karambit", "am_doppler_phase2").orElseThrow().displayName());
		assertEquals("New \"Thing\"", catalog.finish("karambit", "new_thing").orElseThrow().displayName());
	}

	@Test
	void knowsWearLimitsAndWhereTheRecipeIs() throws IOException {
		FinishCatalog catalog = load();

		FinishCatalog.Finish fade = catalog.finish("karambit", "aa_fade").orElseThrow();
		assertEquals(0f, fade.wearMin());
		assertEquals(0.08f, fade.wearMax());
		assertEquals(0.08f, fade.clampWear(0.5f));
		assertEquals("weapons/paints/legacy/aa_fade.vcompmat_c", fade.compositePath());

		// Without limits of its own a finish gets the game's defaults.
		FinishCatalog.Finish doppler = catalog.finish("karambit", "am_doppler_phase1").orElseThrow();
		assertEquals(0.06f, doppler.wearMin());
		assertEquals(0.8f, doppler.wearMax());

		assertEquals("weapons/paints/community/set_9/new_thing.vcompmat_c",
				catalog.finish("karambit", "new_thing").orElseThrow().compositePath());
	}

	@Test
	void namesKnives() throws IOException {
		FinishCatalog catalog = load();

		assertEquals("Karambit", catalog.knifeName("karambit"));
		assertEquals("Knife", catalog.knifeName("default_ct"));
		// Not in the item definitions: a tidied-up id has to do.
		assertEquals("Some New Knife", catalog.knifeName("some_new_knife"));
	}

	@Test
	void aPackageWithoutItemDefinitionsHasNoFinishes() throws IOException {
		new TestVpk().file("something/else.txt").write(temp.resolve("game/csgo/pak01_dir.vpk"));

		FinishCatalog catalog = FinishCatalog.load(new Cs2Install(temp, "1"), KNIVES);

		assertTrue(catalog.finishes("karambit").isEmpty());
		assertEquals("Karambit", catalog.knifeName("karambit"));
	}

	@Test
	void readsTextsWithoutChokingOnOddLines() {
		Map<String, String> texts = FinishCatalog.readTexts(
				"﻿\"lang\"\n{\n\t\"A_Token\"\t\t\"One \\\"two\\\"\"\n\tnot a pair\n\"B\" \"line\\nbreak\" [$WIN32]\n\"unfinished\" \"");

		assertEquals(Map.of("a_token", "One \"two\"", "b", "line break"), texts);
	}
}
