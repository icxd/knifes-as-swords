package io.github.icxd.knives_as_swords.finish;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.Deflater;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RasterTest {
	@TempDir
	Path temp;

	private static float[] sample(Raster raster, float u, float v, int address) {
		float[] out = new float[4];
		raster.sample(u, v, address, address, out);
		return out;
	}

	/** Two pixels side by side: black, then white. */
	private static Raster blackWhite(boolean srgb) {
		return Raster.ofBytes(2, 1, new byte[] {0, 0, 0, (byte) 255, (byte) 255, (byte) 255, (byte) 255, (byte) 255},
				srgb);
	}

	@Test
	void samplesPixelCentresExactlyAndBlendsBetweenThem() {
		Raster raster = blackWhite(false);

		assertEquals(0f, sample(raster, 0.25f, 0.5f, Raster.CLAMP)[0], 1e-6f);
		assertEquals(1f, sample(raster, 0.75f, 0.5f, Raster.CLAMP)[0], 1e-6f);
		assertEquals(0.5f, sample(raster, 0.5f, 0.5f, Raster.CLAMP)[0], 1e-6f);
	}

	@Test
	void addressModesDifferOutsideTheImage() {
		Raster raster = blackWhite(false);

		// Just left of the image: clamping stays black, wrapping blends with the white pixel from the far side.
		assertEquals(0f, sample(raster, 0f, 0.5f, Raster.CLAMP)[0], 1e-6f);
		assertEquals(0.5f, sample(raster, 0f, 0.5f, Raster.WRAP)[0], 1e-6f);
		assertEquals(1f, sample(raster, 1.75f, 0.5f, Raster.WRAP)[0], 1e-6f);
		// Mirroring shows the image backwards on the next tile.
		assertEquals(1f, sample(raster, 1.25f, 0.5f, Raster.MIRROR)[0], 1e-6f);
		assertEquals(0f, sample(raster, 1.75f, 0.5f, Raster.MIRROR)[0], 1e-6f);
	}

	@Test
	void colorIsDecodedBeforeBlendingButAlphaIsNot() {
		Raster raster = Raster.ofBytes(1, 1, new byte[] {(byte) 128, (byte) 128, (byte) 128, (byte) 128}, true);

		float[] texel = sample(raster, 0.5f, 0.5f, Raster.CLAMP);

		// sRGB 128 is about 21.6 % in linear light.
		assertEquals(0.2158f, texel[0], 1e-3f);
		assertEquals(128f / 255f, texel[3], 1e-6f);
	}

	@Test
	void shrinkingAveragesPixels() {
		Raster raster = Raster.ofFloats(2, 2, new float[] {
				0, 0, 0, 1, 1, 0, 0, 1,
				0, 1, 0, 1, 1, 1, 0, 1}).shrunkTo(1);

		assertEquals(1, raster.width);
		assertArrayEquals(new float[] {0.5f, 0.5f, 0f, 1f}, sample(raster, 0.5f, 0.5f, Raster.CLAMP), 1e-6f);
	}

	@Test
	void readsPngFilesOfDifferentKinds() throws IOException {
		BufferedImage color = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
		color.setRGB(0, 0, 0x80FF4000);
		color.setRGB(1, 0, 0xFF000000);
		Path colorFile = temp.resolve("color.png");
		ImageIO.write(color, "png", colorFile.toFile());

		float[] texel = sample(Raster.load(colorFile, false), 0.25f, 0.5f, Raster.CLAMP);
		assertArrayEquals(new float[] {1f, 64f / 255f, 0f, 128f / 255f}, texel, 1e-6f);

		// Grey images must come through with their stored values, not "corrected" ones.
		BufferedImage grey = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
		grey.getRaster().setSample(0, 0, 0, 100);
		Path greyFile = temp.resolve("grey.png");
		ImageIO.write(grey, "png", greyFile.toFile());

		assertArrayEquals(new float[] {100f / 255f, 100f / 255f, 100f / 255f, 1f},
				sample(Raster.load(greyFile, false), 0.5f, 0.5f, Raster.CLAMP), 1e-6f);
	}

	@Test
	void rejectsFilesThatAreNotImages() throws IOException {
		Path file = temp.resolve("broken.png");
		Files.writeString(file, "not an image");

		assertThrows(IOException.class, () -> Raster.load(file, false));
	}

	/** Writes a 2x2 OpenEXR file with float channels, as one ZIP-compressed block or uncompressed lines. */
	private static byte[] exr(boolean compressed) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteBuffer head = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(20000630).putInt(2);
		out.write(head.array());

		ByteArrayOutputStream channels = new ByteArrayOutputStream();
		for (String name : new String[] {"B", "G", "R"}) {
			channels.write((name + "\0").getBytes(StandardCharsets.US_ASCII));
			channels.write(ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(2).putInt(0).putInt(1).putInt(1)
					.array());
		}
		channels.write(0);
		attribute(out, "channels", "chlist", channels.toByteArray());
		attribute(out, "compression", "compression", new byte[] {(byte) (compressed ? 3 : 0)});
		attribute(out, "dataWindow", "box2i",
				ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putInt(0).putInt(1).putInt(1).array());
		out.write(0);

		// Pixel (x, y) holds R = x, G = y, B = 10 + x + 2y. Each line stores B, then G, then R.
		ByteBuffer pixels = ByteBuffer.allocate(2 * 3 * 2 * 4).order(ByteOrder.LITTLE_ENDIAN);
		for (int y = 0; y < 2; y++) {
			for (int x = 0; x < 2; x++) {
				pixels.putFloat(10 + x + 2 * y);
			}
			for (int x = 0; x < 2; x++) {
				pixels.putFloat(y);
			}
			for (int x = 0; x < 2; x++) {
				pixels.putFloat(x);
			}
		}
		byte[] raw = pixels.array();

		if (compressed) {
			// OpenEXR's ZIP: split into even and odd bytes, delta-code, then deflate.
			byte[] reordered = new byte[raw.length];
			int half = (raw.length + 1) / 2;
			for (int i = 0; i < raw.length; i++) {
				reordered[(i & 1) == 0 ? i / 2 : half + i / 2] = raw[i];
			}
			byte[] delta = new byte[raw.length];
			delta[0] = reordered[0];
			for (int i = 1; i < raw.length; i++) {
				delta[i] = (byte) (reordered[i] - reordered[i - 1] + 128);
			}
			Deflater deflater = new Deflater();
			deflater.setInput(delta);
			deflater.finish();
			byte[] packed = new byte[raw.length * 2 + 64];
			int length = deflater.deflate(packed);
			deflater.end();

			long offset = out.size() + 8;
			out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(offset).array());
			out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(0).putInt(length).array());
			out.write(packed, 0, length);
		} else {
			int lineBytes = raw.length / 2;
			long first = out.size() + 16;
			out.write(ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN).putLong(first).putLong(first + 8 + lineBytes)
					.array());
			for (int y = 0; y < 2; y++) {
				out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(y).putInt(lineBytes).array());
				out.write(raw, y * lineBytes, lineBytes);
			}
		}
		return out.toByteArray();
	}

	private static void attribute(ByteArrayOutputStream out, String name, String type, byte[] value)
			throws IOException {
		out.write((name + "\0" + type + "\0").getBytes(StandardCharsets.US_ASCII));
		out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value.length).array());
		out.write(value);
	}

	@Test
	void readsFloatImagesCompressedOrNot() throws IOException {
		for (boolean compressed : new boolean[] {false, true}) {
			Path file = temp.resolve(compressed ? "zip.exr" : "plain.exr");
			Files.write(file, exr(compressed));

			Raster raster = Raster.load(file, false);

			assertEquals(2, raster.width);
			assertEquals(2, raster.height);
			// The lower right pixel: R = 1, G = 1, B = 13; no alpha channel means opaque.
			assertArrayEquals(new float[] {1f, 1f, 13f, 1f}, sample(raster, 0.75f, 0.75f, Raster.CLAMP), 1e-6f);
			assertArrayEquals(new float[] {0f, 0f, 10f, 1f}, sample(raster, 0.25f, 0.25f, Raster.CLAMP), 1e-6f);
		}
	}

	@Test
	void rejectsTruncatedFloatImages() throws IOException {
		byte[] whole = exr(true);
		Path file = temp.resolve("cut.exr");
		Files.write(file, java.util.Arrays.copyOf(whole, whole.length - 10));

		assertThrows(IOException.class, () -> Raster.load(file, false));
	}
}
