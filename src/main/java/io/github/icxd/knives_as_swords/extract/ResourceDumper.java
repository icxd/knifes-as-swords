package io.github.icxd.knives_as_swords.extract;

import io.github.icxd.knives_as_swords.cs2.Cs2Install;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gets individual resources out of the game's main package with the external extractor, into
 * the local cache: the settings of materials as text, and textures as image files.
 *
 * <p>Layout: {@code <cache>/<buildId>/data/<path>.kv3} and
 * {@code <cache>/<buildId>/textures/<path>.png} (or {@code .exr} for floating-point textures).
 */
public final class ResourceDumper {
	private static final Duration TIMEOUT = Duration.ofMinutes(5);
	private static final String TEXTURE_SUFFIX = ".vtex_c";
	/** Starts the part of the extractor's output that belongs to one file: {@code [2/3] some/path.vmat_c}. */
	private static final Pattern FILE_HEADER = Pattern.compile("(?m)^\\[\\d+/\\d+]\\s+(\\S.*?)\\s*$");
	private static final String DATA_HEADER = "--- Data for block \"DATA\" ---";
	/** Paths come from game files; only plain relative ones are ever turned into file names. */
	private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9_][A-Za-z0-9_./+-]*");

	private final Path cacheDir;

	public ResourceDumper(Path cacheDir) {
		this.cacheDir = cacheDir.toAbsolutePath().normalize();
	}

	/**
	 * The data block of each of the given compiled resources, as KeyValues3 text.
	 *
	 * @param paths paths inside the main package, e.g. {@code some/material.vmat_c}
	 * @return text by path; resources the package does not contain are left out
	 */
	public Map<String, String> dataBlocks(Source2ViewerCli cli, Cs2Install install, List<String> paths)
			throws IOException, InterruptedException {
		Path folder = cacheDir.resolve(install.buildId()).resolve("data");
		Map<String, String> result = new LinkedHashMap<>();
		Set<String> missing = new LinkedHashSet<>();
		for (String path : paths) {
			Path file = cacheFile(folder, path, ".kv3");
			if (Files.isRegularFile(file)) {
				result.put(path, Files.readString(file, StandardCharsets.UTF_8));
			} else {
				missing.add(path);
			}
		}
		if (missing.isEmpty()) {
			return result;
		}

		Files.createDirectories(folder);
		Source2ViewerCli.Result output = cli.run(folder, TIMEOUT,
				"--input", install.mainPackage().toString(),
				"--vpk_filepath", String.join(",", missing),
				"--block", "DATA");
		if (output.exitCode() != 0) {
			throw new IOException("Extractor failed reading " + missing + " (exit code " + output.exitCode() + ")");
		}
		for (Map.Entry<String, String> dumped : splitDataBlocks(output.output()).entrySet()) {
			if (missing.contains(dumped.getKey())) {
				Path file = cacheFile(folder, dumped.getKey(), ".kv3");
				Files.createDirectories(file.getParent());
				Files.writeString(file, dumped.getValue(), StandardCharsets.UTF_8);
				result.put(dumped.getKey(), dumped.getValue());
			}
		}
		return result;
	}

	/** Splits the extractor's output for several files into the data block text of each. */
	static Map<String, String> splitDataBlocks(String output) {
		Map<String, String> blocks = new LinkedHashMap<>();
		Matcher header = FILE_HEADER.matcher(output);
		List<int[]> spans = new ArrayList<>();
		List<String> names = new ArrayList<>();
		while (header.find()) {
			names.add(header.group(1).replace('\\', '/'));
			spans.add(new int[] {header.end(), output.length()});
			if (spans.size() > 1) {
				spans.get(spans.size() - 2)[1] = header.start();
			}
		}
		for (int i = 0; i < names.size(); i++) {
			String section = output.substring(spans.get(i)[0], spans.get(i)[1]);
			int data = section.indexOf(DATA_HEADER);
			if (data >= 0) {
				blocks.put(names.get(i), section.substring(data + DATA_HEADER.length()).strip());
			}
		}
		return blocks;
	}

	/**
	 * Converts compiled textures to image files.
	 *
	 * @param paths paths of compiled textures inside the main package, ending in {@code .vtex_c}
	 * @return image file by path; textures that could not be converted are left out
	 */
	public Map<String, Path> textures(Source2ViewerCli cli, Cs2Install install, List<String> paths)
			throws IOException, InterruptedException {
		Path folder = cacheDir.resolve(install.buildId()).resolve("textures");
		Map<String, Path> result = new LinkedHashMap<>();
		Set<String> missing = new LinkedHashSet<>();
		for (String path : paths) {
			Path image = findImage(folder, path);
			if (image != null) {
				result.put(path, image);
			} else {
				missing.add(path);
			}
		}
		if (missing.isEmpty()) {
			return result;
		}

		Files.createDirectories(folder);
		// The folder exists, so the tool writes each texture to <folder>/<path inside the package>.
		Source2ViewerCli.Result output = cli.run(folder, TIMEOUT,
				"--input", install.mainPackage().toString(),
				"--vpk_filepath", String.join(",", missing),
				"--vpk_extensions", "vtex_c",
				"--output", folder.toString(),
				"--vpk_decompile");
		if (output.exitCode() != 0) {
			throw new IOException("Extractor failed converting " + missing + " (exit code " + output.exitCode()
					+ ")\n" + output.output());
		}
		for (String path : missing) {
			Path image = findImage(folder, path);
			if (image != null) {
				result.put(path, image);
			}
		}
		return result;
	}

	private static Path findImage(Path folder, String texturePath) throws IOException {
		if (!texturePath.endsWith(TEXTURE_SUFFIX)) {
			throw new IOException("Not a compiled texture: " + texturePath);
		}
		String stem = texturePath.substring(0, texturePath.length() - TEXTURE_SUFFIX.length());
		for (String extension : new String[] {".png", ".exr"}) {
			Path file = cacheFile(folder, stem, extension);
			if (Files.isRegularFile(file)) {
				return file;
			}
		}
		return null;
	}

	private static Path cacheFile(Path folder, String resourcePath, String extension) throws IOException {
		if (!SAFE_PATH.matcher(resourcePath).matches() || resourcePath.contains("..")) {
			throw new IOException("Refusing to use an unusual resource path: " + resourcePath);
		}
		Path file = folder.resolve(resourcePath + extension).normalize();
		if (!file.startsWith(folder)) {
			throw new IOException("Refusing to use an unusual resource path: " + resourcePath);
		}
		return file;
	}
}
