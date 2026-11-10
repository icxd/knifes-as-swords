package io.github.icxd.knives_as_swords.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FxBatchTest {
	private static float[] position(FxBatch batch, int vertex) {
		return new float[] {batch.position(vertex, 0), batch.position(vertex, 1), batch.position(vertex, 2)};
	}

	private static float distance(float[] a, float[] b) {
		return (float) Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1])
				+ (a[2] - b[2]) * (a[2] - b[2]));
	}

	@Test
	void aRibbonQuadRunsFromBaseToTipAcrossAndAlongTheSweep() {
		FxBatch batch = new FxBatch();
		batch.ribbon(new float[] {0f, 0f, 0f}, new float[] {0f, 1f, 0f}, new float[] {1f, 0f, 0f},
				new float[] {1f, 1f, 0f}, 2f, 3f, 0xFF112233, 0x80445566, 0.5f, 0.25f);

		assertEquals(1, batch.quadCount());
		// Base, tip, tip, base: u says which.
		assertEquals(0f, batch.u(0));
		assertEquals(1f, batch.u(1));
		assertEquals(1f, batch.u(2));
		assertEquals(0f, batch.u(3));
		assertEquals(2f, batch.v(0));
		assertEquals(3f, batch.v(2));
		assertEquals(0xFF112233, batch.color(1));
		assertEquals(0x80445566, batch.color(2));
		assertEquals(FxBatch.RIBBON, batch.kind(0));
		assertEquals(0.5f, batch.heat(0));
		assertEquals(0.25f, batch.roughness(0));
	}

	@Test
	void aLineOfLightTurnsItsFlatSideToTheViewer() {
		FxBatch batch = new FxBatch();
		// The viewer looks along z at a line running along x.
		batch.eye(0f, 0f, -10f);
		batch.polyline(new float[] {-1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f}, 3, 0.1f, 0xFFFFFFFF, 1f, 0f, false);

		assertEquals(2, batch.quadCount());
		for (int i = 0; i < batch.vertexCount(); i++) {
			// Widened up and down, which is sideways for this viewer; not towards or away from them.
			assertEquals(0.1f, Math.abs(batch.position(i, 1)), 1e-5f);
			assertEquals(0f, batch.position(i, 2), 1e-5f);
			assertEquals(FxBatch.BEAM, batch.kind(i));
		}
		// The two quads share an edge, so the line has no gaps.
		assertEquals(0f, distance(position(batch, 3), position(batch, 4)), 1e-6f);
		assertEquals(0f, distance(position(batch, 2), position(batch, 5)), 1e-6f);
	}

	@Test
	void aTaperedLineIsThinnerAndFainterAtItsEnds() {
		FxBatch batch = new FxBatch();
		batch.eye(0f, 0f, -10f);
		batch.polyline(new float[] {-1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f}, 3, 0.1f, 0xFFFFFFFF, 1f, 0f, true);

		float endWidth = distance(position(batch, 0), position(batch, 1));
		float middleWidth = distance(position(batch, 2), position(batch, 3));
		assertTrue(endWidth < middleWidth * 0.8f, endWidth + " against " + middleWidth);
		assertTrue((batch.color(0) >>> 24) < (batch.color(2) >>> 24));
	}

	@Test
	void aSinglePointMakesNoLine() {
		FxBatch batch = new FxBatch();
		batch.polyline(new float[] {0f, 0f, 0f}, 1, 0.1f, 0xFFFFFFFF, 1f, 0f, false);

		assertTrue(batch.isEmpty());
	}

	@Test
	void aDotIsASquareOfItsRadiusFacingTheViewer() {
		FxBatch batch = new FxBatch();
		batch.eye(0f, 0f, 5f);
		batch.dot(0f, 0f, 0f, 0.5f, 0xFFFFFFFF, 0f);

		assertEquals(1, batch.quadCount());
		for (int i = 0; i < 4; i++) {
			assertEquals(0.5f, Math.abs(batch.position(i, 0)), 1e-5f);
			assertEquals(0.5f, Math.abs(batch.position(i, 1)), 1e-5f);
			assertEquals(0f, batch.position(i, 2), 1e-5f);
			assertEquals(FxBatch.DOT, batch.kind(i));
		}
	}

	@Test
	void aRingIsLaidOutLikeADotButSaysItIsARing() {
		FxBatch batch = new FxBatch();
		batch.viewDirection(0f, 0f, 1f);
		batch.ring(1f, 2f, 0f, 0.5f, 0xFF00FF00, 1f);

		assertEquals(1, batch.quadCount());
		for (int i = 0; i < 4; i++) {
			assertEquals(0.5f, Math.abs(batch.position(i, 0) - 1f), 1e-5f);
			assertEquals(0.5f, Math.abs(batch.position(i, 1) - 2f), 1e-5f);
			assertEquals(FxBatch.RING, batch.kind(i));
		}
	}

	@Test
	void aFlatQuadLiesWhereItIsPutWhateverTheViewer() {
		FxBatch batch = new FxBatch();
		batch.eye(5f, 5f, 5f);
		// On the ground: one unit along x, two along z.
		batch.flat(10f, 0f, 20f, 1f, 0f, 0f, 0f, 0f, 2f, 0xFF00FF00, 0.5f, FxBatch.RING);

		assertEquals(1, batch.quadCount());
		for (int i = 0; i < 4; i++) {
			assertEquals(1f, Math.abs(batch.position(i, 0) - 10f), 1e-6f);
			assertEquals(0f, batch.position(i, 1), 1e-6f);
			assertEquals(2f, Math.abs(batch.position(i, 2) - 20f), 1e-6f);
			assertEquals(FxBatch.RING, batch.kind(i));
		}
		// The corners go round, so that the shape's middle is the quad's middle.
		assertEquals(0f, batch.u(0));
		assertEquals(1f, batch.u(1));
		assertEquals(1f, batch.v(2));
		assertEquals(0f, batch.u(3));
	}

	@Test
	void aPlainQuadTakesItsCornersAndTheirColoursAsGiven() {
		FxBatch batch = new FxBatch();
		batch.plain(new float[] {0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f},
				new int[] {0xFF000001, 0xFF000002, 0xFF000003, 0xFF000004});

		assertEquals(1, batch.quadCount());
		assertEquals(1f, batch.position(1, 0));
		assertEquals(1f, batch.position(2, 1));
		assertEquals(0xFF000003, batch.color(2));
		assertEquals(FxBatch.PLAIN, batch.kind(3));
	}

	@Test
	void aStreakIsStretchedAlongItsFlight() {
		FxBatch batch = new FxBatch();
		batch.viewDirection(0f, 0f, 1f);
		batch.streak(0f, 0f, 0f, 2f, 0f, 0f, 0.5f, 0xFFFFFFFF, 0f);

		// Two units to either side along x, half a unit up and down.
		assertEquals(4f, distance(position(batch, 0), position(batch, 3)), 1e-5f);
		assertEquals(1f, distance(position(batch, 0), position(batch, 1)), 1e-5f);
	}

	@Test
	void aSparkFlyingStraightAtTheViewerIsRound() {
		FxBatch batch = new FxBatch();
		batch.viewDirection(0f, 0f, 1f);
		batch.streak(0f, 0f, 0f, 0f, 0f, 3f, 0.5f, 0xFFFFFFFF, 0f);

		assertEquals(1f, distance(position(batch, 0), position(batch, 3)), 1e-5f);
		assertEquals(1f, distance(position(batch, 0), position(batch, 1)), 1e-5f);
	}

	@Test
	void growsAsNeededAndStartsOverWhenCleared() {
		FxBatch batch = new FxBatch();
		batch.viewDirection(0f, 0f, 1f);
		for (int i = 0; i < 1000; i++) {
			batch.dot(i, 0f, 0f, 1f, 0xFFFFFFFF, 0f);
		}
		assertEquals(1000, batch.quadCount());
		assertEquals(999f, (batch.position(3996, 0) + batch.position(3998, 0)) / 2f, 1e-3f);

		batch.clear();
		assertTrue(batch.isEmpty());
	}
}
