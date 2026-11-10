package io.github.icxd.knives_as_swords.sound;

import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.cs2.Cs2Install;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

/**
 * The knife sounds of the player's own game files, ready to play: converted and decoded once on
 * the worker thread, then kept in memory (they are a fraction of a second each).
 *
 * <p>Played through Minecraft's own sound engine as sounds of the "players" kind, so the game's
 * volume sliders apply. Client thread only.
 */
public final class KnifeAudio {
	/**
	 * The one sound the mod declares (in its {@code sounds.json}). It names no sound file of its
	 * own; what is heard is whatever {@link Playing} hands to the engine.
	 */
	private static final Identifier SOUND = Identifier.fromNamespaceAndPath(KnivesAsSwordsClient.MOD_ID, "knife");
	/** After a failure, how long to wait before trying to load again. */
	private static final long RETRY_NANOS = 60_000_000_000L;

	private static Map<KnifeSound, List<Pcm>> takes = Map.of();
	private static final Map<KnifeSound, Integer> lastTake = new EnumMap<>(KnifeSound.class);
	private static final Random RANDOM = new Random();
	private static int loadedScan = -1;
	private static boolean loading;
	private static long retryAt;
	private static int played;
	private static boolean reported;

	private KnifeAudio() {
	}

	/** Whether there is anything to play yet. */
	public static boolean ready() {
		return !takes.isEmpty();
	}

	/**
	 * Starts converting and decoding the sounds, if that has not happened yet for the game files
	 * as they are now. Never asks for anything: without the converter in place, or the player's
	 * agreement to fetch it, nothing happens.
	 */
	public static void prepare(KnifeRuntime runtime) {
		int scan = runtime.scanVersion();
		if (loading || loadedScan == scan || System.nanoTime() < retryAt) {
			return;
		}
		Optional<Cs2Install> install = runtime.state().flatMap(KnifeRuntime.State::install);
		KnifeRuntime.ExtractorStatus status = runtime.extractorStatus();
		if (install.isEmpty() || status == KnifeRuntime.ExtractorStatus.NEEDS_CONSENT
				|| status == KnifeRuntime.ExtractorStatus.UNSUPPORTED_PLATFORM) {
			return;
		}
		loading = true;
		runtime.onWorker(() -> decodeAll(runtime.knifeSounds(install.get())))
				.whenCompleteAsync((decoded, error) -> {
					loading = false;
					if (error != null) {
						KnivesAsSwordsClient.LOGGER.warn("Could not load the knife sounds", error);
						retryAt = System.nanoTime() + RETRY_NANOS;
						return;
					}
					takes = decoded;
					loadedScan = scan;
					int count = decoded.values().stream().mapToInt(List::size).sum();
					KnivesAsSwordsClient.LOGGER.info("Loaded {} knife sounds", count);
				}, Minecraft.getInstance());
	}

	/** Decodes every sound in a folder that belongs to one of the moments. Worker thread. */
	static Map<KnifeSound, List<Pcm>> decodeAll(Path folder) throws IOException {
		Map<KnifeSound, List<Pcm>> decoded = new EnumMap<>(KnifeSound.class);
		List<Path> files;
		try (Stream<Path> list = Files.list(folder)) {
			files = list.sorted().toList();
		}
		for (Path file : files) {
			Optional<KnifeSound> sound = KnifeSound.of(file.getFileName().toString());
			if (sound.isEmpty()) {
				continue;
			}
			try {
				Pcm pcm = Pcm.decodeMp3(file);
				if (pcm.samples().length > 0) {
					decoded.computeIfAbsent(sound.get(), key -> new ArrayList<>()).add(pcm);
				}
			} catch (IOException | RuntimeException e) {
				// One sound that cannot be read should not cost all the others.
				KnivesAsSwordsClient.LOGGER.warn("Could not decode {}", file, e);
			}
		}
		return decoded;
	}

	/**
	 * Plays one take of a sound, a different one from last time where there is a choice.
	 *
	 * @param volume 0 to 1, on top of the game's own volume settings
	 * @param delayTicks how many game ticks from now it should start; 0 for at once
	 */
	public static void play(KnifeSound sound, float volume, int delayTicks) {
		List<Pcm> choices = takes.get(sound);
		if (choices == null || choices.isEmpty() || volume <= 0f) {
			return;
		}
		int take = pick(choices.size(), lastTake.getOrDefault(sound, -1), RANDOM);
		lastTake.put(sound, take);
		// A touch higher or lower each time, as the game does it, so that repeats do not sound stamped out.
		Playing playing = new Playing(choices.get(take), volume, 0.97f + 0.06f * RANDOM.nextFloat());
		if (delayTicks > 0) {
			Minecraft.getInstance().getSoundManager().playDelayed(playing, delayTicks);
		} else {
			SoundEngine.PlayResult result = Minecraft.getInstance().getSoundManager().play(playing);
			if (!reported) {
				// Once, for whoever wonders why they hear nothing: the engine says whether it took the sound.
				reported = true;
				KnivesAsSwordsClient.LOGGER.info("First knife sound ({}): {}", sound, result);
			}
		}
		played++;
	}

	/** How many sounds have been handed to the sound engine so far. */
	public static int played() {
		return played;
	}

	/** Which of {@code count} takes to play, never the one played last if there is another. */
	static int pick(int count, int last, Random random) {
		if (count <= 1) {
			return 0;
		}
		int take = random.nextInt(last >= 0 && last < count ? count - 1 : count);
		return last >= 0 && last < count && take >= last ? take + 1 : take;
	}

	/** One sound being played: at the listener's ears rather than somewhere in the world. */
	private static final class Playing extends AbstractSoundInstance {
		private final Pcm pcm;

		Playing(Pcm pcm, float volume, float pitch) {
			super(SOUND, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
			this.pcm = pcm;
			this.volume = volume;
			this.pitch = pitch;
			this.attenuation = SoundInstance.Attenuation.NONE;
			this.relative = true;
		}

		@Override
		public CompletableFuture<AudioStream> getAudioStream(SoundBufferLibrary library, Identifier id,
				boolean repeatInstantly) {
			return CompletableFuture.completedFuture(new PcmStream(pcm));
		}
	}
}
