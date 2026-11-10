package io.github.icxd.knives_as_swords.sound;

import fr.delthas.javamp3.Sound;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFormat;

/**
 * A decoded sound: plain samples, ready to be played.
 *
 * @param samples 16-bit signed samples, low byte first; for two channels, left and right in turn
 * @param sampleRate samples per second and channel
 * @param channels 1 or 2
 */
public record Pcm(byte[] samples, int sampleRate, int channels) {
	/** Longest sound accepted, in seconds: these are knife sounds, not music. */
	private static final int MAX_SECONDS = 20;
	/** Samples quieter than this at the very start are the encoder's padding, not the sound. */
	private static final int SILENCE = 48;
	/** Never more than this is cut off the start, in seconds, however quiet it is. */
	private static final float MAX_LEAD_SECONDS = 0.15f;

	public AudioFormat format() {
		return new AudioFormat(sampleRate, 16, channels, true, false);
	}

	public float seconds() {
		return samples.length / (float) (2 * channels * sampleRate);
	}

	/** Bytes per moment of sound: one sample for every channel. */
	int frameBytes() {
		return 2 * channels;
	}

	/** Decodes an MP3 file. */
	public static Pcm decodeMp3(Path file) throws IOException {
		try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
			return decodeMp3(in);
		}
	}

	public static Pcm decodeMp3(InputStream in) throws IOException {
		try (Sound sound = new Sound(in)) {
			int sampleRate = sound.getSamplingFrequency();
			int channels = sound.isStereo() ? 2 : 1;
			if (sampleRate <= 0) {
				throw new IOException("Not an MP3 sound this decoder understands");
			}
			int limit = MAX_SECONDS * sampleRate * channels * 2;
			// Read piece by piece: the library's own "decode everything" stops after the first frame.
			ByteArrayOutputStream decoded = new ByteArrayOutputStream();
			byte[] buffer = new byte[8192];
			int count;
			while ((count = sound.read(buffer)) > 0 && decoded.size() < limit) {
				decoded.write(buffer, 0, count);
			}
			return new Pcm(decoded.toByteArray(), sampleRate, channels).withoutLead();
		}
	}

	/**
	 * This sound without the silence at its start. MP3 files begin with a stretch of nothing (a
	 * frame of bookkeeping, and padding the encoder needs), which would make every sound late.
	 */
	public Pcm withoutLead() {
		int frame = frameBytes();
		int frames = samples.length / frame;
		int most = Math.min(frames, Math.round(MAX_LEAD_SECONDS * sampleRate));
		int lead = 0;
		search:
		for (; lead < most; lead++) {
			for (int channel = 0; channel < channels; channel++) {
				int at = lead * frame + channel * 2;
				int sample = (short) ((samples[at] & 0xFF) | (samples[at + 1] << 8));
				if (Math.abs(sample) > SILENCE) {
					break search;
				}
			}
		}
		if (lead == 0) {
			return this;
		}
		byte[] rest = new byte[(frames - lead) * frame];
		System.arraycopy(samples, lead * frame, rest, 0, rest.length);
		return new Pcm(rest, sampleRate, channels);
	}
}
