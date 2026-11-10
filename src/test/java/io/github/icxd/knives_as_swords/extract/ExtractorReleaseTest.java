package io.github.icxd.knives_as_swords.extract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExtractorReleaseTest {
	@Test
	void mapsOperatingSystemAndArchitecture() {
		assertEquals(Optional.of(ExtractorRelease.WINDOWS_X64), ExtractorRelease.forSystem("Windows 11", "amd64"));
		assertEquals(Optional.of(ExtractorRelease.WINDOWS_ARM64), ExtractorRelease.forSystem("Windows 11", "aarch64"));
		assertEquals(Optional.of(ExtractorRelease.LINUX_X64), ExtractorRelease.forSystem("Linux", "amd64"));
		assertEquals(Optional.of(ExtractorRelease.LINUX_ARM64), ExtractorRelease.forSystem("Linux", "aarch64"));
		assertEquals(Optional.of(ExtractorRelease.LINUX_ARM), ExtractorRelease.forSystem("Linux", "arm"));
		assertEquals(Optional.of(ExtractorRelease.MACOS_ARM64), ExtractorRelease.forSystem("Mac OS X", "aarch64"));
		assertEquals(Optional.empty(), ExtractorRelease.forSystem("Mac OS X", "x86_64"));
		assertEquals(Optional.empty(), ExtractorRelease.forSystem("Windows 10", "x86"));
		assertEquals(Optional.empty(), ExtractorRelease.forSystem("FreeBSD", "amd64"));
	}

	@Test
	void everyBuildIsPinned() {
		for (ExtractorRelease release : ExtractorRelease.values()) {
			assertTrue(release.sha256().matches("[0-9a-f]{64}"), release.name());
			assertEquals("https://github.com/ValveResourceFormat/ValveResourceFormat/releases/download/"
					+ ExtractorRelease.VERSION + "/cli-" + release.platform() + ".zip",
					release.downloadUri().toString());
		}
		assertEquals("Source2Viewer-CLI.exe", ExtractorRelease.WINDOWS_X64.executableName());
		assertEquals("Source2Viewer-CLI", ExtractorRelease.LINUX_X64.executableName());
	}
}
