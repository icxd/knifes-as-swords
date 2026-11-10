package io.github.icxd.knives_as_swords.finish;

/**
 * Everything the compositor needs to paint one knife with one finish: the knife's own textures,
 * the finish's textures, and the finish's settings with the pattern seed already applied.
 *
 * <p>Colours are in linear light. Texture coordinates have v pointing down, as in image files.
 */
final class PaintJob {
	/** The paint styles, numbered as the game's paint shader numbers them. */
	static final int SOLID = 0;
	static final int HYDROGRAPHIC = 1;
	static final int SPRAY = 2;
	static final int ANODIZED = 3;
	static final int ANODIZED_MULTICOLORED = 4;
	static final int ANODIZED_AIRBRUSHED = 5;
	static final int CUSTOM = 6;
	static final int PATINA = 7;
	static final int GUNSMITH = 8;

	/**
	 * A rotation, scale and shift of texture coordinates:
	 * {@code u' = u * ux + v * uy + uw} and {@code v' = u * vx + v * vy + vw}.
	 */
	record Transform(float ux, float uy, float uw, float vx, float vy, float vw) {
		static final Transform IDENTITY = new Transform(1f, 0f, 0f, 0f, 1f, 0f);

		/**
		 * The transform the paint shader builds from a rotation in degrees, a scale and an offset:
		 * rotating and scaling about the middle of the texture, then shifting.
		 */
		static Transform of(float rotationDegrees, float scale, float offsetU, float offsetV) {
			// The game rounds all of these to two decimals before using them.
			double rotation = Math.toRadians(round(rotationDegrees));
			double s = round(scale);
			double cos = Math.cos(rotation);
			double sin = Math.sin(rotation);
			double half = 0.5 / (s != 0.0 ? s : 1.0);
			double a = half * cos + half * sin;
			double b = -a * sin + half * cos;
			return new Transform(
					(float) (cos * s), (float) (-sin * s),
					(float) (s * cos * a - s * sin * b + (round(offsetU) - 0.5)),
					(float) (sin * s), (float) (cos * s),
					(float) (s * sin * a + s * cos * b + (round(offsetV) - 0.5)));
		}

		private static double round(float value) {
			return Math.floor((value + 0.005) * 100.0) / 100.0;
		}

		float u(float u, float v) {
			return u * ux + v * uy + uw;
		}

		float v(float u, float v) {
			return u * vx + v * vy + vw;
		}
	}

	// --- the knife ---
	/** The unpainted knife's colour. */
	Raster baseColor = Raster.constant(0.5f, 0.5f, 0.5f, 1f);
	/** The unpainted knife's surface. Red: roughness. Green: metalness. */
	Raster baseMaterial = Raster.constant(0.5f, 0f, 0f, 1f);
	/**
	 * The unpainted knife's ambient occlusion, in red: how much light reaches each spot. This is what
	 * the knife is shaded with, painted or not.
	 */
	Raster baseOcclusion = Raster.constant(1f, 1f, 1f, 1f);
	/**
	 * What the paint goes by. Red: how deep a spot lies, for dirt and wear. Green: cavities. Alpha: where
	 * paint wears off first. Despite its name in the game files this is not for shading: on some knives
	 * its red is a dull grey all over.
	 */
	Raster ambientOcclusion = Raster.constant(1f, 1f, 1f, 0f);
	/** Which areas take paint, and which areas take which of the finish's colours. */
	Raster masks = Raster.constant(1f, 0f, 0f, 1f);
	/** Where each point of the texture is on the knife, for patterns projected onto it. */
	Raster position = Raster.constant(0f, 0f, 0f, 1f);
	/** Which way each point of the texture faces on the knife. */
	Raster surface = Raster.constant(0.5f, 0.5f, 1f, 1f);

	// --- the finish ---
	int style = SOLID;
	Raster pattern = Raster.constant(1f, 1f, 1f, 1f);
	Raster wear = Raster.constant(0.5f, 0.5f, 0.5f, 1f);
	Raster grunge = Raster.constant(1f, 1f, 1f, 1f);
	final float[][] colors = {{0.5f, 0.5f, 0.5f}, {0.59f, 0.59f, 0.59f}, {0.38f, 0.38f, 0.38f}, {0.42f, 0.42f, 0.42f}};
	float colorBrightness = 1f;
	/** How well each colour layer resists wear, 1 being normal; already "one minus" the game's setting. */
	final float[] durability = {1f, 1f, 1f, 1f};
	float wearSoftness;
	float paintMetalness;
	final float[] paintMetalnessByMask = {0f, 0f, 0f, 0f};
	/** How rough the paint is: one value, or one per colour, or a texture laid out like the pattern. */
	float paintRoughness = 0.6f;
	boolean roughnessPerColor;
	final float[] paintRoughnessByColor = {0.6f, 0.6f, 0.6f, 0.6f};
	Raster roughnessTexture;
	/** Whether the brightness setting applies to all paint (1) or only where the mask says "metallic" (0). */
	int colorAdjustmentMode = 1;
	/** How strongly the projections along the knife's other two axes are blended in. */
	float sprayBlendY = 1f;
	float sprayBlendZ = 1f;
	int patternAddressU = Raster.WRAP;
	int patternAddressV = Raster.WRAP;
	Transform patternTransform = Transform.IDENTITY;
	Transform wearTransform = Transform.IDENTITY;
	Transform grungeTransform = Transform.IDENTITY;

	// --- this particular knife ---
	/** 0 (factory new) to 1 (battle-scarred). */
	float wearAmount;

	/** Whether this style's pattern texture holds colours (as opposed to masks for the four paint colours). */
	static boolean patternIsColor(int style) {
		return style == CUSTOM || style == PATINA || style == GUNSMITH;
	}

	/** Whether this style projects its pattern onto the knife instead of following the texture layout. */
	static boolean patternIsProjected(int style) {
		return style == SPRAY || style == ANODIZED_AIRBRUSHED;
	}
}
