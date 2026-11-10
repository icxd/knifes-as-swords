package io.github.icxd.knives_as_swords.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.icxd.knives_as_swords.cs2.Cs2Install;
import io.github.icxd.knives_as_swords.cs2.KnifeModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** Drives the exporter and CLI wrapper with a stand-in shell script instead of the real extractor. */
@DisabledOnOs(OS.WINDOWS)
class KnifeExporterTest {
	private static final KnifeModel KNIFE =
			new KnifeModel("test", "weapons/models/knife/knife_test/weapon_knife_test.vmdl_c");

	@TempDir
	Path temp;

	private Source2ViewerCli fakeCli(String script) throws IOException {
		Path executable = temp.resolve("fake-cli/Source2Viewer-CLI");
		Files.createDirectories(executable.getParent());
		Files.writeString(executable, "#!/bin/sh\n" + script);
		assertTrue(executable.toFile().setExecutable(true));
		return new Source2ViewerCli(executable);
	}

	/** Records its arguments, then writes a model and a physics file where a real export would. */
	private static final String EXPORTING_SCRIPT = """
			if [ "$1" = "--version" ]; then echo "Version: 1.2.3+abc"; exit 0; fi
			echo "$@" > "$(dirname "$0")/arguments.txt"
			echo run >> "$(dirname "$0")/runs.txt"
			while [ $# -gt 0 ]; do
				if [ "$1" = "--output" ]; then out="$2"; fi
				shift
			done
			mkdir -p "$out/weapons/models/knife/knife_test"
			echo model > "$out/weapons/models/knife/knife_test/weapon_knife_test.glb"
			echo physics > "$out/weapons/models/knife/knife_test/weapon_knife_test_physics.glb"
			echo texture > "$out/weapons/models/knife/knife_test/weapon_knife_test_color.png"
			""";

	@Test
	void readsTheVersion() throws Exception {
		assertEquals("1.2.3+abc", fakeCli(EXPORTING_SCRIPT).version());
	}

	@Test
	void versionFailsForSomethingThatIsNotTheCli() throws Exception {
		Source2ViewerCli cli = fakeCli("echo hello\n");

		assertThrows(IOException.class, cli::version);
	}

	@Test
	void runTimesOut() throws Exception {
		Source2ViewerCli cli = fakeCli("sleep 30\n");

		assertThrows(IOException.class, () -> cli.run(temp, Duration.ofMillis(300)));
	}

	@Test
	void exportsIntoTheBuildCacheAndReusesIt() throws Exception {
		Source2ViewerCli cli = fakeCli(EXPORTING_SCRIPT);
		Cs2Install install = new Cs2Install(temp.resolve("game root"), "1234");
		KnifeExporter exporter = new KnifeExporter(temp.resolve("cache"));

		assertTrue(exporter.cached(install, KNIFE).isEmpty());
		Path model = exporter.export(cli, install, KNIFE);

		Path folder = temp.resolve("cache/1234/models/test");
		assertEquals(folder.resolve("weapons/models/knife/knife_test/weapon_knife_test.glb"), model);
		assertEquals(model, exporter.cached(install, KNIFE).orElseThrow());

		String arguments = Files.readString(temp.resolve("fake-cli/arguments.txt"));
		assertTrue(arguments.contains("--input " + install.mainPackage()), arguments);
		assertTrue(arguments.contains("--vpk_filepath " + KNIFE.modelPath()), arguments);
		assertTrue(arguments.contains("--output " + folder), arguments);
		assertTrue(arguments.contains("--vpk_decompile"), arguments);
		assertTrue(arguments.contains("--gltf_export_format glb"), arguments);

		// A second export is served from the cache without running the tool again.
		assertEquals(model, exporter.export(cli, install, KNIFE));
		assertEquals(1, Files.readAllLines(temp.resolve("fake-cli/runs.txt")).size());
	}

	@Test
	void aFailedExportIsNotCached() throws Exception {
		Source2ViewerCli cli = fakeCli("echo 'something went wrong'; exit 2\n");
		Cs2Install install = new Cs2Install(temp.resolve("game root"), "1234");
		KnifeExporter exporter = new KnifeExporter(temp.resolve("cache"));

		IOException error = assertThrows(IOException.class, () -> exporter.export(cli, install, KNIFE));

		assertTrue(error.getMessage().contains("something went wrong"), error.getMessage());
		assertTrue(exporter.cached(install, KNIFE).isEmpty());
	}

	@Test
	void anExportWithoutAModelFails() throws Exception {
		Source2ViewerCli cli = fakeCli("exit 0\n");
		KnifeExporter exporter = new KnifeExporter(temp.resolve("cache"));

		assertThrows(IOException.class,
				() -> exporter.export(cli, new Cs2Install(temp.resolve("game root"), "1234"), KNIFE));
	}

	@Test
	void prunesCachesOfOtherGameVersions() throws Exception {
		KnifeExporter exporter = new KnifeExporter(temp.resolve("cache"));
		Files.createDirectories(temp.resolve("cache/old/models/test"));
		Files.writeString(temp.resolve("cache/old/models/test/file.glb"), "old");
		Files.createDirectories(temp.resolve("cache/1234/models"));

		exporter.pruneOtherBuilds(new Cs2Install(temp.resolve("game root"), "1234"));

		assertFalse(Files.exists(temp.resolve("cache/old")));
		assertTrue(Files.isDirectory(temp.resolve("cache/1234/models")));
	}
}
