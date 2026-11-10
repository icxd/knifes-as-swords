package io.github.icxd.knives_as_swords.cs2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnifeModelTest {
	@TempDir
	Path temp;

	@Test
	void listsOnlyKnifeModelsSortedById() throws IOException {
		new TestVpk()
				.file("weapons/models/knife/knife_karambit/weapon_knife_karambit.vmdl_c")
				.file("weapons/models/knife/knife_karambit/materials/weapon_knife_karambit.vmat_c")
				.file("weapons/models/knife/knife_karambit/materials/composite_inputs/some_texture.vtex_c")
				.file("weapons/models/knife/knife_bayonet/weapon_knife_bayonet.vmdl_c")
				.file("weapons/models/knife/knife_default_ct/weapon_knife_default_ct.vmdl_c")
				// Not knives: nested deeper, outside the knife folder, or directly in it.
				.file("weapons/models/knife/knife_karambit/parts/extra.vmdl_c")
				.file("weapons/models/shared/stattrak/stattrak_module_knife.vmdl_c")
				.file("weapons/models/knife/loose.vmdl_c")
				.file("models/props/dishes_knife_1.vmdl_c")
				.write(temp.resolve("game/csgo/pak01_dir.vpk"));

		List<KnifeModel> knives = KnifeModel.list(new Cs2Install(temp, "1"));

		assertEquals(List.of(
				new KnifeModel("bayonet", "weapons/models/knife/knife_bayonet/weapon_knife_bayonet.vmdl_c"),
				new KnifeModel("default_ct", "weapons/models/knife/knife_default_ct/weapon_knife_default_ct.vmdl_c"),
				new KnifeModel("karambit", "weapons/models/knife/knife_karambit/weapon_knife_karambit.vmdl_c")),
				knives);
	}

	@Test
	void findsEachKnifesAnimations() throws IOException {
		String clips = "animation/anims/viewmodel/knife/";
		new TestVpk()
				.file("weapons/models/knife/knife_karambit/weapon_knife_karambit.vmdl_c")
				.file("weapons/models/knife/knife_default_ct/weapon_knife_default_ct.vmdl_c")
				.file(clips + "knife_karambit/draw_karambit.vnmclip_c")
				.file(clips + "knife_karambit/idle1_karambit.vnmclip_c")
				// Knives without a folder of their own use the shared one.
				.file(clips + "_default_knife/draw_knife.vnmclip_c")
				// Not clips of any knife: third-person ones, and something else in a knife's folder.
				.file("animation/anims/world/knife/knife_default_ct/idle_default_ct.vnmclip_c")
				.file(clips + "knife_default_ct/notes.txt")
				.write(temp.resolve("game/csgo/pak01_dir.vpk"));

		List<KnifeModel> knives = KnifeModel.list(new Cs2Install(temp, "1"));

		KnifeModel shared = knives.get(0);
		assertEquals("default_ct", shared.id());
		assertEquals(clips + "_default_knife/", shared.animationFolder());
		assertEquals("_knife", shared.clipSuffix());
		KnifeModel own = knives.get(1);
		assertEquals(clips + "knife_karambit/", own.animationFolder());
		assertEquals("_karambit", own.clipSuffix());
	}

	@Test
	void aKnifeWithoutAnyClipsHasNoAnimations() throws IOException {
		new TestVpk()
				.file("weapons/models/knife/knife_karambit/weapon_knife_karambit.vmdl_c")
				.write(temp.resolve("game/csgo/pak01_dir.vpk"));

		KnifeModel knife = KnifeModel.list(new Cs2Install(temp, "1")).get(0);

		assertEquals("", knife.animationFolder());
		assertEquals(false, knife.hasAnimations());
	}
}
