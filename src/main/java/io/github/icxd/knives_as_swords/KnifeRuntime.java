package io.github.icxd.knives_as_swords;

import io.github.icxd.knives_as_swords.cs2.Cs2Install;
import io.github.icxd.knives_as_swords.cs2.KnifeModel;
import io.github.icxd.knives_as_swords.cs2.SteamLocator;
import io.github.icxd.knives_as_swords.extract.ExtractorInstaller;
import io.github.icxd.knives_as_swords.extract.ExtractorRelease;
import io.github.icxd.knives_as_swords.extract.KnifeExporter;
import io.github.icxd.knives_as_swords.extract.Source2ViewerCli;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/**
 * Everything the mod knows about the local game files, and the background work on them.
 * All file and process work runs on one worker thread; results are published as immutable state.
 */
public final class KnifeRuntime {
	private static final Logger LOGGER = KnivesAsSwordsClient.LOGGER;

	/**
	 * @param install the CS2 install, if one was found
	 * @param knives the knife models in it
	 */
	public record State(Optional<Cs2Install> install, List<KnifeModel> knives) {
		public Optional<KnifeModel> knife(String id) {
			return knives.stream().filter(knife -> knife.id().equals(id)).findFirst();
		}
	}

	public enum ExtractorStatus {
		/** Installed, or the player configured their own copy. */
		READY,
		/** Would have to be downloaded, and the player has not been asked yet. */
		NEEDS_CONSENT,
		/** Download agreed to but not done yet. */
		NEEDS_DOWNLOAD,
		/** No build of the extractor exists for this OS and CPU. */
		UNSUPPORTED_PLATFORM
	}

