package io.github.icxd.knives_as_swords.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class EaseTest {
	@AfterEach
	void forget() {
		Ease.forget();
	}

	@Test
	void aStepGoesPartOfTheWayAndNeverPastTheTarget() {
		float value = 0f;
		float before;
		for (int frame = 0; frame < 200; frame++) {
			before = value;
			value = Ease.step(value, 1f, 16f, 1f / 60f);
			assertTrue(value >= before && value <= 1f, "frame " + frame + ": " + before + " -> " + value);
		}
		// It does arrive, rather than creeping closer for ever.
		assertEquals(1f, value);
	}

	@Test
	void goesDownAsWellAsUp() {
		float value = Ease.step(1f, 0f, 16f, 1f / 60f);

		assertTrue(value < 1f && value > 0f);
	}

	@Test
	void howFarItGetsDoesNotDependOnTheFrameRate() {
		float slowFrames = Ease.step(0f, 1f, 10f, 0.05f);
		float fastFrames = 0f;
		for (int frame = 0; frame < 5; frame++) {
			fastFrames = Ease.step(fastFrames, 1f, 10f, 0.01f);
		}

		assertEquals(slowFrames, fastFrames, 1e-4f);
	}

	@Test
	void noTimeNoMovement() {
		assertEquals(0.25f, Ease.step(0.25f, 1f, 16f, 0f));
	}

	@Test
	void aNumberIsAtItsTargetTheFirstTimeItIsAskedFor() {
		assertEquals(1f, Ease.toward("fresh", 1f, 16f));
		assertEquals(1f, Ease.at("fresh", 0f));
	}

	@Test
	void aNumberNobodyAskedForYetIsWhereTheCallerSays() {
		assertEquals(0.5f, Ease.at("unknown", 0.5f));
	}

	@Test
	void aNumberPutSomewhereStaysThereUntilItIsMoved() {
		Ease.set("put", 0.25f);

		assertEquals(0.25f, Ease.at("put", 1f));
	}

	@Test
	void forgettingStartsOver() {
		Ease.set("gone", 0.25f);
		Ease.forget();

		assertEquals(1f, Ease.at("gone", 1f));
	}

	@Test
	void easingOutStartsFastAndEndsExactly() {
		assertEquals(0f, Ease.out(0f));
		assertEquals(1f, Ease.out(1f));
		assertTrue(Ease.out(0.5f) > 0.5f);
		// Outside 0 to 1 there is nothing more to do.
		assertEquals(0f, Ease.out(-3f));
		assertEquals(1f, Ease.out(7f));
		float last = 0f;
		for (int i = 1; i <= 16; i++) {
			float now = Ease.out(i / 16f);
			assertTrue(now >= last);
			last = now;
		}
	}
}
