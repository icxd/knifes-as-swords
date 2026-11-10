package io.github.icxd.knives_as_swords.extract;

import io.github.icxd.knives_as_swords.cs2.Cs2Install;
import io.github.icxd.knives_as_swords.cs2.KnifeModel;
import io.github.icxd.knives_as_swords.util.FileUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
	private static final Duration TIMEOUT = Duration.ofMinutes(5);

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
