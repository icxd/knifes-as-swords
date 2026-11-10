package io.github.icxd.knives_as_swords.finish;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The pattern seed's random numbers, and the texture transform they end up in. */
class PatternPlacementTest {
	@Test
	void theSameSeedGivesTheSameNumbers() {
		UniformRandom first = new UniformRandom(661);
		UniformRandom second = new UniformRandom(661);

		for (int i = 0; i < 100; i++) {
			assertEquals(first.between(0f, 1f), second.between(0f, 1f));
		}
	}

	@Test
	void differentSeedsGiveDifferentNumbers() {
		assertNotEquals(new UniformRandom(1).between(0f, 1f), new UniformRandom(2).between(0f, 1f));
		assertNotEquals(new UniformRandom(999).between(0f, 1f), new UniformRandom(1000).between(0f, 1f));
		// A quirk of this generator: it cannot start from zero and uses one instead.
		assertEquals(new UniformRandom(0).between(0f, 1f), new UniformRandom(1).between(0f, 1f));
	}

	@Test
	void numbersStayInRangeAndSpreadOut() {
		UniformRandom random = new UniformRandom(42);
		float sum = 0f;
		for (int i = 0; i < 10_000; i++) {
			float value = random.between(-55f, -65f);
			assertTrue(value <= -55f && value > -65f, "out of range: " + value);
			sum += value;
		}
		assertEquals(-60f, sum / 10_000, 0.2f);
	}

	@Test
	void aSeedPlacesThePatternWherePlayersKnowIt() {
		// A finish that lets the seed choose everything: any offset, any rotation.
		float[] placement = FinishPainter.placePattern(new UniformRandom(387), new float[] {0f, 1f},
				new float[] {0f, 1f}, new float[] {0f, 360f});

		// These are the numbers behind a famous knife: with them, one face of the blade is all blue.
		// A different generator, or drawing the three in another order, gives something else.
		assertEquals(0.0666f, placement[0], 1e-3f);
		assertEquals(0.7309f, placement[1], 1e-3f);
		assertEquals(264.53f, placement[2], 0.05f);
	}

	@Test
	void settingsAFinishDoesNotVaryStillUseUpTheirTurn() {
		float[] fixedOffset = FinishPainter.placePattern(new UniformRandom(387), new float[] {-0.7f, -0.7f},
				new float[] {-0.7f, -0.7f}, new float[] {0f, 360f});

		// The rotation is the third number drawn either way.
		assertEquals(-0.7f, fixedOffset[0]);
		assertEquals(-0.7f, fixedOffset[1]);
		assertEquals(264.53f, fixedOffset[2], 0.05f);
	}

	@Test
	void anUntouchedTransformChangesNothing() {
		PaintJob.Transform transform = PaintJob.Transform.of(0f, 1f, 0f, 0f);

		assertEquals(0.3f, transform.u(0.3f, 0.8f), 1e-6f);
		assertEquals(0.8f, transform.v(0.3f, 0.8f), 1e-6f);
	}

	@Test
	void offsetsShiftAndScalesRepeat() {
		PaintJob.Transform shifted = PaintJob.Transform.of(0f, 1f, 0.25f, -0.5f);
		assertEquals(0.55f, shifted.u(0.3f, 0.8f), 1e-6f);
		assertEquals(0.3f, shifted.v(0.3f, 0.8f), 1e-6f);

		// Twice the scale: the pattern passes twice as fast, so it repeats twice across the knife.
		PaintJob.Transform doubled = PaintJob.Transform.of(0f, 2f, 0f, 0f);
		assertEquals(2f, doubled.u(1f, 0f) - doubled.u(0f, 0f), 1e-6f);
		assertEquals(2f, doubled.v(0f, 1f) - doubled.v(0f, 0f), 1e-6f);
	}

	@Test
	void rotationTurnsTheAxes() {
		PaintJob.Transform turned = PaintJob.Transform.of(90f, 1f, 0f, 0f);

		// A quarter turn: moving along u on the knife moves along v in the pattern, and the other way round.
		assertEquals(0f, turned.u(1f, 0f) - turned.u(0f, 0f), 1e-6f);
		assertEquals(1f, Math.abs(turned.v(1f, 0f) - turned.v(0f, 0f)), 1e-6f);
		assertEquals(1f, Math.abs(turned.u(0f, 1f) - turned.u(0f, 0f)), 1e-6f);
	}

	@Test
	void knowsWhichStylesProjectAndWhichCarryPictures() {
		assertTrue(PaintJob.patternIsProjected(PaintJob.SPRAY));
		assertTrue(PaintJob.patternIsProjected(PaintJob.ANODIZED_AIRBRUSHED));
		assertTrue(!PaintJob.patternIsProjected(PaintJob.HYDROGRAPHIC));
		assertTrue(PaintJob.patternIsColor(PaintJob.CUSTOM));
		assertTrue(!PaintJob.patternIsColor(PaintJob.ANODIZED_MULTICOLORED));
	}
}
