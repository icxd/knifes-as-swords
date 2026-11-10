package io.github.icxd.knives_as_swords.cs2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VpkDirectoryTest {
	@TempDir
	Path temp;

	@ParameterizedTest
	@ValueSource(ints = {1, 2})
	void listsEveryEntryWithItsFullPath(int version) throws IOException {
		Path vpk = temp.resolve("pak01_dir.vpk");
		new TestVpk().version(version)
				.file("models/a/one.vmdl_c")
				.file("models/a/two.vmdl_c")
				.file("models/b/three.vmat_c")
				.file("root_file.txt")
				.file("scripts/no_extension")
				.write(vpk);

		List<String> paths = VpkDirectory.list(vpk, path -> true).stream().map(VpkDirectory.Entry::path).sorted().toList();

		assertEquals(List.of("models/a/one.vmdl_c", "models/a/two.vmdl_c", "models/b/three.vmat_c", "root_file.txt",
				"scripts/no_extension"), paths);
	}

	@Test
	void appliesTheFilter() throws IOException {
		Path vpk = temp.resolve("pak01_dir.vpk");
		new TestVpk().file("a/keep.x").file("b/drop.x").write(vpk);

		List<VpkDirectory.Entry> entries = VpkDirectory.list(vpk, path -> path.startsWith("a/"));

		assertEquals(1, entries.size());
		assertEquals("a/keep.x", entries.get(0).path());
	}

	@Test
	void skipsPreloadDataAndKeepsEntryFields() throws IOException {
		Path vpk = temp.resolve("pak01_dir.vpk");
		new TestVpk()
				.file("a/first.x", new byte[] {1, 2, 3, 4, 5})
				.file("a/second.x")
				.write(vpk);

		List<VpkDirectory.Entry> entries = VpkDirectory.list(vpk, path -> true);

		assertEquals(2, entries.size());
		VpkDirectory.Entry first = entries.get(0);
		assertEquals("a/first.x", first.path());
		assertEquals(5, first.preloadBytes());
		assertEquals(100, first.length());
		assertEquals(105, first.size());
		assertEquals(0, first.archiveIndex());
		assertEquals(0xC0FFEE00, first.crc32());
		assertEquals("a/second.x", entries.get(1).path());
		assertEquals(100, entries.get(1).offset());
	}

	@Test
	void rejectsFilesThatAreNotVpks() throws IOException {
		Path file = temp.resolve("not_a.vpk");
		Files.write(file, new byte[64]);

		assertThrows(IOException.class, () -> VpkDirectory.list(file, path -> true));
	}

	@Test
	void rejectsTruncatedIndex() throws IOException {
		byte[] bytes = new TestVpk().file("a/one.x").file("a/two.x").bytes();
		Path file = temp.resolve("truncated_dir.vpk");
		// The header still announces the full index size.
		Files.write(file, Arrays.copyOf(bytes, bytes.length - 10));

		assertThrows(IOException.class, () -> VpkDirectory.list(file, path -> true));
	}

	@Test
	void rejectsIndexWithoutTerminators() throws IOException {
		byte[] bytes = new TestVpk().file("a/one.x").bytes();
		// Overwrite the tail of the index so the strings never terminate.
		Arrays.fill(bytes, bytes.length - 4, bytes.length, (byte) 'x');
		Path file = temp.resolve("corrupt_dir.vpk");
		Files.write(file, bytes);

		assertThrows(IOException.class, () -> VpkDirectory.list(file, path -> true));
	}
}
