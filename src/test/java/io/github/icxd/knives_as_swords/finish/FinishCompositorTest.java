package io.github.icxd.knives_as_swords.finish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Paints flat, made-up inputs, where the right answer can be worked out by hand. */
class FinishCompositorTest {
	private static final int RED = 0xFFFF0000;

	private static PaintJob job(int style) {
		PaintJob job = new PaintJob();
		job.style = style;
		// A mid-grey knife with no creases, fully paintable, with nothing that wears early.
		job.baseColor = Raster.constant(0.2f, 0.2f, 0.2f, 1f);
		// Underneath it is smooth metal.
		job.baseMaterial = Raster.constant(0.25f, 1f, 0f, 1f);
		job.ambientOcclusion = Raster.constant(1f, 1f, 1f, 0f);
		job.masks = Raster.constant(1f, 0f, 0f, 1f);
		job.wear = Raster.constant(0f, 0f, 0f, 1f);
		job.grunge = Raster.constant(1f, 1f, 1f, 1f);
		for (float[] color : job.colors) {
			color[0] = 1f;
			color[1] = 0f;
			color[2] = 0f;
		}
		return job;
	}

	private static int paint(PaintJob job) {
		int[] pixels = FinishCompositor.compose(job, 4).color();
		assertEquals(16, pixels.length);
		for (int pixel : pixels) {
			assertEquals(pixels[0], pixel, "flat inputs must give a flat result");
		}
		return pixels[0];
	}

	/** The surface at one pixel: roughness in the red byte, metalness in the green one. */
	private static int surface(PaintJob job) {
		int[] pixels = FinishCompositor.compose(job, 4).material();
		assertEquals(16, pixels.length);
		return pixels[0];
	}

	private static int red(int argb) {
		return (argb >> 16) & 0xFF;
	}

	private static int green(int argb) {
		return (argb >> 8) & 0xFF;
	}

	/** 0.2 in linear light, as an sRGB byte. */
	private static final int BASE_GREY = 124;

	@Test
	void aFreshSolidCoatIsThePaintColor() {
		assertEquals(RED, paint(job(PaintJob.SOLID)));
	}

	@Test
	void paintWearsOffWhereTheKnifeSaysItWearsFirst() {
		PaintJob job = job(PaintJob.SOLID);
		// Alpha of this texture marks exposed edges; with that much, even a new knife is bare there.
		job.ambientOcclusion = Raster.constant(1f, 1f, 1f, 1f);

		int pixel = paint(job);

		assertEquals(BASE_GREY, red(pixel), 1);
		assertEquals(BASE_GREY, green(pixel), 1);
	}

	@Test
	void moreWearExposesMore() {
		PaintJob job = job(PaintJob.SOLID);
		// Not enough on its own to come off...
		job.ambientOcclusion = Raster.constant(1f, 1f, 1f, 0.2f);
		assertEquals(RED, paint(job));

		// ...but worn six times as hard, it does.
		job.wearAmount = 1f;
		assertEquals(BASE_GREY, red(paint(job)), 1);
	}

	@Test
	void anodizingLeavesUnpaintableAreasAlone() {
		PaintJob job = job(PaintJob.ANODIZED);
		job.masks = Raster.constant(0f, 0f, 0f, 1f);

		int pixel = paint(job);

		assertEquals(BASE_GREY, red(pixel), 1);
		assertEquals(BASE_GREY, green(pixel), 1);
	}

	@Test
	void anodizingTintsPaintableAreas() {
		assertEquals(RED, paint(job(PaintJob.ANODIZED)));
	}

	@Test
	void patternChannelsPickTheColors() {
		PaintJob job = job(PaintJob.ANODIZED_MULTICOLORED);
		job.colors[2][0] = 0f;
		job.colors[2][1] = 1f;
		// Pure green in the pattern selects the third colour; low alpha keeps it away from the "scratched" band.
		job.pattern = Raster.constant(0f, 1f, 0f, 0.2f);

		assertEquals(0xFF00FF00, paint(job));
	}

	@Test
	void aCustomPaintJobShowsItsPicture() {
		PaintJob job = job(PaintJob.CUSTOM);
		job.pattern = Raster.constant(0f, 0f, 1f, 0.2f);

		assertEquals(0xFF0000FF, paint(job));
	}

