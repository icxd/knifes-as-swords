package io.github.icxd.knives_as_swords.finish;

import java.util.stream.IntStream;

/**
 * Paints a knife with a finish, producing the knife's colour texture and, alongside it, how
 * rough and how metallic each point of the surface is.
 *
 * <p>The game these finishes come from stores them as recipes rather than pictures: a pattern,
 * up to four colours, and one of nine ways ("styles") of putting those onto a weapon, with the
 * amount of wear deciding how much of the paint has rubbed off again. This class follows those
 * recipes per pixel.
 */
final class FinishCompositor {
	/** What bare, scratched metal looks like under an anodized coating, and how rough it is. */
	private static final float[] BARE_METAL = {0.38f, 0.37f, 0.35f};
	private static final float BARE_METAL_ROUGHNESS = 0.35f;
	/** How dark and how bright painted and metallic surfaces may get as they wear: floor, curve, ceiling. */
	private static final float[] PAINT_LEVELS = {0.045f, 1.3219f, 1f};
	private static final float[] METALLIC_LEVELS = {0.08f, 1.3219f, 1f};
	private static final float LUMA_R = 0.2125f;
	private static final float LUMA_G = 0.7154f;
	private static final float LUMA_B = 0.0721f;

	/**
	 * A painted knife, as two images of the same size, row by row from the top.
	 *
	 * @param color opaque ARGB pixels (sRGB)
	 * @param material per pixel, in the red, green and blue bytes: roughness, metalness and
	 *     ambient occlusion, each 0 to 255
	 */
	record Result(int[] color, int[] material) {
	}

	private FinishCompositor() {
	}

	/** @param size width and height of the result in pixels */
	static Result compose(PaintJob job, int size) {
		int[] color = new int[size * size];
		int[] material = new int[size * size];
		IntStream.range(0, size).parallel().forEach(y -> {
			Pixel pixel = new Pixel(job);
			float v = (y + 0.5f) / size;
			for (int x = 0; x < size; x++) {
				pixel.shade((x + 0.5f) / size, v);
				color[y * size + x] = pixel.colorPixel();
				material[y * size + x] = pixel.materialPixel();
			}
		});
		return new Result(color, material);
	}

	/** The working memory for shading pixels; one per thread. */
	private static final class Pixel {
		private final PaintJob job;
		private final float wear;
		private final float[] ao = new float[4];
		private final float[] masks = new float[4];
		private final float[] base = new float[4];
		private final float[] baseMaterial = new float[4];
		private final float[] baseOcclusion = new float[4];
		private final float[] wearTexel = new float[4];
		private final float[] grunge = new float[4];
		private final float[] pattern = new float[4];
		private final float[] patternB = new float[4];
		private final float[] patternC = new float[4];
		private final float[] position = new float[4];
		private final float[] surface = new float[4];
		private final float[] roughnessTexel = new float[4];
		private final float[] paint = new float[3];
		private final float[] grime = new float[3];
		private final float[] color = new float[3];
		private final float[] layers = new float[3];
		private final float[] out = new float[3];
		/** Brightness and opacity of the dirt here, before any style changes {@link #grime}. */
		private float grimeLuma;
		private float grimeAlpha;
		private float outRoughness;
		private float outMetalness;

		Pixel(PaintJob job) {
			this.job = job;
			this.wear = Math.clamp(job.wearAmount, 0f, 1f);
		}

		int colorPixel() {
			return 0xFF000000 | (encode(out[0]) << 16) | (encode(out[1]) << 8) | encode(out[2]);
		}

		int materialPixel() {
			return 0xFF000000 | (toByte(outRoughness) << 16) | (toByte(outMetalness) << 8) | toByte(baseOcclusion[0]);
		}

