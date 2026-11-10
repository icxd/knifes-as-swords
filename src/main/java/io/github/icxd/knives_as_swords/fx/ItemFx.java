package io.github.icxd.knives_as_swords.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.icxd.knives_as_swords.model.KnifeMesh;
import io.github.icxd.knives_as_swords.render.KnifeVisual;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The effects of a knife that is drawn like any other item: in somebody's hand, lying on the
 * ground, in a frame, or in the settings screen's preview.
 *
 * <p>Such a knife glows and crackles wherever it is. One held by a player or mob in the world
 * also leaves a trail when it is swung; see {@link WorldFx}. Inventory slots show no effects.
 *
 * <p>Client thread only.
 */
public final class ItemFx {
	/** Knives further from the camera than this (in blocks) are drawn without effects. */
	private static final float MAX_DISTANCE = 40f;
	/** How excited a knife is that just lies or hangs there. */
	private static final float REST_ENERGY = 0.15f;

	/**
	 * Batches are drawn after this class has long returned, so each knife needs one of its own for
	 * the frame. They are handed out in turn; more knives than this in one frame would share.
	 */
	private static final FxBatch[] POOL = new FxBatch[128];
	private static int nextBatch;

	private static final Matrix4f INVERSE = new Matrix4f();
	private static final Vector3f EYE = new Vector3f();

	private ItemFx() {
	}

	/**
	 * Draws the effects of a knife drawn as an item.
	 *
	 * @param context how the item is shown
	 * @param holder which entity's hand it is in, or 0 for none
	 * @param holderX where that entity is in the world; likewise {@code holderY}, {@code holderZ}
	 * @param poseStack leading from the knife's own space to where it is drawn
	 */
	public static void submit(KnifeVisual visual, FxStyle style, ItemDisplayContext context, int holder, double holderX,
			double holderY, double holderZ, PoseStack poseStack, SubmitNodeCollector collector) {
		boolean inWorld = WorldFx.collecting();
		// Anywhere else an item is drawn in three dimensions is a menu, where effects would only distract.
		if (!inWorld && !context.firstPerson()) {
			return;
		}
		Matrix4f pose = poseStack.last().pose();
		float distanceSquared = pose.m30() * pose.m30() + pose.m31() * pose.m31() + pose.m32() * pose.m32();
		if (distanceSquared > MAX_DISTANCE * MAX_DISTANCE) {
			return;
		}

		float energy = REST_ENERGY;
		if (inWorld && holder != 0) {
			long hand = ((long) holder << 1) | (context.leftHand() ? 1 : 0);
			energy = Math.max(energy, WorldFx.track(hand, visual, style, pose, holderX, holderY, holderZ));
		}
		lights(visual, style, poseStack, collector, FxClock.seconds(), System.identityHashCode(visual) * 31 + holder,
				energy, true);
	}

	/** Draws the effects of the knife in the settings screen, which is looked at straight on from afar. */
	public static void submitPreview(KnifeVisual visual, FxStyle style, PoseStack poseStack,
			SubmitNodeCollector collector) {
		float time = FxClock.seconds();
		// Nothing moves the knife here, so it gets excited by itself every few seconds, to show what the look can do.
		float wave = Math.max(0f, (float) Math.sin(time * 1.3f));
		lights(visual, style, poseStack, collector, time, System.identityHashCode(visual), 0.25f + 0.75f * wave * wave,
				false);
	}

	private static void lights(KnifeVisual visual, FxStyle style, PoseStack poseStack, SubmitNodeCollector collector,
			float time, int seed, float energy, boolean perspective) {
		KnifeMesh mesh = visual.mesh();
		BladeShape blade = visual.blade();
		FxBatch batch = POOL[nextBatch];
		if (batch == null) {
			batch = POOL[nextBatch] = new FxBatch();
		}
		nextBatch = (nextBatch + 1) % POOL.length;
		batch.clear();

		// Where the viewer is, in the knife's own space.
		poseStack.last().pose().invert(INVERSE);
		if (perspective) {
			INVERSE.transformPosition(EYE.set(0f, 0f, 0f));
			batch.eye(EYE.x, EYE.y, EYE.z);
		} else {
			INVERSE.transformDirection(EYE.set(0f, 0f, 1f));
			batch.viewDirection(EYE.x, EYE.y, EYE.z);
		}

		int color = style.color(time, 0f);
		float excited = Math.min(energy, 1f);
		ArcBolts.build(batch, blade, null, time, seed, style.arcs(), energy, color, style.heat(), style.roughness(), 1f);
		batch.dot(blade.center().x(), blade.center().y(), blade.center().z(), blade.length() * (0.8f + 0.4f * excited),
				FxMath.fade(color, style.glow() * (0.07f + 0.15f * excited)), 0.2f * style.heat());
		FxDraw.batch(batch, poseStack, collector);

		FxDraw.ghost(mesh, blade, mesh.positions(), mesh.normals(), poseStack, collector,
				FxMath.fade(style.color(time, 0.15f), style.glow() * (0.45f + 0.45f * excited)),
				0.0012f + 0.002f * excited, 2.5f, time * 0.8f, 0.2f, false);
	}
}
