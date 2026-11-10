package io.github.icxd.knives_as_swords.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.icxd.knives_as_swords.cs2.Cs2Install;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Reading what the extractor prints, with made-up output in its format. */
class ExtractorOutputTest {
	private static final String TWO_FILES = """
			--- Files in package:
			[1/2] paints/first.vmat_c
				Resource Type: Material [Version 1] [Header Version: 12]
			--- Resource Blocks: Count 4 ---
				-- Block: DATA  Size: 1577   bytes [Offset:   2960]
			--- Data for block "DATA" ---
			<!-- kv3 encoding:text -->
			{
				m_materialName = "paints/first.vmat"
				m_value = [ 1.0, 2.0 ]
			}

			[2/2] paints\\second.vcompmat_c
			--- Data for block "DATA" ---
			{
				m_nVersion = 1
			}
			""";

	@TempDir
	Path temp;

	@Test
	void splitsTheOutputForSeveralFiles() {
		Map<String, String> blocks = ResourceDumper.splitDataBlocks(TWO_FILES);

		assertEquals(List.of("paints/first.vmat_c", "paints/second.vcompmat_c"), List.copyOf(blocks.keySet()));
		assertTrue(blocks.get("paints/first.vmat_c").startsWith("<!-- kv3"), blocks.get("paints/first.vmat_c"));
		assertTrue(blocks.get("paints/first.vmat_c").endsWith("}"), blocks.get("paints/first.vmat_c"));
		assertEquals("{\n\tm_nVersion = 1\n}", blocks.get("paints/second.vcompmat_c"));
	}

	@Test
	void outputWithoutDataBlocksGivesNothing() {
		assertTrue(ResourceDumper.splitDataBlocks("").isEmpty());
		assertTrue(ResourceDumper.splitDataBlocks("[1/1] some/file.vmat_c\nnothing useful\n").isEmpty());
	}

	@Test
	void findsTheBoneNamesInAModelDump() {
		String dump = """
				m_modelSkeleton =\s
				{
					m_boneName =\s
					[
						"weapon",
						"weapon_offset",
						"blade",
					]
					m_nParent = [ -1, 0, 1 ]
				}
				""";

		assertEquals(List.of("weapon", "weapon_offset", "blade"), KnifeExporter.parseBoneNames(dump));
		assertTrue(KnifeExporter.parseBoneNames("no bones here").isEmpty());
	}

	@Test
	void servesCachedResourcesWithoutRunningAnything() throws Exception {
		// The executable does not exist: anything not already in the cache would fail.
		Source2ViewerCli cli = new Source2ViewerCli(temp.resolve("no-such-tool"));
		Cs2Install install = new Cs2Install(temp.resolve("game"), "77");
		ResourceDumper dumper = new ResourceDumper(temp.resolve("cache"));
		Path data = temp.resolve("cache/77/data/paints/first.vmat_c.kv3");
		Path texture = temp.resolve("cache/77/textures/paints/pattern_abc.png");
		Files.createDirectories(data.getParent());
		Files.writeString(data, "{ a = 1 }");
		Files.createDirectories(texture.getParent());
		Files.writeString(texture, "image");

		assertEquals(Map.of("paints/first.vmat_c", "{ a = 1 }"),
				dumper.dataBlocks(cli, install, List.of("paints/first.vmat_c")));
		assertEquals(Map.of("paints/pattern_abc.vtex_c", texture),
				dumper.textures(cli, install, List.of("paints/pattern_abc.vtex_c")));
	}

	@Test
	void refusesPathsThatCouldLeaveTheCache() {
		Source2ViewerCli cli = new Source2ViewerCli(temp.resolve("no-such-tool"));
		Cs2Install install = new Cs2Install(temp.resolve("game"), "77");
		ResourceDumper dumper = new ResourceDumper(temp.resolve("cache"));

		for (String path : new String[] {"../outside.vmat_c", "/absolute.vmat_c", "a/../../b.vmat_c", "C:\\x.vmat_c",
				"with space.vmat_c"}) {
			assertThrows(IOException.class, () -> dumper.dataBlocks(cli, install, List.of(path)), path);
		}
		assertThrows(IOException.class, () -> dumper.textures(cli, install, List.of("not/a/texture.png")));
	}
}
