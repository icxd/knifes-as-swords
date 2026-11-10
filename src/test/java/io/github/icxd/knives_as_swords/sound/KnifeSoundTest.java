package io.github.icxd.knives_as_swords.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Knife sounds: which file is for which moment, and getting decoded sound to the sound engine. Made-up sound only. */
class KnifeSoundTest {
	@TempDir
	Path temp;

	/** One second of a made-up sound: silence for {@code lead} moments, then a steady ramp. */
	private static Pcm sound(int channels, int lead, int moments) {
		byte[] samples = new byte[moments * channels * 2];
		for (int i = lead; i < moments; i++) {
			for (int channel = 0; channel < channels; channel++) {
				int value = 1000 + i;
				samples[(i * channels + channel) * 2] = (byte) value;
				samples[(i * channels + channel) * 2 + 1] = (byte) (value >> 8);
			}
		}
		return new Pcm(samples, 44100, channels);
	}

	@Test
	void aFileIsForAMomentGoingByItsName() {
		assertEquals(Optional.of(KnifeSound.DRAW), KnifeSound.of("knife_draw_med_clean_03.mp3"));
		assertEquals(Optional.of(KnifeSound.SWING), KnifeSound.of("knife_swish_light_09.mp3"));
		assertEquals(Optional.of(KnifeSound.SWING_HEAVY), KnifeSound.of("knife_swish_heavy_1.mp3"));
		assertEquals(Optional.of(KnifeSound.HIT), KnifeSound.of("KNIFE_HIT_LIGHT_FLESH_10.MP3"));
		assertEquals(Optional.of(KnifeSound.HIT_HEAVY), KnifeSound.of("heavy_hit_flesh_06.mp3"));
		assertEquals(Optional.of(KnifeSound.INSPECT), KnifeSound.of("knife_inspect_turn_12.mp3"));
	}

	@Test
	void otherFilesBelongToNoMoment() {
		// A different sound that merely starts the same, the text file next to a sound, and no number.
		assertTrue(KnifeSound.of("knife_swish_light_gear_01.mp3").isEmpty());
		assertTrue(KnifeSound.of("knife_draw_med_clean_03.vsnd").isEmpty());
		assertTrue(KnifeSound.of("knife_draw_med_clean_.mp3").isEmpty());
		assertTrue(KnifeSound.of("something_else.mp3").isEmpty());
		assertTrue(KnifeSound.of("").isEmpty());
	}

	@Test
	void theSilenceAnMp3StartsWithIsCutOff() {
		Pcm padded = sound(2, 2000, 10_000);
		Pcm trimmed = padded.withoutLead();

		assertEquals((10_000 - 2000) * 4, trimmed.samples().length);
		assertEquals(padded.samples()[2000 * 4], trimmed.samples()[0]);
		assertEquals(44100, trimmed.sampleRate());
		assertEquals(2, trimmed.channels());
		// A sound that starts at once is left as it is.
		Pcm prompt = sound(1, 0, 500);
		assertSame(prompt, prompt.withoutLead());
	}

	@Test
	void notMoreThanAMomentIsEverCutOff() {
		// A whole second of quiet before the sound: that is the sound's own, not padding.
		Pcm late = sound(1, 44100, 50_000);

		int cut = (late.samples().length - late.withoutLead().samples().length) / 2;
		assertTrue(cut > 0 && cut < 44100 / 4, "cut " + cut + " moments");
		// Nothing but silence stays a (shorter) silence rather than breaking.
		assertTrue(new Pcm(new byte[4000], 44100, 2).withoutLead().samples().length >= 0);
	}

	@Test
	void knowsItsLengthAndFormat() {
		Pcm stereo = sound(2, 0, 22_050);

		assertEquals(0.5f, stereo.seconds(), 1e-4f);
		assertEquals(44100f, stereo.format().getSampleRate());
		assertEquals(16, stereo.format().getSampleSizeInBits());
		assertEquals(2, stereo.format().getChannels());
		assertTrue(!stereo.format().isBigEndian());
	}

	@Test
	void theEngineGetsTheSoundInPiecesAndThenNothing() {
		Pcm pcm = sound(2, 0, 1000);
		PcmStream stream = new PcmStream(pcm);

		// Asked for a size that would cut a moment in two: gets whole moments only.
		ByteBuffer first = stream.read(1002);
		assertEquals(1000, first.remaining());
		assertTrue(first.isDirect());
		assertEquals(pcm.samples()[0], first.get(0));
		assertEquals(pcm.samples()[999], first.get(999));

		int total = first.remaining();
		ByteBuffer next;
		while ((next = stream.read(1002)) != null) {
			assertEquals(pcm.samples()[total], next.get(0));
			total += next.remaining();
		}
		assertEquals(pcm.samples().length, total);
		assertNull(stream.read(1002));
		assertEquals(pcm.format().getChannels(), stream.getFormat().getChannels());
	}

	@Test
	void aShortSoundComesInOnePiece() {
		PcmStream stream = new PcmStream(sound(1, 0, 100));

		assertEquals(200, stream.read(176_400).remaining());
		assertNull(stream.read(176_400));
		// And one with nothing in it is over before it starts.
		assertNull(new PcmStream(new Pcm(new byte[0], 44100, 2)).read(1000));
	}

	@Test
	void theSameTakeIsNotPlayedTwiceRunning() {
		Random random = new Random(7);
		int last = -1;
		int[] counts = new int[5];
		for (int i = 0; i < 2000; i++) {
			int take = KnifeAudio.pick(5, last, random);
			assertTrue(take >= 0 && take < 5);
			assertNotEquals(last, take);
			counts[take]++;
			last = take;
		}
		for (int count : counts) {
			assertTrue(count > 250, "every take gets its turn: " + count);
		}
		// With a single take there is no choice, and a stale "last" does no harm.
		assertEquals(0, KnifeAudio.pick(1, 0, random));
		assertTrue(KnifeAudio.pick(3, 17, random) < 3);
	}

	@Test
	void whatIsNotAnMp3IsRefusedRatherThanPlayed() {
		assertThrows(IOException.class, () -> Pcm.decodeMp3(new ByteArrayInputStream(new byte[4000])));
		assertThrows(IOException.class,
				() -> Pcm.decodeMp3(new ByteArrayInputStream("not a sound at all".repeat(300).getBytes())));
	}

	@Test
	void aFolderWithNothingUsableGivesNoSoundsAndNoError() throws IOException {
		Files.writeString(temp.resolve("knife_swish_light_01.mp3"), "this is not really a sound");
		Files.writeString(temp.resolve("knife_swish_light_01.vsnd"), "<!-- notes -->");
		Files.writeString(temp.resolve("readme.txt"), "nothing to hear here");

		assertTrue(KnifeAudio.decodeAll(temp).isEmpty());
	}
}
