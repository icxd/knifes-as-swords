package io.github.icxd.knives_as_swords.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class BladeTrailTest {
	private static final float BLADE = 0.2f;

	/** A blade pointing up whose base is at {@code x}, at the given moment. */
	private static void add(BladeTrail trail, float time, float x) {
		trail.add(time, new Vector3f(x, 0f, 0f), new Vector3f(x, BLADE, 0f));
	}

	private static FxBatch build(BladeTrail trail, float now) {
		FxBatch batch = new FxBatch();
		trail.build(batch, now, 0.3f, 2f, BLADE, life -> FxMath.mix(0xFFFF0000, 0xFF0000FF, life), 1f, 0.5f, 0.5f,
				0f, 0f, 0f);
		return batch;
	}

	@Test
	void aBladeHeldStillLeavesNoTrail() {
		BladeTrail trail = new BladeTrail();
		for (int i = 0; i <= 30; i++) {
			add(trail, i * 0.01f, 0f);
		}

		assertEquals(0f, trail.tipSpeed());
		assertTrue(build(trail, 0.3f).isEmpty());
	}

	@Test
	void aFastBladeLeavesABandBehindIt() {
		BladeTrail trail = new BladeTrail();
		// Five metres per second along x.
		for (int i = 0; i <= 20; i++) {
			add(trail, i * 0.01f, i * 0.05f);
		}

		assertEquals(5f, trail.tipSpeed(), 0.01f);
		float[] velocity = new float[3];
		trail.tipVelocity(velocity);
		assertEquals(5f, velocity[0], 0.01f);
		assertEquals(0f, velocity[1], 0.01f);

		FxBatch batch = build(trail, 0.2f);
		assertEquals(20, batch.quadCount());
		// Full strength next to the blade, in the head colour; fading and changing colour behind it.
		assertEquals(0xFF, batch.color(0) >>> 24);
		assertEquals(0xFF0000, batch.color(0) & 0xFFFFFF);
		int last = batch.vertexCount() - 1;
		assertTrue((batch.color(last) >>> 24) < 0x40, "tail alpha " + (batch.color(last) >>> 24));
		assertTrue((batch.color(last) & 0xFF) > 0x80, "tail colour " + Integer.toHexString(batch.color(last)));
		// The band's texture is laid out in blade lengths travelled.
		assertEquals(1f / BLADE, batch.v(0) - batch.v(last), 0.01f);
	}

	@Test
	void theBandNarrowsTowardsTheTipsPathAsItAges() {
		BladeTrail trail = new BladeTrail();
		for (int i = 0; i <= 20; i++) {
			add(trail, i * 0.01f, i * 0.05f);
		}
		FxBatch batch = build(trail, 0.2f);

		// Vertices come as base, tip, tip, base: the newest base is at the blade's base, the oldest higher up.
		assertEquals(0f, batch.position(0, 1), 1e-6f);
		int oldestBase = batch.vertexCount() - 1;
		assertTrue(batch.position(oldestBase, 1) > 0.05f);
		assertEquals(BLADE, batch.position(oldestBase - 1, 1), 1e-6f);
	}

	@Test
	void canBeDrawnSomewhereElseThanItWasRecorded() {
		BladeTrail trail = new BladeTrail();
		for (int i = 0; i <= 20; i++) {
			add(trail, i * 0.01f, i * 0.05f);
		}
		FxBatch here = build(trail, 0.2f);
		FxBatch there = new FxBatch();
		trail.build(there, 0.2f, 0.3f, 2f, BLADE, life -> 0xFFFFFFFF, 1f, 0.5f, 0.5f, 10f, -2f, 0.5f);

		assertEquals(here.vertexCount(), there.vertexCount());
		for (int i = 0; i < here.vertexCount(); i++) {
			assertEquals(here.position(i, 0) + 10f, there.position(i, 0), 1e-5f);
			assertEquals(here.position(i, 1) - 2f, there.position(i, 1), 1e-5f);
			assertEquals(here.position(i, 2) + 0.5f, there.position(i, 2), 1e-5f);
		}
	}

	@Test
	void whatIsOldDropsOut() {
		BladeTrail trail = new BladeTrail();
		for (int i = 0; i <= 20; i++) {
			add(trail, i * 0.01f, i * 0.05f);
		}

		assertTrue(build(trail, 0.6f).isEmpty());
	}

	@Test
	void aKnifeThatWasAwayDoesNotJoinUpWithWhereItWasBefore() {
		BladeTrail trail = new BladeTrail();
		for (int i = 0; i <= 5; i++) {
			add(trail, i * 0.01f, i * 0.05f);
		}
		// A moment later it is somewhere else entirely, while the old band has not yet faded.
		for (int i = 0; i <= 5; i++) {
			add(trail, 0.2f + i * 0.01f, 3f + i * 0.05f);
		}
		FxBatch batch = build(trail, 0.25f);

		assertEquals(10, batch.quadCount());
		for (int quad = 0; quad < batch.quadCount(); quad++) {
			float lowest = Float.POSITIVE_INFINITY;
			float highest = Float.NEGATIVE_INFINITY;
			for (int i = quad * 4; i < quad * 4 + 4; i++) {
				lowest = Math.min(lowest, batch.position(i, 0));
				highest = Math.max(highest, batch.position(i, 0));
			}
			assertTrue(highest - lowest < 1f, "a quad bridges the gap: " + lowest + " to " + highest);
		}
	}

	@Test
	void keepsOnlySoMuchAndCanBeEmptied() {
		BladeTrail trail = new BladeTrail();
		for (int i = 0; i < 10_000; i++) {
			add(trail, i * 0.001f, i * 0.005f);
		}
		assertTrue(trail.size() < 1000);
		assertEquals(5f, trail.tipSpeed(), 0.05f);

		trail.clear();
		assertEquals(0, trail.size());
		assertTrue(build(trail, 10f).isEmpty());
	}

	@Test
	void ignoresAMomentThatIsNotLaterThanTheLast() {
		BladeTrail trail = new BladeTrail();
		add(trail, 1f, 0f);
		add(trail, 1f, 5f);
		add(trail, 0.5f, 9f);

		assertEquals(1, trail.size());
	}
}
