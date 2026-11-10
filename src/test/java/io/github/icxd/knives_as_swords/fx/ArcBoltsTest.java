package io.github.icxd.knives_as_swords.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class ArcBoltsTest {
	/** A blade 20 cm long: a strip of points along +Y facing +X. */
	private static BladeShape blade() {
		int count = 40;
		float[] positions = new float[count * 3];
		float[] normals = new float[count * 3];
		for (int i = 0; i < count; i++) {
			positions[i * 3 + 1] = 0.2f * i / (count - 1);
			positions[i * 3 + 2] = i % 2 == 0 ? -0.01f : 0.01f;
			normals[i * 3] = 1f;
		}
		return BladeShape.of(positions, normals, new int[0], new float[0], false);
	}

	private static FxBatch bolts(float time, int seed, float density, float energy, Matrix4f transform) {
		FxBatch batch = new FxBatch();
		batch.eye(2f, 0.1f, 0f);
		ArcBolts.build(batch, blade(), transform, time, seed, density, energy, 0xFF80C0FF, 1f, 0.5f, 1f);
		return batch;
	}

	private static int boltsOverTime(int seed, float density, float energy) {
		int total = 0;
		for (int frame = 0; frame < 400; frame++) {
			FxBatch batch = new FxBatch();
			batch.eye(2f, 0.1f, 0f);
			total += ArcBolts.build(batch, blade(), null, frame * 0.013f, seed, density, energy, 0xFFFFFFFF, 1f, 0.5f,
					1f);
		}
		return total;
	}

	@Test
	void theSameMomentAlwaysLooksTheSame() {
		FxBatch first = bolts(3.21f, 7, 1f, 1f, null);
		FxBatch second = bolts(3.21f, 7, 1f, 1f, null);

		assertTrue(first.quadCount() > 0);
		assertEquals(first.vertexCount(), second.vertexCount());
		for (int i = 0; i < first.vertexCount(); i++) {
			assertEquals(first.position(i, 0), second.position(i, 0));
			assertEquals(first.position(i, 1), second.position(i, 1));
			assertEquals(first.color(i), second.color(i));
		}
	}

	@Test
	void differentKnivesCrackleIndependently() {
		int same = 0;
		for (int frame = 0; frame < 50; frame++) {
			FxBatch one = bolts(frame * 0.05f, 1, 1f, 1f, null);
			FxBatch other = bolts(frame * 0.05f, 2, 1f, 1f, null);
			if (!one.isEmpty() && one.vertexCount() == other.vertexCount()
					&& one.position(0, 1) == other.position(0, 1)) {
				same++;
			}
		}

		assertEquals(0, same);
	}

	@Test
	void withoutDensityThereAreNone() {
		assertEquals(0, boltsOverTime(5, 0f, 1f));
	}

	@Test
	void anExcitedBladeCracklesMuchMoreThanARestingOne() {
		int resting = boltsOverTime(5, 1f, 0f);
		int excited = boltsOverTime(5, 1f, 1f);

		assertTrue(resting > 0, "a resting blade should still show the odd bolt");
		assertTrue(excited > resting * 4, excited + " against " + resting);
	}

	@Test
	void moreDensityMeansMoreBolts() {
		assertTrue(boltsOverTime(5, 1f, 1f) > boltsOverTime(5, 0.2f, 1f) * 2);
	}

	@Test
	void boltsStayCloseToTheBlade() {
		for (int frame = 0; frame < 200; frame++) {
			FxBatch batch = bolts(frame * 0.017f, 3, 1f, 1f, null);
			for (int i = 0; i < batch.vertexCount(); i++) {
				// Within the blade's length of it in every direction: they leap off, but not away.
				assertTrue(Math.abs(batch.position(i, 0)) < 0.25f, "x " + batch.position(i, 0));
				assertTrue(batch.position(i, 1) > -0.2f && batch.position(i, 1) < 0.4f, "y " + batch.position(i, 1));
				assertTrue(Math.abs(batch.position(i, 2)) < 0.25f, "z " + batch.position(i, 2));
			}
		}
	}

	@Test
	void theyGoWhereTheBladeGoes() {
		FxBatch still = bolts(1.5f, 9, 1f, 1f, null);
		FxBatch moved = new FxBatch();
		// The viewer moves along too, so that everything is the same except for the place.
		moved.eye(2f + 10f, 0.1f, 0f);
		ArcBolts.build(moved, blade(), new Matrix4f().translation(10f, 0f, 0f), 1.5f, 9, 1f, 1f, 0xFF80C0FF, 1f, 0.5f,
				1f);

		assertTrue(still.quadCount() > 0);
		assertEquals(still.vertexCount(), moved.vertexCount());
		for (int i = 0; i < still.vertexCount(); i++) {
			assertEquals(still.position(i, 0) + 10f, moved.position(i, 0), 1e-4f);
			assertEquals(still.position(i, 1), moved.position(i, 1), 1e-4f);
		}
	}

	@Test
	void aBoltIsBrightestAsItStrikes() {
		// Follow one moment's bolts for a short while: none of them gets brighter on average.
		int brighter = 0;
		int dimmer = 0;
		for (int start = 0; start < 300; start++) {
			float time = start * 0.031f;
			FxBatch now = bolts(time, 4, 0.3f, 1f, null);
			FxBatch soon = bolts(time + 0.02f, 4, 0.3f, 1f, null);
			if (now.quadCount() == 8 && soon.quadCount() == 8) {
				// One bolt in both: compare its middle.
				int middle = 16;
				if ((soon.color(middle) >>> 24) > (now.color(middle) >>> 24)) {
					brighter++;
				} else {
					dimmer++;
				}
			}
		}

		assertTrue(dimmer > brighter, dimmer + " dimmer, " + brighter + " brighter");
	}
}
