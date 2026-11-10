package io.github.icxd.knives_as_swords.cs2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

/**
 * A local CS2 installation.
 *
 * @param root the install folder (the one containing {@code game/})
 * @param buildId identifies the installed game version; used to key the conversion cache
 */
public record Cs2Install(Path root, String buildId) {
	public static final int APP_ID = 730;
	private static final String MAIN_PACKAGE = "game/csgo/pak01_dir.vpk";

	/** The main asset package; weapons live under {@code weapons/models/} in it. */
	public Path mainPackage() {
		return root.resolve(MAIN_PACKAGE);
	}

	/**
	 * Looks for CS2 in one Steam library folder (a folder containing {@code steamapps/}).
	 */
	public static Optional<Cs2Install> inLibrary(Path library) {
		for (String steamApps : new String[] {"steamapps", "SteamApps"}) {
			Path apps = library.resolve(steamApps);
			Path manifest = apps.resolve("appmanifest_" + APP_ID + ".acf");
			if (!Files.isRegularFile(manifest)) {
				continue;
			}

			KeyValues state;
			try {
				state = KeyValues.parse(Files.readString(manifest, StandardCharsets.UTF_8))
						.child("AppState").orElse(null);
			} catch (IOException | IllegalArgumentException e) {
				continue;
			}
			if (state == null) {
				continue;
			}

			String installDir = state.string("installdir");
			if (installDir == null || installDir.isBlank()) {
				continue;
			}
			Path common = apps.resolve("common").normalize();
			Path root = common.resolve(installDir).normalize();
			if (!root.startsWith(common) || root.equals(common)) {
				continue;
			}

			Optional<Cs2Install> install = at(root, state.string("buildid"));
			if (install.isPresent()) {
				return install;
			}
		}
		return Optional.empty();
	}

	/**
	 * Uses an explicitly given install folder, e.g. from the config. The build id is taken from
	 * Steam's app manifest next to it when there is one.
	 */
	public static Optional<Cs2Install> atFolder(Path root) {
		root = root.toAbsolutePath().normalize();

		// <library>/steamapps/common/<installdir>
		Path common = root.getParent();
		Path apps = common == null ? null : common.getParent();
		if (apps != null) {
			Path library = apps.getParent();
			if (library != null) {
				Optional<Cs2Install> viaManifest = inLibrary(library);
				if (viaManifest.isPresent() && viaManifest.get().root().equals(root)) {
					return viaManifest;
				}
			}
		}

		return at(root, null);
	}

	private static Optional<Cs2Install> at(Path root, String buildId) {
		Path mainPackage = root.resolve(MAIN_PACKAGE);
		if (!Files.isRegularFile(mainPackage)) {
			return Optional.empty();
		}

		if (buildId == null || !buildId.matches("[0-9A-Za-z._-]{1,64}")) {
			buildId = fingerprint(mainPackage);
		}
		return Optional.of(new Cs2Install(root, buildId));
	}

	/** Fallback version key when Steam's manifest is unavailable: changes whenever the package index does. */
	private static String fingerprint(Path mainPackage) {
		try {
			BasicFileAttributes attributes = Files.readAttributes(mainPackage, BasicFileAttributes.class);
			return "pak-" + attributes.size() + "-" + attributes.lastModifiedTime().toMillis();
		} catch (IOException e) {
			return "unknown";
		}
	}
}
