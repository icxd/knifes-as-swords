package io.github.icxd.knives_as_swords.extract;

import io.github.icxd.knives_as_swords.cs2.Cs2Install;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

/**
 * Converts the knife sounds of the player's own game files into sound files in the local cache.
 * Like everything converted, they stay on this machine.
 */
public final class SoundExporter {
	/** Where the sounds every knife shares are, inside the main package. The slash keeps other knife folders out. */
	public static final String KNIFE_SOUNDS = "sounds/weapons/knife/";
	private static final Duration TIMEOUT = Duration.ofMinutes(3);
	/** Written last: a folder without it is from a conversion that did not finish. */
	private static final String DONE = "converted";

	private final Path cacheDir;

	public SoundExporter(Path cacheDir) {
		this.cacheDir = cacheDir;
	}

	private Path folder(Cs2Install install) {
		return cacheDir.resolve(install.buildId()).resolve("sounds");
	}

	/** The folder of converted knife sounds, if they were converted before for this version of the game. */
	public Optional<Path> cached(Cs2Install install) {
		Path folder = folder(install);
		return Files.isRegularFile(folder.resolve(DONE)) ? Optional.of(folder.resolve(KNIFE_SOUNDS)) : Optional.empty();
	}

	/**
	 * Converts the knife sounds, unless that was done before.
	 *
	 * @return the folder they are in, one {@code .mp3} file per sound
	 */
	public Path knifeSounds(Source2ViewerCli cli, Cs2Install install) throws IOException, InterruptedException {
		Optional<Path> cached = cached(install);
		if (cached.isPresent()) {
			return cached.get();
		}
		Path folder = folder(install);
		Files.createDirectories(folder);
		// The folder exists, so the tool writes each sound to <folder>/<path inside the package>.
		Source2ViewerCli.Result output = cli.run(folder, TIMEOUT,
				"--input", install.mainPackage().toString(),
				"--vpk_filepath", KNIFE_SOUNDS,
				"--vpk_extensions", "vsnd_c",
				"--output", folder.toString(),
				"--vpk_decompile");
		Path sounds = folder.resolve(KNIFE_SOUNDS);
		// The tool exits with 0 even when files fail, so go by what is there.
		if (output.exitCode() != 0 || !Files.isDirectory(sounds)) {
			throw new IOException("Extractor failed converting the knife sounds (exit code " + output.exitCode() + ")\n"
					+ output.output());
		}
		Files.writeString(folder.resolve(DONE), "");
		return sounds;
	}
}
