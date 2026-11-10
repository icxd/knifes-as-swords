package io.github.icxd.knives_as_swords.cs2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SteamLocatorTest {
	@TempDir
	Path temp;

	/** Escapes a path the way Steam writes it into its VDF files. */
	private static String vdf(Path path) {
		return path.toString().replace("\\", "\\\\");
	}

	private static void installCs2(Path library, String buildId) throws IOException {
		Path apps = library.resolve("steamapps");
		Files.createDirectories(apps);
		Files.writeString(apps.resolve("appmanifest_730.acf"), """
				"AppState"
				{
					"appid"		"730"
					"name"		"Some Game"
					"installdir"		"Some Game Folder"
					"buildid"		"%s"
				}
				""".formatted(buildId));
		new TestVpk().file("weapons/models/knife/knife_test/weapon_knife_test.vmdl_c")
				.write(apps.resolve("common/Some Game Folder/game/csgo/pak01_dir.vpk"));
	}

	@Test
	void findsCs2InASecondaryLibraryOnLinux() throws IOException {
		Path home = temp.resolve("home");
		Path steam = home.resolve(".local/share/Steam");
		Path secondLibrary = temp.resolve("games/SteamLibrary");
		Files.createDirectories(steam.resolve("steamapps"));
		Files.writeString(steam.resolve("steamapps/libraryfolders.vdf"), """
				"libraryfolders"
				{
					"0"
					{
						"path"		"%s"
						"apps" { "228980" "1" }
					}
					"1"
					{
						"path"		"%s"
						"apps" { "730" "1" }
					}
					"2"
					{
						"path"		"%s"
					}
				}
				""".formatted(vdf(steam), vdf(secondLibrary), vdf(temp.resolve("unplugged-drive"))));
		installCs2(secondLibrary, "20261101");

		SteamLocator locator = new SteamLocator("Linux", home, Map.<String, String>of()::get, key -> Optional.empty());
		Optional<Cs2Install> install = locator.findCs2();

		assertTrue(install.isPresent());
		assertEquals("20261101", install.get().buildId());
		assertEquals(secondLibrary.toRealPath().resolve("steamapps/common/Some Game Folder"), install.get().root());
		assertTrue(Files.isRegularFile(install.get().mainPackage()));
		assertEquals(List.of(steam.toRealPath(), secondLibrary.toRealPath()), locator.libraryFolders());
	}

	@Test
	void usesTheRegistryPathOnWindows() throws IOException {
		Path steam = temp.resolve("Steam");
		installCs2(steam, "777");

		SteamLocator locator = new SteamLocator("Windows 11", temp.resolve("home"), Map.<String, String>of()::get,
				key -> key[0].startsWith("HKCU") && key[1].equals("SteamPath")
						? Optional.of(steam.toString()) : Optional.empty());

		assertEquals("777", locator.findCs2().orElseThrow().buildId());
	}

	@Test
	void fallsBackToProgramFilesOnWindows() throws IOException {
		Path programFiles = temp.resolve("Program Files (x86)");
		installCs2(programFiles.resolve("Steam"), "888");

		SteamLocator locator = new SteamLocator("Windows 10", temp.resolve("home"),
				Map.of("ProgramFiles(x86)", programFiles.toString())::get, key -> Optional.empty());

		assertEquals("888", locator.findCs2().orElseThrow().buildId());
	}

	@Test
	void findsNothingWithoutSteam() {
		SteamLocator locator = new SteamLocator("Linux", temp.resolve("empty-home"), Map.<String, String>of()::get,
				key -> Optional.empty());

		assertTrue(locator.findCs2().isEmpty());
		assertTrue(locator.libraryFolders().isEmpty());
	}

	@Test
	void findsNothingWhenSteamHasNoCs2() throws IOException {
		Path home = temp.resolve("home");
		Files.createDirectories(home.resolve(".local/share/Steam/steamapps"));

		SteamLocator locator = new SteamLocator("Linux", home, Map.<String, String>of()::get, key -> Optional.empty());

		assertTrue(locator.findCs2().isEmpty());
	}

	@Test
	void parsesRegQueryOutput() {
		String output = "\r\nHKEY_CURRENT_USER\\Software\\Valve\\Steam\r\n"
				+ "    SteamPath    REG_SZ    c:/program files (x86)/steam\r\n\r\n";

		assertEquals(Optional.of("c:/program files (x86)/steam"), SteamLocator.parseRegistryValue(output));
		assertEquals(Optional.empty(), SteamLocator.parseRegistryValue("ERROR: The system was unable to find the specified registry key or value.\r\n"));
	}

	@Test
	void parsesBothLibraryFolderLayouts() {
		List<Path> current = SteamLocator.parseLibraryFolders("""
				"libraryfolders" { "contentstatsid" "123" "0" { "path" "/a" } "1" { "path" "/b" } }
				""");
		List<Path> legacy = SteamLocator.parseLibraryFolders("""
				"LibraryFolders" { "TimeNextStatsReport" "1" "1" "/c" "2" "/d" }
				""");

		assertEquals(List.of(Path.of("/a"), Path.of("/b")), current);
		assertEquals(List.of(Path.of("/c"), Path.of("/d")), legacy);
	}
}
