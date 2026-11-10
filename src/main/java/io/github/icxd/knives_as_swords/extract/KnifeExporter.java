package io.github.icxd.knives_as_swords.extract;

import io.github.icxd.knives_as_swords.cs2.Cs2Install;
import io.github.icxd.knives_as_swords.cs2.KnifeModel;
import io.github.icxd.knives_as_swords.util.FileUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Converts knife models to glTF with the external extractor, into the local cache.
 *
 * <p>Layout: {@code <cache>/<buildId>/models/<knife id>/}, holding whatever the extractor wrote
 * plus a marker naming the main model file. The marker is written last, so a half-finished
 * export is never used.
 */
public final class KnifeExporter {
	private static final String MARKER = "export.txt";
	private static final String BONES = "bones.txt";
	private static final String ANIMATIONS_MARKER = "animations.txt";
	private static final Duration TIMEOUT = Duration.ofMinutes(5);
	/** The model's bone list in the extractor's text dump of the model's data block. */
	private static final Pattern BONE_NAMES = Pattern.compile("m_boneName\\s*=\\s*\\[([^\\]]*)\\]");
	private static final Pattern QUOTED = Pattern.compile("\"([^\"]*)\"");

	private final Path cacheDir;

	public KnifeExporter(Path cacheDir) {
		this.cacheDir = cacheDir.toAbsolutePath().normalize();
	}

	/** The cached glTF of this knife for the installed game version, if already exported. */
	public Optional<Path> cached(Cs2Install install, KnifeModel knife) {
		Path folder = folder(install, knife);
		Path marker = folder.resolve(MARKER);
		if (!Files.isRegularFile(marker)) {
			return Optional.empty();
		}
		try {
			Path model = folder.resolve(Files.readString(marker, StandardCharsets.UTF_8).trim()).normalize();
			return model.startsWith(folder) && Files.isRegularFile(model) ? Optional.of(model) : Optional.empty();
		} catch (IOException e) {
			return Optional.empty();
		}
	}

	/** Exports the knife (default finish, no animations) unless it is already cached; returns the glTF file. */
	public Path export(Source2ViewerCli cli, Cs2Install install, KnifeModel knife)
			throws IOException, InterruptedException {
		Optional<Path> cached = cached(install, knife);
		if (cached.isPresent()) {
			return cached.get();
		}

		Path folder = folder(install, knife);
		FileUtil.deleteRecursively(folder);
		Files.createDirectories(folder);

		// The output folder exists, so the tool writes to <folder>/<path inside the package>, with
		// the textures as separate PNG files next to the model.
		Source2ViewerCli.Result result = cli.run(folder, TIMEOUT,
				"--input", install.mainPackage().toString(),
				"--vpk_filepath", knife.modelPath(),
				"--vpk_extensions", "vmdl_c",
				"--output", folder.toString(),
				"--vpk_decompile",
				"--gltf_export_format", "glb",
				"--gltf_export_materials",
				"--gltf_textures_adapt");
		if (result.exitCode() != 0) {
			throw new IOException("Extractor failed for " + knife.modelPath() + " (exit code " + result.exitCode()
					+ ")\n" + result.output());
		}

		// The tool also exits with 0 when a file fails to convert, so the model itself is the check.
		Path model = findModel(folder).orElseThrow(() -> new IOException(
				"Extractor produced no glTF for " + knife.modelPath() + "\n" + result.output()));
		Files.writeString(folder.resolve(MARKER), folder.relativize(model).toString(), StandardCharsets.UTF_8);
		return model;
	}