		void shade(float u, float v) {
			job.ambientOcclusion.sample(u, v, ao);
			job.masks.sample(u, v, masks);
			job.baseColor.sample(u, v, base);
			job.baseMaterial.sample(u, v, baseMaterial);
			job.baseOcclusion.sample(u, v, baseOcclusion);
			job.wear.sample(job.wearTransform.u(u, v), job.wearTransform.v(u, v), Raster.WRAP, Raster.WRAP, wearTexel);
			job.grunge.sample(job.grungeTransform.u(u, v), job.grungeTransform.v(u, v), Raster.WRAP, Raster.WRAP,
					grunge);
			if (job.roughnessTexture != null) {
				PaintJob.Transform transform = job.patternTransform;
				job.roughnessTexture.sample(transform.u(u, v), transform.v(u, v), job.patternAddressU,
						job.patternAddressV, roughnessTexel);
			}

			float occlusion = ao[0];
			// Dirt gathers in the creases, and everywhere as the finish wears.
			float grimeAmount = pow(1f - occlusion, 4f) * 0.25f + 0.75f * wear;
			for (int i = 0; i < 3; i++) {
				grime[i] = mix(1f, grunge[i], grimeAmount);
			}
			grimeLuma = luma(grime);
			grimeAlpha = mix(1f, grunge[3], grimeAmount);
			// How readily the paint comes off here: sooner on exposed edges, and with more wear.
			float abrasion = (ao[3] + wearTexel[0] * occlusion) * (wear * 6f + 1f);

			samplePattern(u, v);
			float patternAlpha = pattern[3];
			// A band of pattern alpha marks where the paint is scratched through on purpose.
			float scratched = smoothstep(0.5f, 0.6f, patternAlpha) * smoothstep(1f, 0.9f, patternAlpha);

			switch (job.style) {
				case PaintJob.HYDROGRAPHIC -> layered(abrasion, scratched, true);
				case PaintJob.SPRAY -> layered(abrasion, scratched, false);
				case PaintJob.ANODIZED, PaintJob.ANODIZED_MULTICOLORED, PaintJob.ANODIZED_AIRBRUSHED ->
						anodized(abrasion, scratched);
				case PaintJob.CUSTOM -> custom(abrasion, scratched);
				case PaintJob.PATINA, PaintJob.GUNSMITH -> patina(scratched);
				default -> solid(abrasion);
			}
		}

		private void samplePattern(float u, float v) {
			PaintJob.Transform transform = job.patternTransform;
			if (job.style == PaintJob.SOLID || job.style == PaintJob.ANODIZED) {
				pattern[0] = pattern[1] = pattern[2] = pattern[3] = 1f;
			} else if (PaintJob.patternIsProjected(job.style)) {
				// Projected onto the knife along each of its three axes, like spraying it from three sides.
				job.position.sample(u, v, position);
				float px = position[0] * 2f;
				float py = position[1] * 2f;
				float pz = position[2] * 2f;
				job.surface.sample(u, v, surface);
				float nx = surface[0] * 2f - 1f;
				float ny = surface[1] * 2f - 1f;
				float nz = surface[2] * 2f - 1f;
				float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				if (length > 1e-6f) {
					ny /= length;
					nz /= length;
				}
				job.pattern.sample(transform.u(py, pz), transform.v(py, pz), job.patternAddressU, job.patternAddressV,
						pattern);
				job.pattern.sample(transform.u(px, pz), transform.v(px, pz), job.patternAddressU, job.patternAddressV,
						patternB);
				job.pattern.sample(transform.u(py, px), transform.v(py, px), job.patternAddressU, job.patternAddressV,
						patternC);
				float blendY = job.sprayBlendY * pow(Math.abs(ny), 7f);
				float blendZ = job.sprayBlendZ * pow(Math.abs(nz), 7f);
				for (int i = 0; i < 4; i++) {
					pattern[i] = mix(mix(pattern[i], patternB[i], blendY), patternC[i], blendZ);
				}
			} else {
				job.pattern.sample(transform.u(u, v), transform.v(u, v), job.patternAddressU, job.patternAddressV,
						pattern);
			}
		}

		/** How rough the paint itself is here, before dirt and wear. */
		private float paintRoughness(float first, float second, float third, float maskG, float maskB) {
			if (job.roughnessTexture != null) {
				return roughnessTexel[0];
			}
			if (job.roughnessPerColor) {
				return mix(mix(byMasks(job.paintRoughnessByColor, first, second, third), job.paintRoughnessByColor[2],
						maskG), job.paintRoughnessByColor[3], maskB);
			}
			return job.paintRoughness;
		}

		/** Paint that sits on top of the knife: worn paint gets rougher, and where it is gone the knife shows. */
		private void coatMaterial(float roughness, float metalness, float worn) {
			float worse = Math.min(1f, roughness + (1f - grimeAlpha) * wear * wear * 0.5f);
			outRoughness = mix(worse, baseMaterial[0], worn);
			outMetalness = mix(metalness, baseMaterial[1], worn);
		}

