package io.github.icxd.knives_as_swords.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.icxd.knives_as_swords.model.KnifeAnimations.Pose;
import io.github.icxd.knives_as_swords.model.KnifeMesh;
import io.github.icxd.knives_as_swords.render.KnifeVisual;
import java.util.Random;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * The effects of one knife whose every move is known: the one in the player's own hand, seen in
 * first person, and the one swinging in the effect editor's preview.
 *
 * <p>Everything here lives in one space, measured in metres, in which the knife moves: for the
 * first-person knife that is the space its animations are made for. The blade's way through
 * that space is followed several times per frame, which is what the effects react to: a fast
 * blade leaves a trail and afterimages and sheds sparks, a resting one merely glows and crackles
 * a little.
 *
 * <p>Client thread only.
 */
public final class MotionFx {
	/** Tip speed, in metres per second, from which a trail shows in full. Idling stays far below. */
	private static final float TRAIL_SPEED = 2.2f;
	/** Tip speed at which the blade counts as fully excited. */
	private static final float EXCITED_SPEED = 5f;
	/** Tip speed from which afterimages are left behind, how often, and how long each one lasts. */
	private static final float ECHO_SPEED = 2.6f;
	private static final float ECHO_INTERVAL = 0.05f;
	private static final float ECHO_LIFE = 0.22f;
	/** Each one is the whole knife drawn once more, so there are only as many as can be alive at once. */
	private static final int ECHOES = 5;
	/** Tip speed from which the blade sheds sparks. */
	private static final float SHED_SPEED = 2f;
	/** How long after a swing starts the blade lands, when it hits something. */
	private static final float LIGHT_HIT_DELAY = 0.1f;
	private static final float HEAVY_HIT_DELAY = 0.16f;
	/** How long the ring of a shock takes to run outwards and fade. */
	private static final float RING_SECONDS = 0.28f;
	/** The clock the effects go by starts over after this many seconds. */
	private static final float CLOCK_PERIOD_SECONDS = 4096f;

	private final BladeTrail trail = new BladeTrail();
	private final SparkSystem sparks = new SparkSystem(384);
	private final FxBatch batch = new FxBatch();
	private final Echo[] echoes = new Echo[ECHOES];
	private final Random random = new Random();

	/** Where the blade is: from the knife's rest pose to the space everything happens in. */
	private final Matrix4f blade = new Matrix4f();
	private final Vector3f base = new Vector3f();
	private final Vector3f tip = new Vector3f();
	private final Vector3f center = new Vector3f();
	private final float[] velocity = new float[3];
	private Matrix4f[] joints = new Matrix4f[0];

	private @Nullable KnifeVisual visual;
	private float time;
	/** How excited the blade is: follows its speed up at once and calms down slowly. */
	private float energy;
	/** A short burst of light: the knife being drawn, a hit landing. */
	private float flash;
	/** A slow swell of light while the knife is being looked at. */
	private float pulse;
	private float bandPhase;
	private float shedDebt;
	private float echoWait;
	private int nextEcho;
	private float burstIn = -1f;
	private boolean burstIsHeavy;
	private float ringBorn = Float.NEGATIVE_INFINITY;
	private final Vector3f ringAt = new Vector3f();
	/** How far the ring gets, in metres. */
	private float ringReach;
	/** Whether the ring stays around the blade rather than where it started. */
	private boolean ringOnBlade;

	/** An afterimage: the knife as it was at one moment. */
	private static final class Echo {
		@Nullable KnifeVisual visual;
		float born;
		/** For a knife moving as one piece: where that piece was. */
		final Matrix4f matrix = new Matrix4f();
		/** For a knife whose parts move against each other: every vertex where it was. */
		float[] positions = new float[0];
		float[] normals = new float[0];
		boolean posed;
	}

	public MotionFx() {
		for (int i = 0; i < ECHOES; i++) {
			echoes[i] = new Echo();
		}
	}

	/** The knife has just been taken out: start afresh, with a flash. */
	public void begin(KnifeVisual current) {
		visual = current;
		trail.clear();
		sparks.clear();
		for (Echo echo : echoes) {
			echo.visual = null;
		}
		energy = 1f;
		flash = 1f;
		pulse = 0f;
		shedDebt = 0f;
		echoWait = 0f;
		burstIn = -1f;
		ringBorn = time;
		ringReach = 0.2f;
		ringOnBlade = true;
	}

