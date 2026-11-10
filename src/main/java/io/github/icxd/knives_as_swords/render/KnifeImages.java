package io.github.icxd.knives_as_swords.render;

import com.mojang.blaze3d.platform.NativeImage;

/** Image helpers for knife textures. */
final class KnifeImages {
	private KnifeImages() {
	}

	/** An image from {@code size * size} ARGB pixels, row by row from the top. */
	static NativeImage ofPixels(int[] argb, int size) {
		if (argb.length != size * size) {
			throw new IllegalArgumentException("Pixel data does not match the image size");
		}
		NativeImage image = new NativeImage(size, size, false);
		try {
			for (int y = 0; y < size; y++) {
				for (int x = 0; x < size; x++) {
					image.setPixel(x, y, argb[y * size + x]);
				}
			}
		} catch (RuntimeException e) {
			image.close();
			throw e;
		}
		return image;
	}

	/**
	 * Rearranges the surface texture the extractor writes next to a model (red: ambient occlusion,
	 * green: roughness, blue: metalness) into the order the knife shader reads (roughness,
	 * metalness, ambient occlusion). Changes the image in place.
	 */
	static NativeImage surfaceFromGltf(NativeImage image) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getPixel(x, y);
				int occlusion = (argb >> 16) & 0xFF;
				int roughness = (argb >> 8) & 0xFF;
				int metalness = argb & 0xFF;
				image.setPixel(x, y, 0xFF000000 | (roughness << 16) | (metalness << 8) | occlusion);
			}
		}
		return image;
	}

	/** A surface for knives that come without one: not metal, neither glossy nor dull. */
	static NativeImage plainSurface() {
		NativeImage image = new NativeImage(4, 4, false);
		image.fillRect(0, 0, 4, 4, 0xFF8000FF);
		return image;
	}

	/**
	 * Halves the image until neither side is longer than {@code maxSize}, averaging pixels.
	 * Returns the image itself when it already fits; otherwise the given image is closed.
	 */
	static NativeImage limitSize(NativeImage image, int maxSize) {
		int factor = 1;
		while (Math.max(image.getWidth(), image.getHeight()) / factor > maxSize
				&& image.getWidth() % (factor * 2) == 0 && image.getHeight() % (factor * 2) == 0) {
			factor *= 2;
		}
		if (factor == 1) {
			return image;
		}

		int width = image.getWidth() / factor;
		int height = image.getHeight() / factor;
		NativeImage smaller = new NativeImage(width, height, false);
		try {
			int area = factor * factor;
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					int a = 0, r = 0, g = 0, b = 0;
					for (int dy = 0; dy < factor; dy++) {
						for (int dx = 0; dx < factor; dx++) {
							int argb = image.getPixel(x * factor + dx, y * factor + dy);
							a += argb >>> 24;
							r += (argb >> 16) & 0xFF;
							g += (argb >> 8) & 0xFF;
							b += argb & 0xFF;
						}
					}
					smaller.setPixel(x, y, ((a / area) << 24) | ((r / area) << 16) | ((g / area) << 8) | (b / area));
				}
			}
		} catch (RuntimeException e) {
			smaller.close();
			throw e;
		} finally {
			image.close();
		}
		return smaller;
	}
}
