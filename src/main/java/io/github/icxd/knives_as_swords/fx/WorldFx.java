package io.github.icxd.knives_as_swords.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.icxd.knives_as_swords.render.KnifeVisual;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Effects that have a place in the world rather than in front of the player's eyes: the trails of
 * knives in the hands of players and mobs, and whatever else wants to add light to the world.
 *
 * <p>A blade's way is remembered relative to whoever holds it. Walking about therefore leaves no
 * trail; swinging the arm does.
 *
 * <p>Client thread only.
 */
public final class WorldFx {
	/** Tip speed, in blocks per second, from which a trail shows in full. An arm swinging while walking stays below. */
	private static final float TRAIL_SPEED = 4.5f;
	/** Tip speed at which a blade counts as fully excited. */
	private static final float EXCITED_SPEED = 8f;
	/** A knife not drawn for this many seconds is forgotten. */
	private static final float FORGET_SECONDS = 1f;
	private static final int MAX_TRACKED = 96;

	/** A knife in somebody's hand. */
	private static final class Tracked {
		final BladeTrail trail = new BladeTrail();
		KnifeVisual visual;
		FxStyle style;
		float seen;
		float energy;
		/** The blade's length as drawn, in blocks. */
		float length;
		/** Where the holder is; the trail is remembered relative to this. */
		double x;
		double y;
		double z;
	}

	private static final Map<Long, Tracked> TRACKED = new HashMap<>();
	private static final FxBatch BATCH = new FxBatch();
	private static final WorldFrame FRAME = new WorldFrame(BATCH);
	private static final List<Consumer<WorldFrame>> PAINTERS = new ArrayList<>();
	private static final Quaternionf FACING = new Quaternionf();
	private static final Vector3f BASE = new Vector3f();
	private static final Vector3f TIP = new Vector3f();

	private static boolean collecting;
	private static double cameraX;
	private static double cameraY;
	private static double cameraZ;
	private static float now;
	private static float before;
	private static int clockRestarts;

	private WorldFx() {
	}

	public static void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> finish(context.submitNodeCollector(), context.poseStack()));
	}

	/**
	 * Lets somebody else add light to the world as well: called once per frame, after everything in
	 * the world has been gathered.
	 */
	public static void paint(Consumer<WorldFrame> painter) {
		PAINTERS.add(painter);
	}

	/** The world starts gathering what to draw this frame. */
	public static void begin(CameraRenderState camera) {
		collecting = true;
		cameraX = camera.pos.x;
		cameraY = camera.pos.y;
		cameraZ = camera.pos.z;
		FACING.set(camera.orientation);
		now = FxClock.seconds();
		if (FxClock.restarts() != clockRestarts) {
			// Remembered times mean nothing on the new clock.
			clockRestarts = FxClock.restarts();
			TRACKED.clear();
			before = now;
		}
	}

	/** Whether the world is gathering what to draw right now, as opposed to a menu or the player's own hand. */
	public static boolean collecting() {
		return collecting;
	}

	/**
	 * A knife in somebody's hand is being drawn: remember where its blade is.
	 *
	 * @param holder tells this hand apart from every other
	 * @param pose leads from the knife's own space to the world as seen from the camera
	 * @param holderX where the holder is in the world; likewise {@code holderY}, {@code holderZ}
	 * @return how excited the blade is, 0 (at rest) to about 1 (swung fast)
	 */
	static float track(long holder, KnifeVisual visual, FxStyle style, Matrix4fc pose, double holderX, double holderY,
			double holderZ) {
		Tracked tracked = TRACKED.get(holder);
		if (tracked == null) {
			if (TRACKED.size() >= MAX_TRACKED) {
				return 0f;
			}
			tracked = new Tracked();
			TRACKED.put(holder, tracked);
		}
		if (tracked.visual != visual) {
			tracked.trail.clear();
			tracked.visual = visual;
			tracked.energy = 0f;
		}
		tracked.style = style;
		tracked.seen = now;
		tracked.x = holderX;
		tracked.y = holderY;
		tracked.z = holderZ;

		BladeShape blade = visual.blade();
		pose.transformPosition(blade.base(), BASE);
		pose.transformPosition(blade.tip(), TIP);
		tracked.length = Math.max(BASE.distance(TIP), 1e-3f);
		// From "as seen from the camera" to "as seen from the holder".
		float dx = (float) (cameraX - holderX), dy = (float) (cameraY - holderY), dz = (float) (cameraZ - holderZ);
		BASE.add(dx, dy, dz);
		TIP.add(dx, dy, dz);
		tracked.trail.add(now, BASE, TIP);
		tracked.energy = Math.max(tracked.energy, Math.min(tracked.trail.tipSpeed() / EXCITED_SPEED, 1.2f));
		return tracked.energy;
	}

	/** Everything has been gathered: add the trails, and whatever others have to add, as seen from the camera. */
	private static void finish(SubmitNodeCollector collector, PoseStack poseStack) {
		if (!collecting) {
			return;
		}
		collecting = false;
		float seconds = Math.clamp(now - before, 0f, 0.1f);
		before = now;
		float time = now;

		BATCH.clear();
		BATCH.eye(0f, 0f, 0f);
		for (Iterator<Tracked> all = TRACKED.values().iterator(); all.hasNext();) {
			Tracked tracked = all.next();
			if (now - tracked.seen > FORGET_SECONDS || now < tracked.seen) {
				all.remove();
				continue;
			}
			tracked.energy *= (float) Math.exp(-3f * seconds);
			if (tracked.seen != now) {
				// Not drawn this frame: out of sight.
				continue;
			}
			FxStyle style = tracked.style;
			tracked.trail.build(BATCH, now, 0.14f + 0.12f * style.trail(), TRAIL_SPEED, tracked.length,
					life -> style.color(time, life), style.trail(), style.heat(), style.roughness(),
					(float) (tracked.x - cameraX), (float) (tracked.y - cameraY), (float) (tracked.z - cameraZ));
		}

		FRAME.begin(cameraX, cameraY, cameraZ, now, seconds, collector, poseStack, FACING);
		for (Consumer<WorldFrame> painter : PAINTERS) {
			painter.accept(FRAME);
		}
		FxDraw.batch(BATCH, poseStack, collector);
	}
}