		/** One coat of paint, its colour chosen by the knife's own masks. */
		private void solid(float abrasion) {
			float durability = byMasks(job.durability, masks[0], masks[1], masks[2]);
			float softness = job.wearSoftness * durability;
			float worn = smoothstep(0.58f - softness, 0.68f + softness, abrasion * durability);
			colorByLayers(masks[0], masks[1], masks[2], 0f, 0f, paint);
			float metalness = byMasks(job.paintMetalnessByMask, masks[0], masks[1], masks[2]);
			finish(paint, luma(paint), metalness, grime, worn);
			coatMaterial(paintRoughness(masks[0], masks[1], masks[2], 0f, 0f), metalness, worn);
		}

		/**
		 * Up to three layers of paint over a base coat, each wearing through a little later than
		 * the one above it: hydrographic prints (which follow the texture layout) and spray paint
		 * (which is projected).
		 */
		private void layered(float abrasion, float scratched, boolean hydrographic) {
			float maskG = hydrographic ? masks[1] : 0f;
			float maskB = hydrographic ? masks[2] : 0f;
			float durability = mix(mix(byMasks(job.durability, pattern[0], pattern[1], pattern[2]),
					job.durability[2], maskG), job.durability[3], maskB);
			float exposure = hydrographic
					? (abrasion + scratched) * Math.max(saturate(maskG + maskB), smoothstep(0f, 0.5f, pattern[3]))
					: abrasion;
			exposure *= durability;
			float softness = job.wearSoftness * durability;
			float first = 0.56f - 0.06f * wear;
			float second = 0.54f - 0.12f * wear;
			float third = 0.52f - 0.18f * wear;
			layers[0] = pattern[0] * smoothstep(0.58f + softness, first - softness, exposure);
			layers[1] = pattern[1] * smoothstep(first + softness, second - softness, exposure);
			layers[2] = pattern[2] * smoothstep(second + softness, third - softness, exposure);
			float worn = smoothstep(0.58f - softness, 0.68f + softness, exposure);

			colorByLayers(layers[0], layers[1], layers[2], maskG, maskB, paint);
			float brightness = mix(job.colorBrightness, 1f, smoothstep(0f, 0.01f, worn) * (1f - scratched));
			for (int i = 0; i < 3; i++) {
				paint[i] = saturate(paint[i] * brightness);
			}
			float metalness = mix(mix(byMasks(job.paintMetalnessByMask, layers[0], layers[1], layers[2]),
					job.paintMetalnessByMask[2], maskG), job.paintMetalnessByMask[3], maskB);
			finish(paint, luma(paint), metalness, grime, worn);
			coatMaterial(paintRoughness(layers[0], layers[1], layers[2], maskG, maskB), metalness, worn);
		}

		/**
		 * A translucent dye over the knife's own metal, so the base texture shows through tinted.
		 * Where it wears, bare metal appears before the base does.
		 */
		private void anodized(float abrasion, float scratched) {
			float paintable = masks[0];
			float worn;
			float bare;
			// How rough the dyed surface is. The multicoloured kind keeps this in its pattern's alpha.
			float roughness = paintRoughness(0f, 0f, 0f, 0f, 0f);
			if (job.style == PaintJob.ANODIZED) {
				worn = smoothstep(0.58f - job.wearSoftness, 0.68f + job.wearSoftness, abrasion);
				bare = smoothstep(0f, 0.01f, worn) * paintable;
				paint[0] = job.colors[0][0];
				paint[1] = job.colors[0][1];
				paint[2] = job.colors[0][2];
			} else {
				float durability = mix(mix(byMasks(job.durability, pattern[0], pattern[1], pattern[2]),
						job.durability[2], masks[1]), job.durability[3], masks[2]);
				float softness = job.wearSoftness * durability;
				boolean multicolored = job.style == PaintJob.ANODIZED_MULTICOLORED;
				worn = smoothstep(0.58f - softness, 0.68f + softness,
						(abrasion + (multicolored ? scratched : 0f)) * durability);
				bare = smoothstep(0f, 0.01f, worn) * (1f - scratched) * paintable;
				float reach = multicolored ? paintable : 1f;
				colorByLayers(pattern[0] * reach, pattern[1] * reach, pattern[2] * reach, masks[1], masks[2], paint);
				if (multicolored && pattern[3] < 0.5f) {
					float gloss = 1f - Math.min(1f, pattern[3] * 2f);
					roughness = gloss * gloss * 0.85f + 0.15f;
				}
			}

			float showBase = Math.max(1f - paintable, worn);
			if (job.style != PaintJob.ANODIZED) {
				roughness = mix(roughness, paintRoughness(0f, 0f, 0f, 0f, 0f), Math.max(Math.max(masks[1], masks[2]),
						showBase));
			}
			float dyed = Math.min(1f, mix(roughness, BARE_METAL_ROUGHNESS, bare) + (1f - grimeLuma) * 0.2f);
			outRoughness = mix(baseMaterial[0], dyed, Math.max(0f, 1f - showBase));
			outMetalness = mix(paintable, baseMaterial[1], showBase);

			float brightness = mix(job.colorBrightness, 1f, bare);
			for (int i = 0; i < 3; i++) {
				float tinted = mix(mix(base[i], paint[i], paintable), BARE_METAL[i], bare);
				color[i] = saturate(saturate(tinted * brightness) * brightness);
				grime[i] = mix(grime[i], 1f, bare);
			}
			finish(color, luma(color), 1f, grime, showBase);
		}