	/**
	 * The names of the model's bones, in the order its vertices refer to them. The converted
	 * model itself carries bone numbers only, because its skeleton lives in a separate file.
	 */
	public List<String> boneNames(Source2ViewerCli cli, Cs2Install install, KnifeModel knife)
			throws IOException, InterruptedException {
		Path file = folder(install, knife).resolve(BONES);
		if (Files.isRegularFile(file)) {
			return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(line -> !line.isBlank()).toList();
		}

		Files.createDirectories(file.getParent());
		Source2ViewerCli.Result result = cli.run(file.getParent(), TIMEOUT,
				"--input", install.mainPackage().toString(),
				"--vpk_filepath", knife.modelPath(),
				"--block", "DATA");
		List<String> names = parseBoneNames(result.output());
		if (result.exitCode() != 0 || names.isEmpty()) {
			throw new IOException("Could not read the bones of " + knife.modelPath() + " (exit code "
					+ result.exitCode() + ")");
		}
		Files.write(file, names, StandardCharsets.UTF_8);
		return names;
	}

	static List<String> parseBoneNames(String dataBlockDump) {
		List<String> names = new ArrayList<>();
		Matcher list = BONE_NAMES.matcher(dataBlockDump);
		if (list.find()) {
			Matcher name = QUOTED.matcher(list.group(1));
			while (name.find()) {
				names.add(name.group(1));
			}
		}
		return names;
	}

	/**
	 * Converts the knife's first-person animation clips to glTF, one file per clip, unless that
	 * is already cached. Returns the folder holding them.
	 */
	public Path exportAnimations(Source2ViewerCli cli, Cs2Install install, KnifeModel knife)
			throws IOException, InterruptedException {
		if (!knife.hasAnimations()) {
			throw new IOException("The game has no first-person animations for " + knife.id());
		}
		Path folder = cacheDir.resolve(install.buildId()).resolve("animations").resolve(knife.id());
		Path marker = folder.resolve(ANIMATIONS_MARKER);
		if (Files.isRegularFile(marker)) {
			Path clips = folder.resolve(Files.readString(marker, StandardCharsets.UTF_8).trim()).normalize();
			if (clips.startsWith(folder) && Files.isDirectory(clips)) {
				return clips;
			}
		}

		FileUtil.deleteRecursively(folder);
		Files.createDirectories(folder);
		Source2ViewerCli.Result result = cli.run(folder, TIMEOUT,
				"--input", install.mainPackage().toString(),
				"--vpk_filepath", knife.animationFolder(),
				"--vpk_extensions", "vnmclip_c",
				"--output", folder.toString(),
				"--vpk_decompile",
				"--gltf_export_format", "glb",
				"--gltf_export_animations");
		Path clips = folder.resolve(knife.animationFolder()).normalize();
		boolean any;
		try (Stream<Path> files = Files.isDirectory(clips) ? Files.list(clips) : Stream.empty()) {
			any = files.anyMatch(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".glb"));
		}
		if (result.exitCode() != 0 || !clips.startsWith(folder) || !any) {
			throw new IOException("Extractor produced no animations for " + knife.id() + " (exit code "
					+ result.exitCode() + ")\n" + result.output());
		}
		Files.writeString(marker, folder.relativize(clips).toString(), StandardCharsets.UTF_8);
		return clips;
	}

	/** Removes cached conversions made from other game versions. */
	public void pruneOtherBuilds(Cs2Install install) throws IOException {
		if (!Files.isDirectory(cacheDir)) {
			return;
		}
		List<Path> builds;
		try (Stream<Path> children = Files.list(cacheDir)) {
			builds = children.toList();
		}
		for (Path build : builds) {
			if (!build.getFileName().toString().equals(install.buildId())) {
				FileUtil.deleteRecursively(build);
			}
		}
	}

	private Path folder(Cs2Install install, KnifeModel knife) {
		return cacheDir.resolve(install.buildId()).resolve("models").resolve(knife.id());
	}

	/** The extractor also writes a separate collision file, {@code <name>_physics}; that one is skipped. */
	private static Optional<Path> findModel(Path folder) throws IOException {
		try (Stream<Path> files = Files.walk(folder)) {
			return files.filter(Files::isRegularFile)
					.filter(file -> {
						String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
						return name.endsWith(".glb") && !name.endsWith("_physics.glb");
					})
					.sorted()
					.findFirst();
		}
	}
}
