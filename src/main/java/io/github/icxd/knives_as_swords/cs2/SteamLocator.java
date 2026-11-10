package io.github.icxd.knives_as_swords.cs2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds Steam, its library folders, and the CS2 install among them. */
public final class SteamLocator {
	private static final Pattern REG_SZ_VALUE = Pattern.compile("REG_SZ\\s+(.+)");

	private final String osName;
	private final Path userHome;
	private final Function<String, String> env;
	private final Function<String[], Optional<String>> registry;

	/**
	 * @param osName value of the {@code os.name} system property
	 * @param env environment variable lookup
	 * @param registry Windows registry lookup taking {@code {key, valueName}}
	 */
	SteamLocator(String osName, Path userHome, Function<String, String> env,
			Function<String[], Optional<String>> registry) {
		this.osName = osName.toLowerCase(Locale.ROOT);
		this.userHome = userHome;
		this.env = env;
		this.registry = registry;
	}

	public static SteamLocator system() {
		return new SteamLocator(System.getProperty("os.name", ""), Path.of(System.getProperty("user.home", "")),
				System::getenv, SteamLocator::queryRegistry);
	}

	/** Finds CS2 in any Steam library on this machine. */
	public Optional<Cs2Install> findCs2() {
		for (Path library : libraryFolders()) {
			Optional<Cs2Install> install = Cs2Install.inLibrary(library);
			if (install.isPresent()) {
				return install;
			}
		}
		return Optional.empty();
	}

	/** All Steam library folders: every Steam install found, plus the extra libraries each one lists. */
	public List<Path> libraryFolders() {
		Set<Path> libraries = new LinkedHashSet<>();
		for (Path steam : steamRoots()) {
			libraries.add(steam);
			for (String relative : new String[] {"steamapps/libraryfolders.vdf", "SteamApps/libraryfolders.vdf",
					"config/libraryfolders.vdf"}) {
				Path file = steam.resolve(relative);
				if (Files.isRegularFile(file)) {
					try {
						libraries.addAll(parseLibraryFolders(Files.readString(file, StandardCharsets.UTF_8)));
					} catch (IOException | IllegalArgumentException e) {
						// Unreadable or malformed: fall back to the other candidates.
					}
				}
			}
		}

		List<Path> existing = new ArrayList<>();
		Set<Path> seen = new LinkedHashSet<>();
		for (Path library : libraries) {
			try {
				// Resolves symlinks (e.g. ~/.steam/steam), so the same library is not listed twice.
				Path real = library.toRealPath();
				if (seen.add(real)) {
					existing.add(real);
				}
			} catch (IOException e) {
				// Does not exist (e.g. an unplugged drive).
			}
		}
		return existing;
	}

	/**
	 * Extracts library paths from {@code libraryfolders.vdf}. Handles the current layout, where
	 * each numbered entry is a block with a {@code path}, and the older one, where it is the path.
	 */
	static List<Path> parseLibraryFolders(String vdf) {
		List<Path> paths = new ArrayList<>();
		KeyValues folders = KeyValues.parse(vdf).child("libraryfolders").orElse(null);
		if (folders == null) {
			return paths;
		}

		for (KeyValues entry : folders.children) {
			if (!entry.key.chars().allMatch(Character::isDigit) || entry.key.isEmpty()) {
				continue;
			}
			String path = entry.isBlock() ? entry.string("path") : entry.value;
			if (path == null || path.isBlank()) {
				continue;
			}
			try {
				paths.add(Path.of(path));
			} catch (InvalidPathException e) {
				// Skip entries this OS cannot represent.
			}
		}
		return paths;
	}

	/** Candidate Steam install folders for this OS, most authoritative first. May not exist. */
	List<Path> steamRoots() {
		List<Path> roots = new ArrayList<>();

		if (osName.contains("win")) {
			registry.apply(new String[] {"HKCU\\Software\\Valve\\Steam", "SteamPath"}).ifPresent(p -> add(roots, p));
			registry.apply(new String[] {"HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam", "InstallPath"})
					.ifPresent(p -> add(roots, p));
			registry.apply(new String[] {"HKLM\\SOFTWARE\\Valve\\Steam", "InstallPath"}).ifPresent(p -> add(roots, p));
			for (String variable : new String[] {"ProgramFiles(x86)", "ProgramFiles"}) {
				String programFiles = env.apply(variable);
				if (programFiles != null && !programFiles.isBlank()) {
					add(roots, programFiles, "Steam");
				}
			}
		} else if (osName.contains("mac")) {
			roots.add(userHome.resolve("Library/Application Support/Steam"));
		} else {
			String dataHome = env.apply("XDG_DATA_HOME");
			if (dataHome != null && !dataHome.isBlank()) {
				add(roots, dataHome, "Steam");
			}
			roots.add(userHome.resolve(".local/share/Steam"));
			roots.add(userHome.resolve(".steam/steam"));
			roots.add(userHome.resolve(".steam/root"));
			// Flatpak and Snap keep their own home.
			roots.add(userHome.resolve(".var/app/com.valvesoftware.Steam/.local/share/Steam"));
			roots.add(userHome.resolve(".var/app/com.valvesoftware.Steam/data/Steam"));
			roots.add(userHome.resolve("snap/steam/common/.local/share/Steam"));
		}

		return roots;
	}

	private static void add(List<Path> roots, String path, String... more) {
		try {
			roots.add(Path.of(path.trim(), more));
		} catch (InvalidPathException e) {
			// ignore
		}
	}

	/** Extracts the value from the output of {@code reg query <key> /v <name>}. */
	static Optional<String> parseRegistryValue(String regQueryOutput) {
		Matcher matcher = REG_SZ_VALUE.matcher(regQueryOutput);
		return matcher.find() ? Optional.of(matcher.group(1).trim()) : Optional.empty();
	}

	private static Optional<String> queryRegistry(String[] keyAndValue) {
		String systemRoot = System.getenv("SystemRoot");
		String reg = systemRoot == null ? "reg" : systemRoot + "\\System32\\reg.exe";

		Process process = null;
		try {
			process = new ProcessBuilder(reg, "query", keyAndValue[0], "/v", keyAndValue[1])
					.redirectErrorStream(true)
					.start();
			process.getOutputStream().close();
			// The output is a few lines, far below the pipe buffer, so reading after exit cannot block.
			if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
				return Optional.empty();
			}
			return parseRegistryValue(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
		} catch (IOException e) {
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		} finally {
			if (process != null) {
				process.destroyForcibly();
			}
		}
	}
}
