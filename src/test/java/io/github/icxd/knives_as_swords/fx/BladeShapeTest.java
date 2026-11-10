package io.github.icxd.knives_as_swords.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.icxd.knives_as_swords.model.KnifeMesh;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BladeShapeTest {
	/** Vertices of a made-up knife, with the bone each one follows. */
	private static final class Knife {
		final List<float[]> vertices = new ArrayList<>();
		final List<Integer> bones = new ArrayList<>();

		/** A flat rectangle of vertices in the YZ plane, like a blade or a handle seen from the side. */
		Knife slab(float fromY, float toY, float fromZ, float toZ, int bone) {
			for (int row = 0; row <= 10; row++) {
				for (int column = 0; column <= 4; column++) {
					vertices.add(new float[] {0f, fromY + (toY - fromY) * row / 10f, fromZ + (toZ - fromZ) * column / 4f});
					bones.add(bone);
				}
			}
			return this;
		}

		Knife point(float x, float y, float z, int bone) {
			vertices.add(new float[] {x, y, z});
			bones.add(bone);
			return this;
		}

		BladeShape shape(boolean skinned, boolean severalParts) {
			int count = vertices.size();
			float[] positions = new float[count * 3];
			float[] normals = new float[count * 3];
			int[] joints = new int[skinned ? count * KnifeMesh.INFLUENCES : 0];
			float[] weights = new float[skinned ? count * KnifeMesh.INFLUENCES : 0];
			for (int i = 0; i < count; i++) {
				System.arraycopy(vertices.get(i), 0, positions, i * 3, 3);
				normals[i * 3] = 1f;
				if (skinned) {
					joints[i * KnifeMesh.INFLUENCES] = bones.get(i);
					weights[i * KnifeMesh.INFLUENCES] = 1f;
				}
			}
			return BladeShape.of(positions, normals, joints, weights, severalParts);
		}
	}

	@Test
	void findsTheBladeOfAKnifeMadeOfOnePiece() {
		// Handle below the origin, blade above it with its edge towards +Z, and a point on top.
		BladeShape blade = new Knife().slab(-0.1f, 0f, -0.01f, 0.01f, 0).slab(0f, 0.18f, -0.015f, 0.02f, 0)
				.point(0f, 0.2f, 0.005f, 0).shape(false, false);

		assertEquals(0, blade.joint());
		assertEquals(0.2f, blade.tip().y(), 1e-6f);
		assertEquals(0.005f, blade.tip().z(), 1e-6f);
		// The edge starts right above the handle, on the side the edge is on.
		assertEquals(0.02f, blade.base().z(), 1e-6f);
		assertTrue(blade.base().y() >= 0f && blade.base().y() < 0.03f, "base at " + blade.base().y());
		assertTrue(blade.center().y() > 0.05f && blade.center().y() < 0.15f);
		assertEquals(blade.base().distance(blade.tip()), blade.length(), 1e-6f);
	}

	@Test
	void onAKnifeOfSeveralPartsTheBladeIsWhatMovesWithThePoint() {
		// Two handle halves on bones 1 and 2 that reach up beside the blade, as on a folding knife.
		BladeShape blade = new Knife().slab(-0.12f, 0.02f, -0.02f, 0f, 1).slab(-0.12f, 0.02f, 0f, 0.02f, 2)
				.slab(0.0f, 0.16f, -0.01f, 0.012f, 3).point(0f, 0.17f, 0f, 3).shape(true, true);

		assertEquals(3, blade.joint());
		assertEquals(0.17f, blade.tip().y(), 1e-6f);
		assertEquals(0.012f, blade.base().z(), 1e-6f);
		for (int i = 0; i < blade.anchorCount(); i++) {
			// Nothing of the handle halves, which end at z = ±0.02.
			assertTrue(Math.abs(blade.anchors()[i * 3 + 2]) <= 0.012f + 1e-6f);
		}
	}

	@Test
	void aBoneCarryingTheWholeKnifeDoesNotMakeTheHandleABlade() {
		BladeShape blade = new Knife().slab(-0.1f, 0f, -0.01f, 0.01f, 0).slab(0f, 0.2f, -0.015f, 0.02f, 0)
				.shape(true, false);

		assertTrue(blade.base().y() >= 0f, "base at " + blade.base().y());
		for (int i = 0; i < blade.anchorCount(); i++) {
			assertTrue(blade.anchors()[i * 3 + 1] >= 0f);
		}
	}

	@Test
	void theBladeStartsWithinReasonEvenIfTheModelsOriginIsOdd() {
		// The whole knife above the origin: the lowest third at least is taken to be handle.
		BladeShape high = new Knife().slab(1f, 1.3f, -0.01f, 0.01f, 0).shape(false, false);
		assertTrue(high.base().y() >= 1.089f, "base at " + high.base().y());

		// The whole knife below it: the blade is not allowed to shrink to nothing.
		BladeShape low = new Knife().slab(-1.3f, -1f, -0.01f, 0.01f, 0).shape(false, false);
		assertTrue(low.base().y() <= -1.12f + 0.031f, "base at " + low.base().y());
		assertTrue(low.length() > 0.1f);
	}

	@Test
	void keepsASpreadOfPointsOnTheBladeForEffectsToStartFrom() {
		BladeShape blade = new Knife().slab(-0.1f, 0f, -0.01f, 0.01f, 0).slab(0f, 0.2f, -0.015f, 0.02f, 0)
				.shape(false, false);

		assertTrue(blade.anchorCount() > 16 && blade.anchorCount() <= 64, "anchors: " + blade.anchorCount());
		float lowest = Float.POSITIVE_INFINITY;
		float highest = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < blade.anchorCount(); i++) {
			lowest = Math.min(lowest, blade.anchors()[i * 3 + 1]);
			highest = Math.max(highest, blade.anchors()[i * 3 + 1]);
			assertEquals(1f, blade.anchorNormals()[i * 3]);
		}
		// From near the handle to near the point, not bunched up at one end.
		assertTrue(lowest < 0.03f && highest > 0.17f, lowest + " to " + highest);
	}

	@Test
	void saysHowFarAlongTheKnifeAHeightIs() {
		BladeShape blade = new Knife().slab(-0.1f, 0f, -0.01f, 0.01f, 0).slab(0f, 0.3f, -0.015f, 0.02f, 0)
				.shape(false, false);

		assertEquals(0f, blade.along(-0.1f), 1e-6f);
		assertEquals(0.25f, blade.along(0f), 1e-6f);
		assertEquals(1f, blade.along(0.3f), 1e-6f);
	}

	@Test
	void anEmptyModelStillGivesSomethingUsable() {
		BladeShape blade = BladeShape.of(new float[0], new float[0], new int[0], new float[0], false);

		assertEquals(1, blade.anchorCount());
		assertTrue(blade.length() > 0f);
	}
}
