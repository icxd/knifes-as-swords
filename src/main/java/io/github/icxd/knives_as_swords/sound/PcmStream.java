package io.github.icxd.knives_as_swords.sound;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import javax.sound.sampled.AudioFormat;
import net.minecraft.client.sounds.AudioStream;
import org.jspecify.annotations.Nullable;

/**
 * Hands a decoded sound to Minecraft's sound engine, piece by piece as the engine asks for it.
 */
public final class PcmStream implements AudioStream {
	private final Pcm pcm;
	private int position;

	public PcmStream(Pcm pcm) {
		this.pcm = pcm;
	}

	@Override
	public AudioFormat getFormat() {
		return pcm.format();
	}

	/**
	 * @return the next piece, never cutting a moment of sound in two; {@code null} once there is
	 *     nothing left, which is how the engine learns that the sound is over
	 */
	@Override
	public @Nullable ByteBuffer read(int expectedSize) {
		int left = pcm.samples().length - position;
		int frame = pcm.frameBytes();
		left -= left % frame;
		if (left <= 0) {
			return null;
		}
		int size = Math.min(left, Math.max(expectedSize - expectedSize % frame, frame));
		// The engine hands the buffer straight to the sound card's driver, which wants memory of its own kind.
		ByteBuffer buffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
		buffer.put(pcm.samples(), position, size);
		buffer.flip();
		position += size;
		return buffer;
	}

	@Override
	public void close() {
	}
}