	/**
	 * A swing has started.
	 *
	 * @param hits whether it lands on something
	 * @param heavy whether it is the heavy stab rather than a quick slash
	 */
	public void swing(boolean hits, boolean heavy) {
		energy = Math.max(energy, heavy ? 1.2f : 0.9f);
		if (hits) {
			burstIn = heavy ? HEAVY_HIT_DELAY : LIGHT_HIT_DELAY;
			burstIsHeavy = heavy;
		}
	}

	/** The player started looking the knife over. */
	public void inspect() {
		pulse = 1f;
	}

	/**
	 * Lets a little time pass with an animated knife in the given pose. Called several times per
	 * frame while the knife moves, so that fast moves are followed along their way. "Up", for
	 * sparks to rise or fall along, is +Y.
	 *
	 * @param style the look of the effects; {@code null} for none
	 */
	public void step(KnifeVisual current, @Nullable FxStyle style, Pose pose, float seconds) {
		pose.matrix(current.blade().joint(), blade);
		advance(current, style, pose, 0f, 1f, 0f, seconds);
	}

	/**
	 * Lets a little time pass with a knife that moves as a whole, in its rest pose.
	 *
	 * @param whole where the knife is: from its own space to the space everything happens in. It
	 *     must not change sizes
	 * @param upX which way is up in that space, for sparks to rise or fall along; likewise
	 *     {@code upY} and {@code upZ}
	 */
	public void step(KnifeVisual current, @Nullable FxStyle style, Matrix4fc whole, float upX, float upY, float upZ,
			float seconds) {
		blade.set(whole);
		advance(current, style, null, upX, upY, upZ, seconds);
	}

	private void advance(KnifeVisual current, @Nullable FxStyle style, @Nullable Pose pose, float upX, float upY,
			float upZ, float seconds) {
		if (current != visual) {
			begin(current);
		}
		BladeShape shape = current.blade();
		blade.transformPosition(shape.base(), base);
		blade.transformPosition(shape.tip(), tip);
		blade.transformPosition(shape.center(), center);
		if (style == null || seconds <= 0f) {
			return;
		}
		time += seconds;
		if (time > CLOCK_PERIOD_SECONDS) {
			// Started over, so that fractions of a second stay exact; what was remembered is void.
			time -= CLOCK_PERIOD_SECONDS;
			ringBorn = Float.NEGATIVE_INFINITY;
			trail.clear();
			for (Echo echo : echoes) {
				echo.visual = null;
			}
		}
		trail.add(time, base, tip);
		float speed = trail.tipSpeed();

		energy = Math.max(energy * (float) Math.exp(-3f * seconds), Math.min(speed / EXCITED_SPEED, 1.2f));
		flash *= (float) Math.exp(-7f * seconds);
		pulse *= (float) Math.exp(-0.6f * seconds);
		// Whole bands make no difference to the eye, so the count is kept small.
		bandPhase = (bandPhase + seconds * (0.5f + 2.2f * Math.min(energy, 1f))) % 64f;

		if (style.sparks() > 0f) {
			if (speed > SHED_SPEED) {
				// Thrown off by the moving blade, more of them towards the tip.
				shedDebt += style.sparks() * 150f * Math.min((speed - SHED_SPEED) / 6f, 1f) * seconds;
				trail.tipVelocity(velocity);
				while (shedDebt >= 1f) {
					shedDebt -= 1f;
					float along = 1f - random.nextFloat() * random.nextFloat();
					spark(style, along, velocity[0] * along * 0.35f, velocity[1] * along * 0.35f,
							velocity[2] * along * 0.35f, 0.6f, 0.25f + 0.45f * random.nextFloat());
				}
			} else {
				// At rest a few drift off the blade by themselves.
				shedDebt += style.sparks() * 5f * seconds;
				while (shedDebt >= 1f) {
					shedDebt -= 1f;
					spark(style, random.nextFloat(), 0f, 0f, 0f, 0.12f, 0.5f + 0.7f * random.nextFloat());
				}
			}
		}
		if (burstIn >= 0f) {
			burstIn -= seconds;
			if (burstIn < 0f) {
				burst(style, burstIsHeavy, upX, upY, upZ);
			}
		}
		float gravity = style.sparkGravity();
		sparks.advance(seconds, upX * gravity, upY * gravity, upZ * gravity, style.sparkDrag(), style.sparkSwirl());

		echoWait -= seconds;
		if (style.echo() > 0f && speed > ECHO_SPEED && echoWait <= 0f) {
			capture(current, pose);
			echoWait = ECHO_INTERVAL;
		}
	}

