package io.github.icxd.knives_as_swords.cs2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Cs2InstallTest {
	@TempDir
	Path temp;

	private void manifest(Path library, String installDir, String buildId) throws IOException {
		Files.createDirectories(library.resolve("steamapps"));
		Files.writeString(library.resolve("steamapps/appmanifest_730.acf"),
				"\"AppState\" { \"installdir\" \"" + installDir + "\" \"buildid\" \"" + buildId + "\" }");
	}

	private static void mainPackage(Path root) throws IOException {
		new TestVpk().file("a/b.c").write(root.resolve("game/csgo/pak01_dir.vpk"));
	}

	@Test
	void ignoresManifestWithoutInstalledFiles() throws IOException {
		manifest(temp, "Game", "1");

		assertTrue(Cs2Install.inLibrary(temp).isEmpty());
	}

	@Test
	void rejectsInstallDirEscapingTheLibrary() throws IOException {
		manifest(temp, "../../outside", "1");
		mainPackage(temp.resolve("outside"));

		assertTrue(Cs2Install.inLibrary(temp).isEmpty());
	}

	@Test
	void explicitFolderUsesTheManifestBuildIdWhenThereIsOne() throws IOException {
		Path root = temp.resolve("steamapps/common/Game");
		manifest(temp, "Game", "4242");
		mainPackage(root);

		Optional<Cs2Install> install = Cs2Install.atFolder(root);

		assertEquals("4242", install.orElseThrow().buildId());
		assertEquals(root, install.get().root());
	}

	@Test
	void explicitFolderWithoutManifestGetsAFingerprint() throws IOException {
		Path root = temp.resolve("copied/Game");
		mainPackage(root);

		Cs2Install install = Cs2Install.atFolder(root).orElseThrow();

		assertTrue(install.buildId().startsWith("pak-"), install.buildId());
		assertEquals(install.buildId(), Cs2Install.atFolder(root).orElseThrow().buildId());
	}

	@Test
	void explicitFolderMustContainTheMainPackage() throws IOException {
		Files.createDirectories(temp.resolve("empty"));

		assertTrue(Cs2Install.atFolder(temp.resolve("empty")).isEmpty());
	}
}