	private final Path configFile;
	private final Path dataDir;
	private final KnifeExporter exporter;
	private final Optional<ExtractorInstaller> installer;
	private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, KnivesAsSwordsClient.MOD_ID + "-worker");
		thread.setDaemon(true);
		return thread;
	});

	/** Re-read from disk by every scan, so edits apply without a restart. */
	private volatile ModConfig config = new ModConfig();
	/** {@code null} until the first scan has finished. */
	private volatile State state;
	/** Worker thread only. */
	private Source2ViewerCli cli;

	public KnifeRuntime(Path configFile, Path dataDir) {
		this.configFile = configFile;
		this.dataDir = dataDir;
		this.exporter = new KnifeExporter(dataDir.resolve("cache"));
		this.installer = ExtractorRelease.current()
				.map(release -> ExtractorInstaller.forRelease(dataDir.resolve("tools"), release));
	}

	/** The result of the latest scan, or empty while the first one is still running. */
	public Optional<State> state() {
		return Optional.ofNullable(state);
	}

	public Path dataDir() {
		return dataDir;
	}

	/** Reloads the config, then looks for CS2 and lists its knives, in the background. */
	public CompletableFuture<State> scan() {
		return CompletableFuture.supplyAsync(() -> {
			config = ModConfig.load(configFile);
			// A changed extractorPath must not keep using the previous executable.
			cli = null;
			State scanned;
			try {
				scanned = scanNow();
			} catch (RuntimeException e) {
				// Whatever went wrong, the game must keep working with vanilla swords.
				LOGGER.error("Unexpected error while looking for the game files", e);
				scanned = new State(Optional.empty(), List.of());
			}
			state = scanned;
			return scanned;
		}, worker);
	}

	private State scanNow() {
		Optional<Cs2Install> install = locate();
		if (install.isEmpty()) {
			LOGGER.info("CS2 not found; swords keep their vanilla look");
			return new State(Optional.empty(), List.of());
		}
		LOGGER.info("Found CS2 at {} (build {})", install.get().root(), install.get().buildId());

		List<KnifeModel> knives;
		try {
			knives = List.copyOf(KnifeModel.list(install.get()));
		} catch (IOException e) {
			LOGGER.warn("Could not read {}", install.get().mainPackage(), e);
			return new State(install, List.of());
		}
		LOGGER.info("Found {} knife models: {}", knives.size(),
				knives.stream().map(KnifeModel::id).collect(Collectors.joining(", ")));
		for (KnifeModel knife : knives) {
			LOGGER.debug("  {} -> {}", knife.id(), knife.modelPath());
		}

		try {
			exporter.pruneOtherBuilds(install.get());
		} catch (IOException e) {
			LOGGER.warn("Could not clean up the cache of older game versions", e);
		}
		return new State(install, knives);
	}

	private Optional<Cs2Install> locate() {
		ModConfig config = this.config;
		if (!config.cs2Dir.isBlank()) {
			try {
				Optional<Cs2Install> install = Cs2Install.atFolder(Path.of(config.cs2Dir));
				if (install.isPresent()) {
					return install;
				}
			} catch (InvalidPathException e) {
				// reported below
			}
			LOGGER.warn("Configured cs2Dir '{}' is not a CS2 install; searching the Steam libraries instead",
					config.cs2Dir);
		}
		return SteamLocator.system().findCs2();
	}

	public ExtractorStatus extractorStatus() {
		ModConfig config = this.config;
		if (!config.extractorPath.isBlank()) {
			return ExtractorStatus.READY;
		}
		if (installer.isEmpty()) {
			return ExtractorStatus.UNSUPPORTED_PLATFORM;
		}
		if (installer.get().installed().isPresent()) {
			return ExtractorStatus.READY;
		}
		return config.extractorDownloadAllowed ? ExtractorStatus.NEEDS_DOWNLOAD : ExtractorStatus.NEEDS_CONSENT;
	}

	/** Records that the player agreed to downloading the extractor. */
	public void allowExtractorDownload() {
		ModConfig config = this.config;
		config.extractorDownloadAllowed = true;
		config.save(configFile);
	}

	/**
	 * Converts a knife to glTF in the local cache, downloading the extractor first if that is
	 * needed and has been agreed to. Completes with the glTF file.
	 */
	public CompletableFuture<Path> export(Cs2Install install, KnifeModel knife) {
		return CompletableFuture.supplyAsync(() -> {
			try {
				Optional<Path> cached = exporter.cached(install, knife);
				if (cached.isPresent()) {
					return cached.get();
				}
				return exporter.export(extractor(), install, knife);
			} catch (IOException e) {
				throw new CompletionException(e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new CompletionException(e);
			}
		}, worker);
	}

	/** Worker thread only. */
	private Source2ViewerCli extractor() throws IOException, InterruptedException {
		if (cli != null) {
			return cli;
		}

		ModConfig config = this.config;
		Path executable;
		if (!config.extractorPath.isBlank()) {
			try {
				executable = Path.of(config.extractorPath);
			} catch (InvalidPathException e) {
				throw new IOException("Configured extractorPath is not a valid path: " + config.extractorPath);
			}
			if (!Files.isRegularFile(executable)) {
				throw new IOException("Configured extractorPath does not exist: " + executable);
			}
		} else {
			ExtractorInstaller extractorInstaller = installer.orElseThrow(() -> new IOException(
					"No Source2Viewer-CLI build exists for this platform; set extractorPath in the config"));
			Optional<Path> installed = extractorInstaller.installed();
			if (installed.isPresent()) {
				executable = installed.get();
			} else if (config.extractorDownloadAllowed) {
				LOGGER.info("Downloading Source2Viewer-CLI {} from {}", ExtractorRelease.VERSION,
						ExtractorRelease.PROJECT_URL);
				executable = extractorInstaller.install();
			} else {
				throw new IOException("The extractor is not installed and its download has not been agreed to");
			}
		}

		Source2ViewerCli candidate = new Source2ViewerCli(executable);
		LOGGER.info("Using Source2Viewer-CLI {} at {}", candidate.version(), executable);
		cli = candidate;
		return candidate;
	}
}