	/** One spark from somewhere along the blade ({@code along}: 0 its base, 1 its tip). */
	private void spark(FxStyle style, float along, float velocityX, float velocityY, float velocityZ, float scatter,
			float life) {
		float x = base.x + (tip.x - base.x) * along;
		float y = base.y + (tip.y - base.y) * along;
		float z = base.z + (tip.z - base.z) * along;
		sparks.emit(x, y, z, velocityX + scatter * (float) random.nextGaussian(),
				velocityY + scatter * (float) random.nextGaussian(), velocityZ + scatter * (float) random.nextGaussian(),
				life, 0.003f + 0.004f * random.nextFloat(), style.color(time, random.nextFloat()), style.heat());
	}

	/** The blade lands on something: sparks fly from its tip, mostly on along the cut and upwards. */
	private void burst(FxStyle style, boolean heavy, float upX, float upY, float upZ) {
		flash = 1f;
		ringBorn = time;
		ringAt.set(tip);
		ringReach = heavy ? 0.26f : 0.13f;
		ringOnBlade = false;

		trail.tipVelocity(velocity);
		float moving = (float) Math.sqrt(velocity[0] * velocity[0] + velocity[1] * velocity[1] + velocity[2] * velocity[2]);
		float onward = moving > 1e-3f ? 0.8f / moving : 0f;
		int count = Math.round(style.sparks() * (heavy ? 70f : 34f));
		for (int i = 0; i < count; i++) {
			float speed = (heavy ? 2f : 1.5f) + (heavy ? 5f : 3.5f) * random.nextFloat() * random.nextFloat();
			float x = (float) random.nextGaussian() + velocity[0] * onward + 0.3f * upX;
			float y = (float) random.nextGaussian() + velocity[1] * onward + 0.3f * upY;
			float z = (float) random.nextGaussian() + velocity[2] * onward + 0.3f * upZ;
			float length = (float) Math.sqrt(x * x + y * y + z * z);
			if (length < 1e-4f) {
				continue;
			}
			sparks.emit(tip.x, tip.y, tip.z, x / length * speed, y / length * speed, z / length * speed,
					0.25f + 0.5f * random.nextFloat(), 0.0035f + 0.005f * random.nextFloat(),
					style.color(time, random.nextFloat()), style.heat());
		}
	}

	/** Keeps the knife as it is right now, as an afterimage. */
	private void capture(KnifeVisual current, @Nullable Pose pose) {
		Echo echo = echoes[nextEcho++ % ECHOES];
		echo.visual = current;
		echo.born = time;
		KnifeMesh mesh = current.mesh();
		if (pose == null) {
			echo.matrix.set(blade);
			echo.posed = false;
			return;
		}
		int singleJoint = mesh.singleJoint();
		if (singleJoint >= 0) {
			pose.matrix(singleJoint, echo.matrix);
			echo.posed = false;
			return;
		}
		int count = pose.jointCount();
		if (joints.length != count) {
			joints = new Matrix4f[count];
			for (int i = 0; i < count; i++) {
				joints[i] = new Matrix4f();
			}
		}
		for (int i = 0; i < count; i++) {
			pose.matrix(i, joints[i]);
		}
		if (echo.positions.length < mesh.positions().length) {
			echo.positions = new float[mesh.positions().length];
			echo.normals = new float[mesh.normals().length];
		}
		mesh.skin(joints, echo.positions, echo.normals);
		echo.posed = true;
	}