		/** A picture painted onto the knife, following its texture layout. */
		private void custom(float abrasion, float scratched) {
			float durability = Math.max(0f, smoothstep(0f, 0.5f, pattern[3]));
			float softness = job.wearSoftness * durability;
			float worn = smoothstep(0.58f - softness, 0.68f + softness, (abrasion + scratched) * durability);
			for (int i = 0; i < 3; i++) {
				color[i] = saturate(pattern[i] * job.colorBrightness);
			}
			finish(color, luma(color), job.paintMetalness, grime, worn);
			coatMaterial(paintRoughness(0f, 0f, 0f, 0f, 0f), job.paintMetalness, worn);
		}

		/**
		 * Metal treated chemically rather than painted (patina), or a picture with such areas
		 * (gunsmith). These do not rub off; they tarnish, changing colour as they wear.
		 */
		private void patina(float scratched) {
			float occlusion = ao[0];
			float cavity = ao[1];
			float treated = masks[0];
			float polished = smoothstep(0.1f, 0.2f, wearTexel[0] * cavity * occlusion * occlusion * wear);
			float aged = smoothstep(0f, 0.15f, saturate(occlusion * cavity - wear * 0.1f)
					- saturate(grunge[0] * grunge[1] * grunge[2]) * 0.23f + 0.08f);
			float brighten = Math.max(treated, job.colorAdjustmentMode);
			for (int i = 0; i < 3; i++) {
				color[i] = mix(pattern[i], pattern[i] * job.colorBrightness, brighten);
			}
			float patternLuma = luma(color);

			float wearRoot = (float) Math.sqrt(wear);
			float[] c0 = job.colors[0];
			float[] c1 = job.colors[1];
			float[] c2 = job.colors[2];
			float[] c3 = job.colors[3];
			boolean gunsmith = job.style == PaintJob.GUNSMITH;
			float referenceLuma = 0f;
			for (int i = 0; i < 3; i++) {
				float tint = mix(mix(c1[i], c3[i], wearRoot), mix(c1[i], c2[i], wear), aged);
				float tarnished = mix(tint * color[i], c0[i] * patternLuma, polished);
				paint[i] = gunsmith ? mix(color[i], tarnished, treated) : tarnished;
				float reference = gunsmith ? mix(color[i], color[i] * c1[i], treated) : color[i] * c1[i];
				referenceLuma += reference * (i == 0 ? LUMA_R : i == 1 ? LUMA_G : LUMA_B);
			}

			// Treated metal stays metal, a little duller where it has aged and where dirt sits on it.
			float dirty = (1f - grimeAlpha) * wear;
			float treatedRoughness = paintRoughness(0f, 0f, 0f, 0f, 0f);
			if (gunsmith) {
				float pictureRoughness = treatedRoughness;
				if (pattern[3] < 0.5f) {
					float gloss = 1f - Math.min(1f, pattern[3] * 2f);
					pictureRoughness = gloss * gloss * 0.85f + 0.15f;
				}
				treatedRoughness = mix(treatedRoughness, pictureRoughness, treated);
			}
			float roughness = saturate(treatedRoughness * mix(1f, 0.9f, polished) + (1f - grimeLuma) * wear * 0.05f
					+ (1f - aged) * 0.15f * wear + dirty * 0.15f);
			float roughnessHere = mix(Math.min(1f, roughness + dirty * wear * 0.5f), roughness, treated);
			float treatedMetalness = mix(mix(1f, (float) Math.sqrt(saturate(aged * grimeAlpha * grimeLuma)), wear), 1f,
					polished);

			if (gunsmith) {
				float untreated = 1f - treated;
				float durability = Math.max(0f, smoothstep(0f, 0.5f, pattern[3]));
				float softness = job.wearSoftness * durability;
				float exposure = ((Math.min(ao[3], untreated) + wearTexel[0] * occlusion) * (wear * 6f + 1f) + scratched)
						* durability;
				float worn = treated > 0.99f ? exposure : smoothstep(0.58f - softness, 0.68f + softness, exposure);
				finish(paint, referenceLuma, mix(job.paintMetalness, 1f, treated), grime, saturate(worn * untreated));
				outRoughness = mix(baseMaterial[0], roughnessHere, treated > 0.99f ? 1f : saturate(1f - worn));
				outMetalness = mix(mix(job.paintMetalness, baseMaterial[1], saturate(worn)), treatedMetalness, treated);
			} else {
				finish(paint, referenceLuma, 1f, grime, 1f - treated);
				// Alpha of the occlusion texture at its maximum marks areas the treatment never reaches.
				outRoughness = mix(baseMaterial[0], roughnessHere, ao[3] <= 0.996f ? treated : 0f);
				outMetalness = mix(baseMaterial[1], treatedMetalness, treated);
			}
		}