	@Test
	void whereThePictureHasNoAlphaItNeverWears() {
		PaintJob job = job(PaintJob.CUSTOM);
		job.pattern = Raster.constant(0f, 0f, 1f, 0f);
		job.ambientOcclusion = Raster.constant(1f, 1f, 1f, 1f);
		job.wearAmount = 1f;

		// The picture's alpha says how readily it rubs off; at zero even the most exposed edge keeps it.
		int pixel = paint(job);
		assertTrue((pixel & 0xFF) > 200 && red(pixel) < 30, Integer.toHexString(pixel));
	}

	@Test
	void patinaOnlyTouchesTreatedMetal() {
		PaintJob job = job(PaintJob.PATINA);
		job.pattern = Raster.constant(1f, 1f, 1f, 1f);
		job.masks = Raster.constant(0f, 0f, 0f, 1f);

		assertEquals(BASE_GREY, red(paint(job)), 1);

		job.masks = Raster.constant(1f, 0f, 0f, 1f);
		int treated = paint(job);
		assertTrue(red(treated) > 200 && green(treated) < 30, Integer.toHexString(treated));
	}

	@Test
	void freshPaintCoversTheMetalUnderneath() {
		PaintJob job = job(PaintJob.SOLID);
		job.paintRoughness = 0.6f;

		int surface = surface(job);

		// As rough as the paint, not metallic, and the knife's own ambient occlusion in the blue byte.
		assertEquals(153, red(surface), 1);
		assertEquals(0, green(surface));
		assertEquals(255, surface & 0xFF);
	}

	@Test
	void theKnifeIsShadedByItsOwnOcclusionNotThePaints() {
		PaintJob job = job(PaintJob.SOLID);
		// What the paint goes by says "deep in a crease" everywhere; the knife itself says "half in shadow".
		job.ambientOcclusion = Raster.constant(0.1f, 0.1f, 0.1f, 0f);
		job.baseOcclusion = Raster.constant(0.5f, 0f, 0f, 1f);

		assertEquals(128, surface(job) & 0xFF, 1);
	}

	@Test
	void wornPaintShowsTheMetalAgain() {
		PaintJob job = job(PaintJob.SOLID);
		job.ambientOcclusion = Raster.constant(1f, 1f, 1f, 1f);

		int surface = surface(job);

		assertEquals(64, red(surface), 1);
		assertEquals(255, green(surface));
	}

	@Test
	void anodizedMetalStaysMetal() {
		PaintJob job = job(PaintJob.ANODIZED);
		job.paintRoughness = 0.3f;

		int surface = surface(job);

		assertEquals(77, red(surface), 1);
		assertEquals(255, green(surface));

		// Where the dye does not reach, the knife's own surface is untouched.
		job.masks = Raster.constant(0f, 0f, 0f, 1f);
		job.baseMaterial = Raster.constant(0.8f, 0f, 0f, 1f);
		surface = surface(job);
		assertEquals(204, red(surface), 1);
		assertEquals(0, green(surface));
	}

	@Test
	void aRoughnessMapStandsInForTheSingleValue() {
		PaintJob job = job(PaintJob.CUSTOM);
		job.pattern = Raster.constant(0f, 0f, 1f, 0.2f);
		job.paintRoughness = 0.9f;
		job.roughnessTexture = Raster.constant(0.1f, 0f, 0f, 1f);

		assertEquals(26, red(surface(job)), 1);
	}

	@Test
	void everyStyleCopesWithEveryAmountOfWear() {
		for (int style = PaintJob.SOLID; style <= PaintJob.GUNSMITH; style++) {
			for (float wear : new float[] {0f, 0.07f, 0.5f, 1f}) {
				PaintJob job = job(style);
				job.wearAmount = wear;
				job.pattern = Raster.constant(0.3f, 0.6f, 0.9f, 0.75f);
				job.ambientOcclusion = Raster.constant(0.5f, 0.5f, 0.5f, 0.3f);
				job.wear = Raster.constant(0.5f, 0.5f, 0.5f, 1f);
				job.grunge = Raster.constant(0.4f, 0.5f, 0.6f, 1f);
				job.position = Raster.constant(0.3f, -0.2f, 0.1f, 1f);

				assertEquals(0xFF, paint(job) >>> 24, "style " + style + " at wear " + wear);
				assertEquals(0xFF, surface(job) >>> 24, "style " + style + " at wear " + wear);
			}
		}
	}
}