	/**
	 * Draws everything that is light without a surface: the trail, bolts, sparks, the ring of a
	 * hit, and a soft light around the blade.
	 *
	 * @param poseStack leading from the space everything happens in to the screen
	 * @param perspective whether the viewer sits at a point in that space (given by
	 *     {@code eyeX}, {@code eyeY}, {@code eyeZ}), as in the world, or looks on from afar in
	 *     the direction given, as in menus
	 */
	public void submitLights(KnifeVisual current, FxStyle style, PoseStack poseStack, SubmitNodeCollector collector,
			boolean perspective, float eyeX, float eyeY, float eyeZ) {
		BladeShape shape = current.blade();
		int head = style.color(time, 0f);
		float now = time;
		batch.clear();
		if (perspective) {
			batch.eye(eyeX, eyeY, eyeZ);
		} else {
			batch.viewDirection(eyeX, eyeY, eyeZ);
		}

		trail.build(batch, time, 0.16f + 0.14f * style.trail(), TRAIL_SPEED, shape.length(),
				life -> style.color(now, life), Math.min(style.trail() * 1.1f, 1f), style.heat(), style.roughness(),
				0f, 0f, 0f);
		ArcBolts.build(batch, shape, blade, time, System.identityHashCode(current), style.arcs(),
				0.12f + energy + flash + 0.5f * pulse, head, style.heat(), style.roughness(), 1f);
		sparks.build(batch, 0f, 0f, 0f, 0.035f, 1f);

		float halo = style.glow() * Math.min(0.12f + 0.2f * energy + 0.5f * flash + 0.1f * pulse, 1f);
		batch.dot(center.x, center.y, center.z, shape.length() * (0.8f + 0.4f * Math.min(energy, 1f) + 0.8f * flash),
				FxMath.fade(head, halo), 0.3f * style.heat());

		float ringAge = time - ringBorn;
		if (ringAge >= 0f && ringAge < RING_SECONDS) {
			float t = ringAge / RING_SECONDS;
			Vector3f at = ringOnBlade ? center : ringAt;
			batch.ring(at.x, at.y, at.z, ringReach * (0.2f + 0.8f * (float) Math.sqrt(t)),
					FxMath.fade(head, style.glow() * (1f - t) * (1f - t)), style.heat());
		}

		FxDraw.batch(batch, poseStack, collector);
	}

	/**
	 * Draws the light on the knife itself.
	 *
	 * @param positions the knife's vertices as they are drawn right now, and their {@code normals}
	 * @param poseStack leading from those positions to the screen
	 */
	public void submitGlow(KnifeVisual current, FxStyle style, float[] positions, float[] normals, PoseStack poseStack,
			SubmitNodeCollector collector, boolean mirrored) {
		float swell = 0.5f + 0.5f * (float) Math.sin(time * 6f);
		float strength = style.glow() * Math.clamp(0.5f + 0.4f * energy + 0.7f * flash + 0.35f * pulse * swell, 0f, 1f);
		FxDraw.ghost(current.mesh(), current.blade(), positions, normals, poseStack, collector,
				FxMath.fade(style.color(time, 0.15f), strength), 0.0012f + 0.002f * Math.min(energy, 1f), 2.5f,
				bandPhase, 0.22f + 0.25f * flash, mirrored);
	}

	/**
	 * Draws the afterimages.
	 *
	 * @param poseStack leading from the space everything happens in to the screen
	 */
	public void submitEchoes(KnifeVisual current, FxStyle style, PoseStack poseStack, SubmitNodeCollector collector,
			boolean mirrored) {
		if (style.echo() <= 0f) {
			return;
		}
		KnifeMesh mesh = current.mesh();
		for (Echo echo : echoes) {
			float age = time - echo.born;
			if (echo.visual != current || age < 0f || age >= ECHO_LIFE) {
				continue;
			}
			float life = age / ECHO_LIFE;
			float strength = style.echo() * 0.55f * (float) Math.pow(1f - life, 1.5);
			int color = FxMath.fade(style.color(time, life), strength);
			if (echo.posed) {
				FxDraw.ghost(mesh, current.blade(), echo.positions, echo.normals, poseStack, collector, color, 0.0006f,
						5f, bandPhase * 2f, 0.22f, mirrored);
			} else {
				poseStack.pushPose();
				poseStack.mulPose(echo.matrix);
				FxDraw.ghost(mesh, current.blade(), mesh.positions(), mesh.normals(), poseStack, collector, color,
						0.0006f, 5f, bandPhase * 2f, 0.22f, mirrored);
				poseStack.popPose();
			}
		}
	}
}