		/**
		 * The last steps every style shares: dirt, the loss of contrast that comes with wear, and
		 * the unpainted knife showing wherever the finish is gone.
		 *
		 * @param paintColor the finish's colour here, before dirt
		 * @param referenceLuma brightness of the clean finish, which limits how dark wear may make it
		 * @param showBase how much of the unpainted knife shows, 0 to 1
		 */
		private void finish(float[] paintColor, float referenceLuma, float metalness, float[] dirt, float showBase) {
			float r = paintColor[0] * dirt[0];
			float g = paintColor[1] * dirt[1];
			float b = paintColor[2] * dirt[2];

			float floor = mix(PAINT_LEVELS[0], METALLIC_LEVELS[0], metalness);
			float curve = mix(PAINT_LEVELS[1], METALLIC_LEVELS[1], metalness);
			float ceiling = mix(PAINT_LEVELS[2], METALLIC_LEVELS[2], metalness);
			float nr = Math.max(0.0003f, r);
			float ng = Math.max(0.0003f, g);
			float nb = Math.max(0.0003f, b);
			float brightest = Math.max(nr, Math.max(ng, nb));
			float level = mix(Math.min(floor, referenceLuma), ceiling,
					saturate(pow(Math.max(r, Math.max(g, b)), curve)));
			// Same hue, brightness pulled towards the allowed range: worn finishes look washed out.
			float wr = nr / brightest * level;
			float wg = ng / brightest * level;
			float wb = nb / brightest * level;

			out[0] = mix(mix(r, wr, wear), base[0], showBase);
			out[1] = mix(mix(g, wg, wear), base[1], showBase);
			out[2] = mix(mix(b, wb, wear), base[2], showBase);
		}

		/** The finish's four colours mixed by three layer strengths, then by two of the knife's masks. */
		private void colorByLayers(float first, float second, float third, float maskG, float maskB, float[] result) {
			for (int i = 0; i < 3; i++) {
				float value = mix(mix(mix(job.colors[0][i], job.colors[1][i], first), job.colors[2][i], second),
						job.colors[3][i], third);
				result[i] = mix(mix(value, job.colors[2][i], maskG), job.colors[3][i], maskB);
			}
		}

		private static float byMasks(float[] values, float first, float second, float third) {
			return mix(mix(mix(values[0], values[1], first), values[2], second), values[3], third);
		}
	}

	private static float luma(float[] color) {
		return color[0] * LUMA_R + color[1] * LUMA_G + color[2] * LUMA_B;
	}

	private static float mix(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float saturate(float value) {
		return value < 0f ? 0f : Math.min(value, 1f);
	}

	private static float pow(float base, float exponent) {
		return base <= 0f ? 0f : (float) Math.pow(base, exponent);
	}

	/** Smooth 0-to-1 step between two edges; the edges may be given high-to-low for a falling step. */
	private static float smoothstep(float from, float to, float value) {
		if (from == to) {
			return value < from ? 0f : 1f;
		}
		float t = saturate((value - from) / (to - from));
		return t * t * (3f - 2f * t);
	}

	/** Linear light to an 8-bit sRGB channel. */
	private static int encode(float linear) {
		float value = saturate(linear);
		float srgb = value <= 0.0031308f ? value * 12.92f : 1.055f * (float) Math.pow(value, 1.0 / 2.4) - 0.055f;
		return Math.clamp(Math.round(srgb * 255f), 0, 255);
	}

	private static int toByte(float unit) {
		return Math.clamp(Math.round(saturate(unit) * 255f), 0, 255);
	}
}
