package io.github.icxd.knives_as_swords.fx;

import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Small bolts of lightning that crawl over a blade and leap off it.
 *
 * <p>There is nothing to remember between frames: which bolts exist at a moment, where they run
 * and how they wriggle all follow from the time and a seed. Two knives with different seeds
 * crackle independently, and the same knife looks the same wherever it is drawn in one frame.
 */
public final class ArcBolts {
	/** Points per bolt. */
	private static final int POINTS = 9;
	private static final int MAX_BOLTS = 8;
	/** The part of its time slot during which a bolt is lit; the rest is the dark before the next. */
	private static final float LIT = 0.75f;
	/** How often a bolt changes its path while it is lit. */
	private static final float WRIGGLES = 5f;

	/** Client thread only. */
	private static final float[] SCRATCH = new float[POINTS * 3];
	private static final Vector3f FROM = new Vector3f();
	private static final Vector3f TO = new Vector3f();
	private static final Vector3f SPAN = new Vector3f();
	private static final Vector3f SIDE = new Vector3f();
	private static final Vector3f UP = new Vector3f();
	private static final Vector3f POINT = new Vector3f();

	private ArcBolts() {
	}

	/**
	 * Adds the bolts of this moment to {@code out}.
	 *
	 * @param transform moves the blade from its rest pose to where it is now; it must not change
	 *     sizes. {@code null} leaves the bolts in the model's own space
	 * @param time seconds, on any steadily running clock
	 * @param seed tells this knife's bolts apart from another's
	 * @param density how many bolts there may be at once, 0 to 1
	 * @param energy how excited the blade is, 0 (resting: the odd bolt) to 1 (crackling all over)
	 * @param strength overall visibility, 0 to 1
	 * @return how many bolts were added
	 */
	public static int build(FxBatch out, BladeShape blade, @Nullable Matrix4fc transform, float time, int seed,
			float density, float energy, int argb, float heat, float roughness, float strength) {
		if (density <= 0f || strength <= 0f || blade.anchorCount() == 0) {
			return 0;
		}
		int slots = Math.clamp(Math.round(1f + density * (MAX_BOLTS - 1)), 1, MAX_BOLTS);
		float chance = Math.clamp(density * (0.12f + 0.88f * Math.clamp(energy, 0f, 1f)), 0f, 1f);
		int anchors = blade.anchorCount();
		float[] anchorPoints = blade.anchors();
		float[] anchorNormals = blade.anchorNormals();
		int drawn = 0;

		for (int slot = 0; slot < slots; slot++) {
			// Every slot keeps its own time: bolts neither appear nor vanish in step.
			float period = 0.10f + 0.16f * FxMath.unit(FxMath.hash(slot, seed, 1));
			float position = time / period + FxMath.unit(FxMath.hash(slot, seed, 2));
			int cycle = (int) Math.floor(position);
			float t = position - cycle;
			int bolt = FxMath.hash(slot, seed, cycle);
			if (t > LIT || FxMath.unit(FxMath.hash(bolt, 3)) >= chance) {
				continue;
			}

			int a = Math.floorMod(FxMath.hash(bolt, 4), anchors);
			FROM.set(anchorPoints, a * 3);
			float nx = anchorNormals[a * 3], ny = anchorNormals[a * 3 + 1], nz = anchorNormals[a * 3 + 2];
			float lift = blade.length() * 0.02f;
			FROM.add(nx * lift, ny * lift, nz * lift);

			boolean leaps = anchors < 2 || FxMath.unit(FxMath.hash(bolt, 5)) < 0.3f;
			if (!leaps) {
				int b = Math.floorMod(a + 1 + Math.floorMod(FxMath.hash(bolt, 6), anchors - 1), anchors);
				TO.set(anchorPoints, b * 3);
				TO.add(anchorNormals[b * 3] * lift, anchorNormals[b * 3 + 1] * lift, anchorNormals[b * 3 + 2] * lift);
				// Two points almost on top of each other make no bolt worth seeing.
				leaps = TO.distance(FROM) < blade.length() * 0.12f;
			}
			if (leaps) {
				// Off the blade into the air: mostly away from the surface, a little any which way.
				float reach = blade.length() * (0.25f + 0.35f * FxMath.unit(FxMath.hash(bolt, 7)));
				TO.set(nx + 0.8f * FxMath.signed(FxMath.hash(bolt, 8)), ny + 0.8f * FxMath.signed(FxMath.hash(bolt, 9)),
						nz + 0.8f * FxMath.signed(FxMath.hash(bolt, 10)));
				if (TO.lengthSquared() < 1e-6f) {
					TO.set(1f, 0f, 0f);
				}
				TO.normalize(reach).add(FROM);
			}

			TO.sub(FROM, SPAN);
			float length = SPAN.length();
			if (length < 1e-6f) {
				continue;
			}
			// Two directions at right angles to the bolt, for it to wriggle in.
			SIDE.set(SPAN).cross(nx, ny, nz);
			if (SIDE.lengthSquared() < 1e-10f) {
				SIDE.set(SPAN).cross(0f, 0f, 1f);
				if (SIDE.lengthSquared() < 1e-10f) {
					SIDE.set(SPAN).cross(1f, 0f, 0f);
				}
			}
			SIDE.normalize();
			UP.set(SPAN).cross(SIDE).normalize();

			int wriggle = FxMath.hash(bolt, 11 + (int) (t / LIT * WRIGGLES));
			float sway = length * 0.22f * (0.5f + roughness);
			for (int k = 0; k < POINTS; k++) {
				float along = k / (float) (POINTS - 1);
				// Pinned where it touches the blade; a bolt into the air is free at its far end.
				float free = (float) Math.sin(Math.PI * (leaps ? along * 0.5f : along));
				float sideways = FxMath.signed(FxMath.hash(wriggle, k, 1)) * sway * free;
				float upwards = FxMath.signed(FxMath.hash(wriggle, k, 2)) * sway * free;
				POINT.set(SPAN).mul(along).add(FROM).add(SIDE.x * sideways + UP.x * upwards,
						SIDE.y * sideways + UP.y * upwards, SIDE.z * sideways + UP.z * upwards);
				if (transform != null) {
					transform.transformPosition(POINT);
				}
				SCRATCH[k * 3] = POINT.x;
				SCRATCH[k * 3 + 1] = POINT.y;
				SCRATCH[k * 3 + 2] = POINT.z;
			}

			// Brightest as it strikes, dying away, and flickering all the while.
			float brightness = strength * (float) Math.pow(1f - t / LIT, 0.6)
					* (0.65f + 0.35f * FxMath.unit(FxMath.hash(wriggle, 12)));
			out.polyline(SCRATCH, POINTS, blade.length() * 0.05f, FxMath.fade(argb, brightness), heat, roughness, true);
			drawn++;
		}
		return drawn;
	}
}
