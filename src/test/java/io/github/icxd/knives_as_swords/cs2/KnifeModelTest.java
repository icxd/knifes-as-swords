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
}
