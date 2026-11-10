package io.github.icxd.knives_as_swords.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.icxd.knives_as_swords.model.KnifeMesh;
import io.github.icxd.knives_as_swords.render.KnifeVisual;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * A knife that slashes by itself, over and over, so that a look for its effects can be judged
 * while it is being put together: most effects only show in motion.
 *
 * <p>The knife turns about its grip, in the plane it lies in, edge first: a wind-up, a fast
 * slash that lands on something, a pause, and a slow way back. Client thread only.
 */
public final class PreviewFx {
	/** Seconds one round of the demonstration takes, and when its parts end. */
	private static final float PERIOD = 2.6f;
	private static final float SLASH_STARTS = 0.9f;
	private static final float SLASH_ENDS = 1.15f;
	private static final float RETURN_STARTS = 1.5f;
	/** The angles the knife is swung between, about the axis through its flat sides. */
	private static final float WOUND_UP = (float) Math.toRadians(-50.0);
	private static final float SWUNG_THROUGH = (float) Math.toRadians(130.0);
	/** As for the first-person knife: steps short enough to follow the blade along its way. */
	private static final float STEP_SECONDS = 1f / 120f;
	private static final int MAX_STEPS = 12;

	private final MotionFx motion = new MotionFx();
	private final Matrix4f swing = new Matrix4f();
	private final Matrix4f inverse = new Matrix4f();
	private final Vector3f up = new Vector3f();
	private final Vector3f toViewer = new Vector3f();
	private final Vector3f grip = new Vector3f();
	private @Nullable KnifeVisual visual;
	private long lastNanos;
	private float clock;
	private int slashes;

	/** Where the knife is held, in its own space: the point it turns about. */
	public static Vector3f grip(KnifeMesh mesh) {
		Vector3f min = mesh.min();
		Vector3f max = mesh.max();
		// The handle is the part below the origin; hold it halfway down.
		return new Vector3f((min.x + max.x) / 2f, min.y * 0.5f, 0f);
	}

	/**
	 * Lets time pass up to now.
	 *
	 * @param style the look of the effects; {@code null} for a knife that merely swings
	 * @param pose leading from the knife's own space to the picture, in which y points down and z
	 *     at the viewer
	 * @return where the knife is now: a turn within its own space, to draw it with
	 */
	public Matrix4fc advance(KnifeVisual current, @Nullable FxStyle style, Matrix4fc pose) {
		if (current != visual) {
			visual = current;
			grip.set(grip(current.mesh()));
			clock = 0f;
		}
		long now = System.nanoTime();
		float seconds = Math.clamp((now - lastNanos) / 1e9f, 0f, 0.1f);
		lastNanos = now;

		pose.invert(inverse);
		inverse.transformDirection(up.set(0f, -1f, 0f)).normalize();
		inverse.transformDirection(toViewer.set(0f, 0f, 1f)).normalize();

		int steps = Math.clamp((int) Math.ceil(seconds / STEP_SECONDS), 1, MAX_STEPS);
		for (int i = 0; i < steps; i++) {
			float before = clock;
			clock = (clock + seconds / steps) % PERIOD;
			if (before < SLASH_STARTS && clock >= SLASH_STARTS) {
				// Every slash lands; every other one is a heavy hit.
				motion.swing(true, slashes++ % 2 == 1);
			}
			float angle = angle(clock);
			swing.translation(grip).rotateX(angle).translate(-grip.x, -grip.y, -grip.z);
			motion.step(current, style, swing, up.x, up.y, up.z, seconds / steps);
		}
		return swing;
	}

	/** How far the knife is turned at a moment of the demonstration. */
	static float angle(float moment) {
		if (moment < SLASH_STARTS) {
			return woundUp(moment);
		}
		if (moment < SLASH_ENDS) {
			float from = woundUp(SLASH_STARTS);
			return from + (SWUNG_THROUGH - from) * FxMath.smoothstep(SLASH_STARTS, SLASH_ENDS, moment);
		}
		if (moment < RETURN_STARTS) {
			return SWUNG_THROUGH;
		}
		return SWUNG_THROUGH + (woundUp(0f) - SWUNG_THROUGH) * FxMath.smoothstep(RETURN_STARTS, PERIOD, moment);
	}

	/** Wound up, and not quite still. */
	private static float woundUp(float moment) {
		return WOUND_UP + (float) Math.toRadians(3.0) * (float) Math.sin(moment * 4f);
	}

	/** How long one round of the demonstration takes, in seconds. */
	static float period() {
		return PERIOD;
	}

	/**
	 * Draws what is not on the knife itself: trail, bolts, sparks and afterimages.
	 *
	 * @param poseStack leading from the knife's own space (before the turn that
	 *     {@link #advance} returned) to the picture
	 */
	public void submitAround(KnifeVisual current, FxStyle style, PoseStack poseStack, SubmitNodeCollector collector) {
		motion.submitLights(current, style, poseStack, collector, false, toViewer.x, toViewer.y, toViewer.z);
		motion.submitEchoes(current, style, poseStack, collector, false);
	}

	/**
	 * Draws the light on the knife itself.
	 *
	 * @param poseStack as for the knife: with the turn that {@link #advance} returned on top
	 */
	public void submitGlow(KnifeVisual current, FxStyle style, PoseStack poseStack, SubmitNodeCollector collector) {
		KnifeMesh mesh = current.mesh();
		motion.submitGlow(current, style, mesh.positions(), mesh.normals(), poseStack, collector, false);
	}
}
