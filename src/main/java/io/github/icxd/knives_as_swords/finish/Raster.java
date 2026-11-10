package io.github.icxd.knives_as_swords.finish;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * An image to sample from while compositing a finish: four channels per pixel, either 8-bit
 * (ordinary textures) or floating point (the position map).
 *
 * <p>Sampling is bilinear and works on what a GPU would see: colour textures are converted from
 * sRGB to linear light before neighbouring pixels are mixed.
 */
final class Raster {
	/** How coordinates outside 0..1 are treated; the numbers are the game's own for these modes. */
	static final int WRAP = 0;
	static final int MIRROR = 1;
	static final int CLAMP = 2;

	private static final int MAX_SIDE = 8192;
	private static final float[] SRGB_TO_LINEAR = new float[256];
	private static final float[] BYTE_TO_UNIT = new float[256];

	static {
		for (int i = 0; i < 256; i++) {
			float value = i / 255f;
			BYTE_TO_UNIT[i] = value;
			SRGB_TO_LINEAR[i] = value <= 0.04045f ? value / 12.92f : (float) Math.pow((value + 0.055) / 1.055, 2.4);
		}
	}

	final int width;
	final int height;
	/** RGBA bytes, or {@code null} for a float image. */
	private final byte[] bytes;
	/** RGBA floats, or {@code null} for an 8-bit image. */
	private final float[] floats;
	/** Whether the RGB channels hold sRGB-encoded colour. Alpha never does. */
	private final boolean srgb;

	private Raster(int width, int height, byte[] bytes, float[] floats, boolean srgb) {
		this.width = width;
		this.height = height;
		this.bytes = bytes;
		this.floats = floats;
		this.srgb = srgb;
	}

	static Raster ofBytes(int width, int height, byte[] rgba, boolean srgb) {
		if (rgba.length != width * height * 4) {
			throw new IllegalArgumentException("Pixel data does not match the image size");
		}
		return new Raster(width, height, rgba, null, srgb);
	}

	static Raster ofFloats(int width, int height, float[] rgba) {
		if (rgba.length != width * height * 4) {
			throw new IllegalArgumentException("Pixel data does not match the image size");
		}
		return new Raster(width, height, null, rgba, false);
	}

	/** A one-pixel image, for textures a material leaves out. */
	static Raster constant(float r, float g, float b, float a) {
		return ofFloats(1, 1, new float[] {r, g, b, a});
	}

	/** Loads a PNG or OpenEXR file as written by the extractor. */
	static Raster load(Path file, boolean srgb) throws IOException {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (name.endsWith(".exr")) {
			return ExrReader.read(file);
		}

		BufferedImage image;
		try (InputStream in = Files.newInputStream(file)) {
			image = ImageIO.read(in);
		}
		if (image == null) {
			throw new IOException("Not a readable image: " + file);
		}
		int width = image.getWidth();
		int height = image.getHeight();
		if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE) {
			throw new IOException("Unexpected image size " + width + "x" + height + ": " + file);
		}

