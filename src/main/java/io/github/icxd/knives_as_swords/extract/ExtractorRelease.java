package io.github.icxd.knives_as_swords.extract;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;

/**
 * The pinned release of the external extractor: Source2Viewer-CLI from the ValveResourceFormat
 * project (MIT licensed). It is not bundled; the player downloads it from the project's own
 * GitHub release after agreeing to, and every download is checked against the hashes below.
 *
 * <p>To update: change {@link #VERSION} and replace every hash with the SHA-256 of the matching
 * {@code cli-<platform>.zip} of that release.
 */
public enum ExtractorRelease {
	WINDOWS_X64("windows-x64", "67aaf8a67a85ca60fd09ff343e62103a4cdf3a2d177fc656231d7d734c5b3aa3", true),
	WINDOWS_ARM64("windows-arm64", "a5348023b3d881847c228ea5aad6f2d8eb65abb8cdeed7fa138ea6426ff9ee93", true),
	LINUX_X64("linux-x64", "29d25a6d6b62377fc5386c3e5145b0a2180a401a50041afe9494b3f503deaef5", false),
	LINUX_ARM64("linux-arm64", "6fae01594f0c54c42a8aad2f3c2c8771b40a18195a907c5c686bcb5a94a67023", false),
	LINUX_ARM("linux-arm", "7339f0dbab567a8fb7fad70a5e2df256efe2e2fa043e211bcb0d34d17da49d70", false),
	MACOS_ARM64("macos-arm64", "3e3196a678a8f010c345b49bac67c63bc7af0ef02e4543bb7a80b7508c1eefc4", false);

	public static final String VERSION = "20.1";
	public static final String PROJECT_URL = "https://github.com/ValveResourceFormat/ValveResourceFormat";
	private static final String DOWNLOAD_BASE = PROJECT_URL + "/releases/download/" + VERSION + "/";

	private final String platform;
	private final String sha256;
	private final boolean windows;

	ExtractorRelease(String platform, String sha256, boolean windows) {
		this.platform = platform;
		this.sha256 = sha256;
		this.windows = windows;
	}

	public String platform() {
		return platform;
	}

	public String sha256() {
		return sha256;
	}

	public URI downloadUri() {
		return URI.create(DOWNLOAD_BASE + "cli-" + platform + ".zip");
	}

	public String executableName() {
		return windows ? "Source2Viewer-CLI.exe" : "Source2Viewer-CLI";
	}

	/** The build for the machine this JVM runs on, if the project publishes one. */
	public static Optional<ExtractorRelease> current() {
		return forSystem(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
	}

	static Optional<ExtractorRelease> forSystem(String osName, String osArch) {
		String os = osName.toLowerCase(Locale.ROOT);
		String arch = osArch.toLowerCase(Locale.ROOT);
		boolean x64 = arch.equals("amd64") || arch.equals("x86_64");
		boolean arm64 = arch.equals("aarch64") || arch.equals("arm64");

		if (os.contains("win")) {
			return x64 ? Optional.of(WINDOWS_X64) : arm64 ? Optional.of(WINDOWS_ARM64) : Optional.empty();
		}
		if (os.contains("mac")) {
			return arm64 ? Optional.of(MACOS_ARM64) : Optional.empty();
		}
		if (os.contains("linux")) {
			if (x64) {
				return Optional.of(LINUX_X64);
			}
			if (arm64) {
				return Optional.of(LINUX_ARM64);
			}
			return arch.startsWith("arm") ? Optional.of(LINUX_ARM) : Optional.empty();
		}
		return Optional.empty();
	}
}
