package io.github.icxd.knives_as_swords.extract;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExtractorInstallerTest {
	private static final URI SOMEWHERE = URI.create("https://example.invalid/tool.zip");

	@TempDir
	Path temp;

	private static byte[] zip(Map<String, String> files) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (var file : files.entrySet()) {
				zip.putNextEntry(new ZipEntry(file.getKey()));
				zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

	private ExtractorInstaller installer(byte[] archive, String expectedSha256) {
		return new ExtractorInstaller(temp.resolve("tools/tool-1.0"), SOMEWHERE, expectedSha256, "tool",
				uri -> new ByteArrayInputStream(archive));
	}

	@Test
	void installsAVerifiedArchive() throws Exception {
		Map<String, String> files = new LinkedHashMap<>();
		files.put("tool", "#!/bin/sh\n");
		files.put("lib/helper.so", "library");
		byte[] archive = zip(files);
		ExtractorInstaller installer = installer(archive, sha256(archive));

		assertTrue(installer.installed().isEmpty());
		Path executable = installer.install();

		assertEquals(temp.resolve("tools/tool-1.0/tool"), executable);
		assertEquals(executable, installer.installed().orElseThrow());
		assertArrayEquals("library".getBytes(StandardCharsets.UTF_8),
				Files.readAllBytes(temp.resolve("tools/tool-1.0/lib/helper.so")));
		if (!System.getProperty("os.name").toLowerCase().contains("win")) {
			assertTrue(Files.isExecutable(executable));
		}
		// The downloaded archive is not left behind.
		try (Stream<Path> leftovers = Files.list(temp.resolve("tools"))) {
			assertEquals(1, leftovers.count());
		}
	}

	@Test
	void refusesAnArchiveWithTheWrongHash() throws Exception {
		byte[] archive = zip(Map.of("tool", "tampered"));
		ExtractorInstaller installer = installer(archive, sha256("something else".getBytes(StandardCharsets.UTF_8)));

		IOException error = assertThrows(IOException.class, installer::install);

		assertTrue(error.getMessage().contains("verification"), error.getMessage());
		assertTrue(installer.installed().isEmpty());
		assertFalse(Files.exists(temp.resolve("tools/tool-1.0/tool")));
	}

	@Test
	void refusesEntriesOutsideTheInstallFolder() throws Exception {
		byte[] archive = zip(Map.of("../escaped.txt", "nope"));
		ExtractorInstaller installer = installer(archive, sha256(archive));

		assertThrows(IOException.class, installer::install);

		assertFalse(Files.exists(temp.resolve("tools/escaped.txt")));
		assertTrue(installer.installed().isEmpty());
	}

	@Test
	void refusesAnArchiveWithoutTheExecutable() throws Exception {
		byte[] archive = zip(Map.of("readme.txt", "hello"));
		ExtractorInstaller installer = installer(archive, sha256(archive));

		assertThrows(IOException.class, installer::install);
		assertTrue(installer.installed().isEmpty());
	}

	@Test
	void anInstallForAnotherPinIsNotReused() throws Exception {
		byte[] archive = zip(Map.of("tool", "v1"));
		installer(archive, sha256(archive)).install();

		ExtractorInstaller newerPin = installer(archive, sha256("v2".getBytes(StandardCharsets.UTF_8)));

		assertTrue(newerPin.installed().isEmpty());
	}
}