		byte[] rgba = new byte[width * height * 4];
		WritableRaster raster = image.getRaster();
		int bands = raster.getNumBands();
		if (image.getColorModel() instanceof IndexColorModel || bands < 1 || bands > 4) {
			int[] row = new int[width];
			for (int y = 0; y < height; y++) {
				image.getRGB(0, y, width, 1, row, 0, width);
				for (int x = 0; x < width; x++) {
					int at = (y * width + x) * 4;
					rgba[at] = (byte) (row[x] >> 16);
					rgba[at + 1] = (byte) (row[x] >> 8);
					rgba[at + 2] = (byte) row[x];
					rgba[at + 3] = (byte) (row[x] >>> 24);
				}
			}
		} else {
			// Straight from the stored samples: going through getRGB would "colour correct" grey images.
			int[] shift = new int[bands];
			for (int band = 0; band < bands; band++) {
				shift[band] = Math.max(raster.getSampleModel().getSampleSize(band) - 8, 0);
			}
			int[] row = new int[width * bands];
			for (int y = 0; y < height; y++) {
				raster.getPixels(0, y, width, 1, row);
				for (int x = 0; x < width; x++) {
					int at = (y * width + x) * 4;
					int from = x * bands;
					int first = row[from] >> shift[0];
					if (bands <= 2) {
						rgba[at] = rgba[at + 1] = rgba[at + 2] = (byte) first;
						rgba[at + 3] = bands == 2 ? (byte) (row[from + 1] >> shift[1]) : (byte) 255;
					} else {
						rgba[at] = (byte) first;
						rgba[at + 1] = (byte) (row[from + 1] >> shift[1]);
						rgba[at + 2] = (byte) (row[from + 2] >> shift[2]);
						rgba[at + 3] = bands == 4 ? (byte) (row[from + 3] >> shift[3]) : (byte) 255;
					}
				}
			}
		}
		return ofBytes(width, height, rgba, srgb);
	}

	/**
	 * Averages 2x2 blocks until neither side is longer than {@code maxSide}. Meant for images that
	 * are sampled one-to-one with the output, where anything finer would only alias.
	 */
	Raster shrunkTo(int maxSide) {
		Raster current = this;
		while (Math.max(current.width, current.height) > maxSide && current.width % 2 == 0
				&& current.height % 2 == 0) {
			current = current.halved();
		}
		return current;
	}

	private Raster halved() {
		int w = width / 2;
		int h = height / 2;
		float[] texel = new float[4];
		if (floats != null) {
			float[] out = new float[w * h * 4];
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					for (int c = 0; c < 4; c++) {
						out[(y * w + x) * 4 + c] = (floats[((y * 2) * width + x * 2) * 4 + c]
								+ floats[((y * 2) * width + x * 2 + 1) * 4 + c]
								+ floats[((y * 2 + 1) * width + x * 2) * 4 + c]
								+ floats[((y * 2 + 1) * width + x * 2 + 1) * 4 + c]) * 0.25f;
					}
				}
			}
			return new Raster(w, h, null, out, false);
		}

		// Averaged in linear light, then stored linear: the result no longer needs sRGB decoding.
		float[] out = new float[w * h * 4];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				float r = 0f, g = 0f, b = 0f, a = 0f;
				for (int dy = 0; dy < 2; dy++) {
					for (int dx = 0; dx < 2; dx++) {
						texel(x * 2 + dx, y * 2 + dy, texel);
						r += texel[0];
						g += texel[1];
						b += texel[2];
						a += texel[3];
					}
				}
				int at = (y * w + x) * 4;
				out[at] = r * 0.25f;
				out[at + 1] = g * 0.25f;
				out[at + 2] = b * 0.25f;
				out[at + 3] = a * 0.25f;
			}
		}
		return new Raster(w, h, null, out, false);
	}

	/** Samples at texture coordinates (0..1 across the image, v pointing down), into {@code out[0..3]}. */
	void sample(float u, float v, int addressU, int addressV, float[] out) {
		float x = address(u, addressU) * width - 0.5f;
		float y = address(v, addressV) * height - 0.5f;
		int x0 = (int) Math.floor(x);
		int y0 = (int) Math.floor(y);
		float fx = x - x0;
		float fy = y - y0;
		int x1 = edge(x0 + 1, width, addressU);
		int y1 = edge(y0 + 1, height, addressV);
		x0 = edge(x0, width, addressU);
		y0 = edge(y0, height, addressV);

		float w00 = (1f - fx) * (1f - fy);
		float w10 = fx * (1f - fy);
		float w01 = (1f - fx) * fy;
		float w11 = fx * fy;
		if (floats != null) {
			int a = (y0 * width + x0) * 4;
			int b = (y0 * width + x1) * 4;
			int c = (y1 * width + x0) * 4;
			int d = (y1 * width + x1) * 4;
			for (int k = 0; k < 4; k++) {
				out[k] = floats[a + k] * w00 + floats[b + k] * w10 + floats[c + k] * w01 + floats[d + k] * w11;
			}
			return;
		}

		float[] rgb = srgb ? SRGB_TO_LINEAR : BYTE_TO_UNIT;
		int a = (y0 * width + x0) * 4;
		int b = (y0 * width + x1) * 4;
		int c = (y1 * width + x0) * 4;
		int d = (y1 * width + x1) * 4;
		for (int k = 0; k < 3; k++) {
			out[k] = rgb[bytes[a + k] & 0xFF] * w00 + rgb[bytes[b + k] & 0xFF] * w10 + rgb[bytes[c + k] & 0xFF] * w01
					+ rgb[bytes[d + k] & 0xFF] * w11;
		}
		out[3] = BYTE_TO_UNIT[bytes[a + 3] & 0xFF] * w00 + BYTE_TO_UNIT[bytes[b + 3] & 0xFF] * w10
				+ BYTE_TO_UNIT[bytes[c + 3] & 0xFF] * w01 + BYTE_TO_UNIT[bytes[d + 3] & 0xFF] * w11;
	}

	/** Samples with coordinates clamped to the image. */
	void sample(float u, float v, float[] out) {
		sample(u, v, CLAMP, CLAMP, out);
	}

	private void texel(int x, int y, float[] out) {
		int at = (y * width + x) * 4;
		if (floats != null) {
			System.arraycopy(floats, at, out, 0, 4);
			return;
		}
		float[] rgb = srgb ? SRGB_TO_LINEAR : BYTE_TO_UNIT;
		out[0] = rgb[bytes[at] & 0xFF];
		out[1] = rgb[bytes[at + 1] & 0xFF];
		out[2] = rgb[bytes[at + 2] & 0xFF];
		out[3] = BYTE_TO_UNIT[bytes[at + 3] & 0xFF];
	}

	private static float address(float coordinate, int mode) {
		if (!Float.isFinite(coordinate)) {
			return 0f;
		}
		return switch (mode) {
			case WRAP -> coordinate - (float) Math.floor(coordinate);
			case MIRROR -> {
				float t = coordinate - 2f * (float) Math.floor(coordinate / 2f);
				yield t > 1f ? 2f - t : t;
			}
			default -> Math.clamp(coordinate, 0f, 1f);
		};
	}

	/** Brings a pixel index next to the image's edge back inside it. */
	private static int edge(int index, int size, int mode) {
		if (index >= 0 && index < size) {
			return index;
		}
		return mode == WRAP ? Math.floorMod(index, size) : Math.clamp(index, 0, size - 1);
	}
}
