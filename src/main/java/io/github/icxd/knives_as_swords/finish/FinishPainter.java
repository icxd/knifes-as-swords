package io.github.icxd.knives_as_swords.finish;

import io.github.icxd.knives_as_swords.cs2.Kv3;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns "this knife, this finish, this pattern seed, this much wear" into the knife's colour
 * texture, gathering the finish's settings and textures from the game files on the way.
 */
public final class FinishPainter {
	/** Where the compiled resources and textures come from; implemented with the external extractor. */
	public interface Resources {
		/** The data block of compiled resources as KeyValues3 text, by path; missing ones are left out. */
		Map<String, String> dataBlocks(List<String> paths) throws IOException, InterruptedException;

		/** Compiled textures converted to image files, by path; missing ones are left out. */
		Map<String, Path> textures(List<String> paths) throws IOException, InterruptedException;
	}

	/**
	 * A painted knife, as two images of the same size, row by row from the top.
	 *
	 * @param size width and height in pixels
	 * @param color opaque ARGB pixels: what the knife looks like
	 * @param material per pixel, in the red, green and blue bytes: roughness, metalness and
	 *     ambient occlusion, each 0 to 255
	 */
	public record Painted(int size, int[] color, int[] material) {
	}

	private static final String COMPILED = "_c";
	/**
	 * Decoded textures that were used recently. Trying out patterns and wear repaints the same
	 * knife with the same finish again and again, and decoding its textures takes longer than
	 * the painting does. Softly held: memory comes first.
	 */
	private static final int REMEMBERED_TEXTURES = 16;
	private static final Map<String, SoftReference<Raster>> TEXTURES = new LinkedHashMap<>(32, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, SoftReference<Raster>> eldest) {
			return size() > REMEMBERED_TEXTURES;
		}
	};
	/** The size the game's "weapon length" setting is relative to. */
	private static final float REFERENCE_WEAPON_LENGTH = 36f;

	private FinishPainter() {
	}

	/**
	 * @param knifeMaterial the knife's own material, e.g. {@code weapons/.../weapon_knife_karambit.vmat}
	 * @param compositePath the finish's compiled composite material inside the main package
	 * @param seed pattern seed; decides where the pattern sits on the knife
	 * @param wear 0 (factory new) to 1 (battle-scarred)
	 * @param size width and height of the result in pixels
	 */
	public static Painted paint(Resources resources, String knifeMaterial, String compositePath, int seed,
			float wear, int size) throws IOException, InterruptedException {
		String knifeMaterialFile = compiled(knifeMaterial);
		Map<String, String> first = resources.dataBlocks(List.of(knifeMaterialFile, compositePath));
		Material knife = Material.parse(require(first, knifeMaterialFile));
		Composite composite = Composite.parse(require(first, compositePath));
		if (composite.paintMaterial == null) {
			throw new IOException("The finish " + compositePath + " names no paint material");
		}

		// The knife's material points at a second one holding everything a finish needs to know about it.
		String inputsFile = knife.strings.get("composite_inputs");
		String paintFile = compiled(composite.paintMaterial);
		List<String> second = new ArrayList<>(List.of(paintFile));
		if (inputsFile != null) {
			inputsFile = compiled(inputsFile);
			second.add(inputsFile);
		}
		Map<String, String> more = resources.dataBlocks(second);
		Material paint = Material.parse(require(more, paintFile));
		Material inputs = inputsFile != null && more.containsKey(inputsFile) ? Material.parse(more.get(inputsFile))
				: knife;

		PaintJob job = new PaintJob();
		job.style = (int) paint.number("F_PAINT_STYLE", PaintJob.SOLID);
		if (job.style < PaintJob.SOLID || job.style > PaintJob.GUNSMITH) {
			throw new IOException("Unknown paint style " + job.style + " in " + paintFile);
		}
		job.wearAmount = wear;
		settings(job, paint, inputs, composite, seed);
		textures(job, resources, paint, inputs, knife, composite, size);
		FinishCompositor.Result result = FinishCompositor.compose(job, size);
		return new Painted(size, result.color(), result.material());
	}

	private static void settings(PaintJob job, Material paint, Material inputs, Composite composite, int seed) {
		for (int i = 0; i < 4; i++) {
			float[] color = paint.vectors.get("g_vColor" + i);
			if (color != null && color.length >= 3) {
				for (int k = 0; k < 3; k++) {
					job.colors[i][k] = srgbToLinear(color[k]);
				}
			}
		}
		job.colorBrightness = paint.number("g_flColorBrightness", 1f);
		job.wearSoftness = paint.number("g_fWearSoftness", 0f);
		job.paintMetalness = paint.number("g_flPaintMetalness", 0f);
		job.colorAdjustmentMode = (int) paint.number("g_nColorAdjustmentMode", 1f);
		float[] durability = paint.vectors.get("g_vPaintDurability");
		float[] metalness = paint.vectors.get("g_vPaintMetalness");
		for (int i = 0; i < 4; i++) {
			// The game's setting says how much sooner a layer wears; the recipe works with what is left.
			job.durability[i] = durability != null && durability.length > i ? 1f - durability[i] : 1f;
			job.paintMetalnessByMask[i] = metalness != null && metalness.length > i ? metalness[i] : 0f;
		}
		float[] sprayBlend = paint.vectors.get("g_vSprayBlend");
		if (sprayBlend != null && sprayBlend.length >= 2) {
			job.sprayBlendY = sprayBlend[0];
			job.sprayBlendZ = sprayBlend[1];
		}
		// Glossier by default where the dye is the whole point.
		job.paintRoughness = paint.number("g_flPaintRoughness",
				job.style == PaintJob.ANODIZED_MULTICOLORED ? 0.2f : 0.6f);
		job.roughnessPerColor = paint.number("F_ROUGHNESS_PER_COLOR", 0f) != 0f;
		float[] roughness = paint.vectors.get("g_vPaintRoughness");
		for (int i = 0; i < 4; i++) {
			job.paintRoughnessByColor[i] = roughness != null && roughness.length > i ? roughness[i] : 0.6f;
		}
		job.patternAddressU = addressMode(paint.number("g_nPatternTextureHorizontalSampling", 0f));
		job.patternAddressV = addressMode(paint.number("g_nPatternTextureVerticalSampling", 0f));

		// Patterns are sized for a reference weapon and scaled to this one, unless the finish says to
		// ignore the weapon's size. (Checked against the game's own pictures of finished knives: a web or
		// camouflage pattern comes out the right size this way, and clearly too fine without the scaling.)
		float sizeScale;
		if (paint.number("g_bIgnoreWeaponSizeScale", 0f) != 0f) {
			sizeScale = 1f;
		} else if (PaintJob.patternIsProjected(job.style)) {
			sizeScale = inputs.number("g_flWeaponLength1", REFERENCE_WEAPON_LENGTH) / REFERENCE_WEAPON_LENGTH;
		} else {
			sizeScale = inputs.number("g_flUvScale1", 1f);
		}

		// The seed decides everything the finish leaves open: where the pattern sits and how it is turned,
		// and the same for the wear and dirt textures.
		UniformRandom random = new UniformRandom(seed);
		float[] patternOffset = paint.vectors.getOrDefault("g_vPatternTexCoordOffset", new float[] {0f, 0f});
		float[][] bounds = {
				range(composite, "g_vPatternTexCoordOffset.x", patternOffset.length > 0 ? patternOffset[0] : 0f),
				range(composite, "g_vPatternTexCoordOffset.y", patternOffset.length > 1 ? patternOffset[1] : 0f),
				range(composite, "g_flPatternTexCoordRotation", paint.number("g_flPatternTexCoordRotation", 0f))};
		float[] placement = placePattern(random, bounds[0], bounds[1], bounds[2]);
		job.patternTransform = PaintJob.Transform.of(placement[2],
				paint.number("g_flPatternTexCoordScale", 1f) * sizeScale, placement[0], placement[1]);

		float wearScale = random.between(1.6f, 1.8f);
		float wearU = random.between(0f, 1f);
		float wearV = random.between(0f, 1f);
		float wearRotation = random.between(0f, 360f);
		job.wearTransform = PaintJob.Transform.of(wearRotation, wearScale * sizeScale, wearU, wearV);
		float grungeScale = random.between(1.6f, 1.8f);
		float grungeU = random.between(0f, 1f);
		float grungeV = random.between(0f, 1f);
		float grungeRotation = random.between(0f, 360f);
		job.grungeTransform = PaintJob.Transform.of(grungeRotation, grungeScale * sizeScale, grungeU, grungeV);
	}

	/**
	 * Where a seed puts the pattern: its offset along u, its offset along v, and its rotation in
	 * degrees, each picked from a span given as {@code {from, to}}.
	 *
	 * <p>Always these three, in this order, whether or not the finish varies them: that is what
	 * makes a seed look the way players know it. Checked with a well-known one: Case Hardened 387
	 * on the karambit comes out with one face all blue.
	 */
	static float[] placePattern(UniformRandom random, float[] offsetU, float[] offsetV, float[] rotation) {
		float u = random.between(offsetU[0], offsetU[1]);
		float v = random.between(offsetV[0], offsetV[1]);
		float turned = random.between(rotation[0], rotation[1]);
		return new float[] {u, v, turned};
	}

	/** The span the seed picks a setting from; a setting the finish does not vary is a span of one value. */
	private static float[] range(Composite composite, String name, float fixed) {
		for (Composite.Range range : composite.ranges) {
			if (range.name().equals(name)) {
				return new float[] {range.min(), range.max()};
			}
		}
		return new float[] {fixed, fixed};
	}

	private static void textures(PaintJob job, Resources resources, Material paint, Material inputs, Material knife,
			Composite composite, int size) throws IOException, InterruptedException {
		// A finish may bring its own masks instead of using the knife's.
		String masks = composite.overrideMasks && paint.textures.containsKey("g_tMasks")
				? paint.textures.get("g_tMasks") : inputs.textures.get("g_tMasks");
		String baseColor = inputs.textures.getOrDefault("g_tColor", knife.textures.get("g_tColor"));
		String baseMaterial = inputs.textures.getOrDefault("g_tMetalness", knife.textures.get("g_tMetalness"));
		// A finish may map its roughness instead of giving one value. (Assumed, not seen in the shader:
		// that the map simply stands in for the value, laid out like the pattern.)
		String roughness = paint.number("F_ROUGHNESS_TEXTURE", 0f) != 0f ? paint.textures.get("g_tPaintRoughness")
				: null;
		// Two textures of this name: the knife's own is for shading, the paint's for dirt and wear.
		String baseOcclusion = knife.textures.get("g_tAmbientOcclusion");
		String ambientOcclusion = inputs.textures.get("g_tAmbientOcclusion");
		String position = inputs.textures.get("g_tPosition");
		String surface = inputs.textures.get("g_tSurface");
		String pattern = paint.textures.get("g_tPattern");
		String wear = paint.textures.get("g_tWear");
		String grunge = paint.textures.get("g_tGrunge");

		boolean projected = PaintJob.patternIsProjected(job.style);
		boolean patterned = job.style != PaintJob.SOLID && job.style != PaintJob.ANODIZED;
		List<String> wanted = new ArrayList<>();
		for (String texture : new String[] {masks, baseColor, baseMaterial, baseOcclusion, ambientOcclusion,
				projected ? position : null, projected ? surface : null, patterned ? pattern : null, roughness, wear,
				grunge}) {
			if (texture != null) {
				wanted.add(compiled(texture));
			}
		}
		Map<String, Path> files = resources.textures(wanted);

		// Textures laid out like the result are never needed finer than the result.
		job.baseColor = load(files, baseColor, true, size, job.baseColor);
		job.baseMaterial = load(files, baseMaterial, false, size, job.baseMaterial);
		job.baseOcclusion = load(files, baseOcclusion, false, size, job.baseOcclusion);
		job.ambientOcclusion = load(files, ambientOcclusion, true, size, job.ambientOcclusion);
		job.masks = load(files, masks, false, size, job.masks);
		if (projected) {
			job.position = load(files, position, false, size, job.position);
			job.surface = load(files, surface, false, size, job.surface);
		}
		if (patterned) {
			job.pattern = load(files, pattern, PaintJob.patternIsColor(job.style), 0, job.pattern);
		}
		job.roughnessTexture = load(files, roughness, false, 0, null);
		job.wear = load(files, wear, false, 0, job.wear);
		job.grunge = load(files, grunge, true, 0, job.grunge);
	}

	/**
	 * @param shrinkTo longest side the image is needed at, or 0 to keep it as it is
	 */
	private static Raster load(Map<String, Path> files, String texture, boolean srgb, int shrinkTo, Raster fallback)
			throws IOException {
		if (texture == null) {
			return fallback;
		}
		Path file = files.get(compiled(texture));
		if (file == null) {
			return fallback;
		}
		String key = file + "|" + srgb + "|" + shrinkTo;
		synchronized (TEXTURES) {
			SoftReference<Raster> remembered = TEXTURES.get(key);
			Raster raster = remembered == null ? null : remembered.get();
			if (raster != null) {
				return raster;
			}
		}
		Raster raster = Raster.load(file, srgb);
		if (shrinkTo > 0) {
			raster = raster.shrunkTo(shrinkTo);
		}
		synchronized (TEXTURES) {
			TEXTURES.put(key, new SoftReference<>(raster));
		}
		return raster;
	}

	private static String require(Map<String, String> blocks, String path) throws IOException {
		String text = blocks.get(path);
		if (text == null) {
			throw new IOException("The game files do not contain " + path);
		}
		return text;
	}

	private static String compiled(String resource) {
		return resource.endsWith(COMPILED) ? resource : resource + COMPILED;
	}

	/** The game's numbers for wrap (0), mirror (1) and clamp (2); anything else is treated as clamp. */
	private static int addressMode(float value) {
		int mode = (int) value;
		return mode == Raster.WRAP || mode == Raster.MIRROR ? mode : Raster.CLAMP;
	}

	private static float srgbToLinear(float value) {
		float clamped = Math.clamp(value, 0f, 1f);
		return clamped <= 0.04045f ? clamped / 12.92f : (float) Math.pow((clamped + 0.055) / 1.055, 2.4);
	}

	/** The settings of one compiled material. */
	private static final class Material {
		final Map<String, Float> numbers = new HashMap<>();
		final Map<String, float[]> vectors = new HashMap<>();
		final Map<String, String> textures = new HashMap<>();
		final Map<String, String> strings = new HashMap<>();

		static Material parse(String kv3) throws IOException {
			Map<String, Object> root;
			try {
				root = Kv3.parse(kv3);
			} catch (IllegalArgumentException e) {
				throw new IOException("Could not read a material: " + e.getMessage(), e);
			}
			Material material = new Material();
			for (Object entry : Kv3.asList(root.get("m_intParams"))) {
				Map<String, Object> item = Kv3.asObject(entry);
				material.numbers.put(Kv3.asString(item.get("m_name"), ""), (float) Kv3.asNumber(item.get("m_nValue"), 0));
			}
			for (Object entry : Kv3.asList(root.get("m_floatParams"))) {
				Map<String, Object> item = Kv3.asObject(entry);
				material.numbers.put(Kv3.asString(item.get("m_name"), ""),
						(float) Kv3.asNumber(item.get("m_flValue"), 0));
			}
			for (Object entry : Kv3.asList(root.get("m_vectorParams"))) {
				Map<String, Object> item = Kv3.asObject(entry);
				List<Object> values = Kv3.asList(item.get("m_value"));
				float[] vector = new float[values.size()];
				for (int i = 0; i < vector.length; i++) {
					vector[i] = (float) Kv3.asNumber(values.get(i), 0);
				}
				material.vectors.put(Kv3.asString(item.get("m_name"), ""), vector);
			}
			for (Object entry : Kv3.asList(root.get("m_textureParams"))) {
				Map<String, Object> item = Kv3.asObject(entry);
				String path = Kv3.asString(item.get("m_pValue"), "");
				if (!path.isEmpty()) {
					material.textures.put(Kv3.asString(item.get("m_name"), ""), path);
				}
			}
			for (Object entry : Kv3.asList(root.get("m_stringAttributes"))) {
				Map<String, Object> item = Kv3.asObject(entry);
				material.strings.put(Kv3.asString(item.get("m_name"), ""), Kv3.asString(item.get("m_value"), ""));
			}
			return material;
		}

		float number(String name, float fallback) {
			Float value = numbers.get(name);
			return value != null && Float.isFinite(value) ? value : fallback;
		}
	}

	/** What a finish's composite material adds to its paint material. */
	private static final class Composite {
		/** A setting the pattern seed picks a value for. Vector settings are named {@code name.x}, {@code name.y}. */
		record Range(String name, float min, float max) {
		}

		String paintMaterial;
		boolean overrideMasks;
		final List<Range> ranges = new ArrayList<>();

		static Composite parse(String kv3) throws IOException {
			Map<String, Object> root;
			try {
				root = Kv3.parse(kv3);
			} catch (IllegalArgumentException e) {
				throw new IOException("Could not read a finish: " + e.getMessage(), e);
			}
			Composite composite = new Composite();
			for (Object point : Kv3.asList(root.get("m_Points"))) {
				for (Object procedure : Kv3.asList(Kv3.asObject(point).get("m_vecCompositeMaterialAssemblyProcedures"))) {
					for (Object entry : Kv3.asList(Kv3.asObject(procedure).get("m_vecCompositeInputContainers"))) {
						composite.container(Kv3.asObject(entry));
					}
				}
			}
			return composite;
		}

		private void container(Map<String, Object> container) {
			String alias = Kv3.asString(container.get("m_strAlias"), "");
			if (alias.equals("paint")) {
				String material = Kv3.asString(container.get("m_strSpecificContainerMaterial"), "");
				if (!material.isEmpty()) {
					paintMaterial = material;
				}
			}
			for (Object entry : Kv3.asList(container.get("m_vecLooseVariables"))) {
				Map<String, Object> variable = Kv3.asObject(entry);
				String name = Kv3.asString(variable.get("m_strName"), "");
				String type = Kv3.asString(variable.get("m_nVariableType"), "");
				if (alias.equals("override_vars") && name.equals("g_bOverrideMasks")) {
					overrideMasks = Kv3.asNumber(variable.get("m_bValueBoolean"), 0) != 0;
				}
				if (Kv3.asNumber(variable.get("m_bHasFloatBounds"), 0) == 0) {
					continue;
				}
				String[] parts = switch (type) {
					case "LOOSE_VARIABLE_TYPE_FLOAT1" -> new String[] {"X"};
					case "LOOSE_VARIABLE_TYPE_FLOAT2" -> new String[] {"X", "Y"};
					default -> new String[0];
				};
				for (String part : parts) {
					float value = (float) Kv3.asNumber(variable.get("m_flValueFloat" + part), 0);
					float min = (float) Kv3.asNumber(variable.get("m_flValueFloat" + part + "_Min"), value);
					float max = (float) Kv3.asNumber(variable.get("m_flValueFloat" + part + "_Max"), value);
					String rangeName = parts.length == 1 ? name : name + "." + part.toLowerCase(java.util.Locale.ROOT);
					ranges.add(new Range(rangeName, min, max));
				}
			}
		}
	}
}
