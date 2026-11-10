package io.github.icxd.knives_as_swords.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.fx.FxStyle;
import io.github.icxd.knives_as_swords.fx.MotionFx;
import io.github.icxd.knives_as_swords.fx.WeaponFx;
import io.github.icxd.knives_as_swords.model.KnifeAnimations;
import io.github.icxd.knives_as_swords.model.KnifeAnimations.Clip;
import io.github.icxd.knives_as_swords.model.KnifeAnimations.Pose;
import io.github.icxd.knives_as_swords.model.KnifeMesh;
import io.github.icxd.knives_as_swords.visuals.Visuals;
import io.github.icxd.knives_as_swords.visuals.combat.KnifeSounds;
import java.util.List;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * The knife in the player's own main hand, seen in first person: placed and moved by the knife's
 * own animations instead of Minecraft's item-in-hand pose.
 *
 * <p>Only what is drawn changes. Attacks, cooldowns and everything else stay Minecraft's; the
 * animations are merely started by the same events. Client thread only.
 */
public final class FirstPersonKnife {
	/**
	 * The animations were made for a narrower view than the fixed 70 degrees Minecraft draws
	 * held items with. Stretching sideways and upwards by this factor is the same as drawing
	 * them with the field of view they were made for.
	 */
	private static final float VIEW_SCALE = (float) (Math.tan(Math.toRadians(70.0 / 2.0))
			/ Math.tan(Math.toRadians(54.0 / 2.0)));
	/**
	 * Where the animations' camera sits relative to Minecraft's, in metres: a little to the left
	 * and above, which moves the knife towards the lower right. Without arms on screen, a knife
	 * further out to the side reads better; the game the animations come from offers the same
	 * adjustment to its players.
	 */
	private static final float SHIFT_RIGHT = 0.0635f;
	private static final float SHIFT_DOWN = 0.038f;
	/** Seconds over which one animation gives way to the next. */
	private static final float BLEND_SECONDS = 0.12f;
	/** Not drawn for this long means the knife was put away, so it is drawn (unsheathed) anew. */
	private static final long REDRAW_AFTER_NANOS = 250_000_000L;
	/** Longest step the animation takes in one frame; anything longer was a hitch or a pause. */
	private static final float MAX_FRAME_SECONDS = 0.1f;
	/**
	 * With effects on, a frame's worth of animation is taken in steps no longer than this, so that
	 * the effects see where the blade went on its way and not only where it ended up.
	 */
	private static final float EFFECT_STEP_SECONDS = 1f / 120f;
	private static final int MAX_EFFECT_STEPS = 8;

	private static final Random RANDOM = new Random();
	/** Trails, afterimages, sparks: everything that follows the knife's moves. */
	private static final MotionFx EFFECTS = new MotionFx();

	private static @Nullable KnifeVisual visual;
	private static int slot = -1;
	private static @Nullable Clip clip;
	private static boolean looping;
	private static float clipSeconds;
	private static @Nullable Pose pose;
	private static @Nullable Pose target;
	private static @Nullable Pose blendFrom;
	private static float blendSeconds = BLEND_SECONDS;
	private static long lastFrameNanos;
	private static boolean nextSwingHits;
	private static boolean nextSwingIsHeavy;
	private static int attackCounter;
	private static int inspectCounter;

	private static Matrix4f[] jointMatrices = new Matrix4f[0];
	private static float[] skinnedPositions = new float[0];
	private static float[] skinnedNormals = new float[0];
	private static final Matrix4f SCRATCH = new Matrix4f();

	private FirstPersonKnife() {
	}

	/**
	 * Draws the main-hand item as an animated knife, if it is one.
	 *
	 * @return {@code false} when Minecraft should draw the item its usual way instead
	 */
	public static boolean submit(ItemStack stack, HumanoidArm arm, PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector, int lightCoords) {
		KnifeRuntime runtime = KnivesAsSwordsClient.runtime();
		if (runtime == null || !runtime.config().firstPersonAnimations) {
			return false;
		}
		KnifeVisual current = KnifeReplacement.visualFor(stack);
		if (current == null || current.animations() == null) {
			return false;
		}
		KnifeAnimations animations = current.animations();
		Minecraft minecraft = Minecraft.getInstance();
		int selectedSlot = minecraft.player != null ? minecraft.player.getInventory().getSelectedSlot() : -1;

		long now = System.nanoTime();
		float elapsed = Math.clamp((now - lastFrameNanos) / 1e9f, 0f, MAX_FRAME_SECONDS);
		boolean putAway = now - lastFrameNanos > REDRAW_AFTER_NANOS;
		lastFrameNanos = now;
		if (minecraft.isPaused()) {
			elapsed = 0f;
		}

		if (current != visual || selectedSlot != slot || putAway || clip == null || pose == null
				|| pose.jointCount() != animations.jointCount()) {
			visual = current;
			slot = selectedSlot;
			pose = new Pose(animations.jointCount());
			target = new Pose(animations.jointCount());
			blendFrom = new Pose(animations.jointCount());
			Clip draw = animations.clip("draw").orElse(null);
			Clip start = draw != null ? draw : idle(animations);
			if (start == null) {
				clip = null;
				return false;
			}
			play(start, draw == null, false);
			elapsed = 0f;
			EFFECTS.begin(current);
			if (draw != null) {
				Visuals.get(KnifeSounds.class).drawn();
			}
		}

		FxStyle effects = WeaponFx.styleFor(stack.getItem());
		int steps = effects != null ? Math.clamp((int) Math.ceil(elapsed / EFFECT_STEP_SECONDS), 1, MAX_EFFECT_STEPS) : 1;
		for (int i = 0; i < steps; i++) {
			advance(animations, elapsed / steps);
			if (pose != null) {
				EFFECTS.step(current, effects, pose, elapsed / steps);
			}
		}
		draw(current, effects, arm, poseStack, submitNodeCollector, lightCoords, stack.hasFoil());
		return true;
	}

	/** The player swung their main hand: slash. */
	public static void onSwing() {
		KnifeVisual current = visual;
		boolean hits = nextSwingHits;
		boolean heavy = nextSwingIsHeavy;
		nextSwingHits = false;
		nextSwingIsHeavy = false;
		if (!active() || current == null || current.animations() == null) {
			return;
		}
		KnifeAnimations animations = current.animations();
		// A fully wound-up hit is the heavy stab; everything else is a quick slash.
		List<Clip> choices = animations.numbered(heavy ? "heavy_hit" : hits ? "light_hit" : "light_miss");
		if (choices.isEmpty()) {
			choices = animations.numbered("light_miss");
		}
		if (!choices.isEmpty()) {
			play(choices.get(attackCounter++ % choices.size()), false, true);
		}
		EFFECTS.swing(hits, heavy);
		Visuals.get(KnifeSounds.class).swung(hits, heavy);
	}

	/**
	 * The player is about to hit an entity; the swing that follows uses a "hit" animation.
	 *
	 * @param charged whether the attack was fully wound up, which Minecraft rewards with full damage
	 */
	public static void onAttackEntity(boolean charged) {
		nextSwingHits = true;
		nextSwingIsHeavy = charged;
	}

	/** The inspect key was pressed: look the knife over, unless busy with something else. */
	public static void inspect() {
		KnifeVisual current = visual;
		if (!active() || current == null || current.animations() == null || clip == null) {
			return;
		}
		boolean idle = looping;
		boolean inspecting = clip.name().startsWith("lookat");
		if (!idle && !inspecting) {
			return;
		}
		List<Clip> choices = current.animations().numbered("lookat");
		if (!choices.isEmpty()) {
			// A few knives have more than one; start with the first and vary after that.
			int index = inspectCounter++ == 0 ? 0 : RANDOM.nextInt(choices.size());
			play(choices.get(index), false, true);
			EFFECTS.inspect();
			Visuals.get(KnifeSounds.class).inspected();
		}
	}

	/** Whether the animated knife is what is on screen right now. */
	public static boolean showing() {
		return active();
	}

	private static boolean active() {
		return System.nanoTime() - lastFrameNanos < REDRAW_AFTER_NANOS;
	}

	private static @Nullable Clip idle(KnifeAnimations animations) {
		return animations.clip("idle1").or(() -> animations.clip("idle"))
				.or(() -> animations.numbered("idle").stream().findFirst()).orElse(null);
	}

	private static void play(Clip next, boolean loop, boolean blend) {
		if (blend && pose != null && blendFrom != null) {
			blendFrom.set(pose);
			blendSeconds = 0f;
		} else {
			blendSeconds = BLEND_SECONDS;
		}
		clip = next;
		looping = loop;
		clipSeconds = 0f;
	}

	private static void advance(KnifeAnimations animations, float elapsed) {
		clipSeconds += elapsed;
		if (clip != null && clipSeconds >= clip.duration()) {
			if (looping) {
				clipSeconds = clip.duration() > 0f ? clipSeconds % clip.duration() : 0f;
			} else {
				// Every one-shot animation ends where idling begins.
				Clip idle = idle(animations);
				if (idle != null) {
					play(idle, true, true);
				} else {
					clipSeconds = clip.duration();
				}
			}
		}
		if (clip == null || pose == null || target == null || blendFrom == null) {
			return;
		}

		clip.sample(clipSeconds, target);
		if (blendSeconds < BLEND_SECONDS) {
			blendSeconds += elapsed;
			float amount = Math.clamp(blendSeconds / BLEND_SECONDS, 0f, 1f);
			// Ease in and out, so the hand-over has no visible start or stop.
			amount = amount * amount * (3f - 2f * amount);
			pose.set(blendFrom);
			pose.blendTowards(target, amount);
		} else {
			pose.set(target);
		}
	}

	private static void draw(KnifeVisual current, @Nullable FxStyle effects, HumanoidArm arm, PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector, int lightCoords, boolean hasFoil) {
		if (pose == null) {
			return;
		}
		KnifeMesh mesh = current.mesh();
		boolean mirrored = arm == HumanoidArm.LEFT;
		poseStack.pushPose();
		// The poses are for a right hand; a left-handed player gets the mirror image.
		poseStack.scale(mirrored ? -VIEW_SCALE : VIEW_SCALE, VIEW_SCALE, 1f);
		// From the animations' view space (looking along +Z, +X left) to Minecraft's (along -Z, +X right).
		poseStack.rotateDegrees(Axis.YP, 180f);
		poseStack.translate(-SHIFT_RIGHT, -SHIFT_DOWN, 0f);

		if (effects != null) {
			// The shift above moved the animations' space; the eye is where it ended up.
			EFFECTS.submitLights(current, effects, poseStack, submitNodeCollector, true, SHIFT_RIGHT, SHIFT_DOWN, 0f);
			EFFECTS.submitEchoes(current, effects, poseStack, submitNodeCollector, mirrored);
		}

		int singleJoint = mesh.singleJoint();
		if (singleJoint >= 0) {
			// The whole knife moves as one piece: let the matrix do the work.
			poseStack.pushPose();
			poseStack.mulPose(pose.matrix(singleJoint, SCRATCH));
			KnifeItemRenderer.submit(current, mesh.positions(), mesh.normals(), poseStack, submitNodeCollector,
					lightCoords, OverlayTexture.NO_OVERLAY, hasFoil, mirrored);
			if (effects != null) {
				EFFECTS.submitGlow(current, effects, mesh.positions(), mesh.normals(), poseStack,
						submitNodeCollector, mirrored);
			}
			poseStack.popPose();
		} else {
			int joints = pose.jointCount();
			if (jointMatrices.length != joints) {
				jointMatrices = new Matrix4f[joints];
				for (int i = 0; i < joints; i++) {
					jointMatrices[i] = new Matrix4f();
				}
			}
			for (int i = 0; i < joints; i++) {
				pose.matrix(i, jointMatrices[i]);
			}
			if (skinnedPositions.length < mesh.positions().length) {
				skinnedPositions = new float[mesh.positions().length];
				skinnedNormals = new float[mesh.normals().length];
			}
			// Drawn later this frame, and only once per frame, so one shared buffer is enough.
			mesh.skin(jointMatrices, skinnedPositions, skinnedNormals);
			KnifeItemRenderer.submit(current, skinnedPositions, skinnedNormals, poseStack, submitNodeCollector,
					lightCoords, OverlayTexture.NO_OVERLAY, hasFoil, mirrored);
			if (effects != null) {
				EFFECTS.submitGlow(current, effects, skinnedPositions, skinnedNormals, poseStack,
						submitNodeCollector, mirrored);
			}
		}
		poseStack.popPose();
	}
}
