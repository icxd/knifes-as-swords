package io.github.icxd.knives_as_swords.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SparkSystemTest {
	@Test
	void sparksFlyWhereTheyAreThrown() {
		SparkSystem sparks = new SparkSystem(8);
		sparks.emit(1f, 2f, 3f, 4f, 0f, -2f, 1f, 0.01f, 0xFFFFFFFF, 1f);

		sparks.advance(0.25f, 0f, 0f, 0f);

		assertEquals(1, sparks.count());
		assertEquals(2f, sparks.x(0), 1e-5f);
		assertEquals(2f, sparks.y(0), 1e-5f);
		assertEquals(2.5f, sparks.z(0), 1e-5f);
		assertEquals(0.25f, sparks.age(0), 1e-6f);
	}

	@Test
	void gravityPullsThemDownOrLetsThemRise() {
		SparkSystem falling = new SparkSystem(8);
		falling.emit(0f, 0f, 0f, 0f, 0f, 0f, 5f, 0.01f, 0xFFFFFFFF, 1f);
		SparkSystem rising = new SparkSystem(8);
		rising.emit(0f, 0f, 0f, 0f, 0f, 0f, 5f, 0.01f, 0xFFFFFFFF, 1f);

		for (int i = 0; i < 100; i++) {
			falling.advance(0.01f, -10f, 0f, 0f);
			rising.advance(0.01f, 2f, 0f, 0f);
		}

		// Half of the acceleration times the time squared, give or take the small steps.
		assertEquals(-5f, falling.y(0), 0.1f);
		assertEquals(1f, rising.y(0), 0.05f);
	}

	@Test
	void downCanBeAnyDirection() {
		SparkSystem sparks = new SparkSystem(8);
		sparks.emit(0f, 0f, 0f, 0f, 0f, 0f, 5f, 0.01f, 0xFFFFFFFF, 1f);

		for (int i = 0; i < 100; i++) {
			sparks.advance(0.01f, 4f, 0f, -2f, 0f, 0f);
		}

		assertEquals(2f, sparks.x(0), 0.05f);
		assertEquals(0f, sparks.y(0), 1e-6f);
		assertEquals(-1f, sparks.z(0), 0.05f);
	}

	@Test
	void dragSlowsThemDown() {
		SparkSystem free = new SparkSystem(8);
		free.emit(0f, 0f, 0f, 10f, 0f, 0f, 5f, 0.01f, 0xFFFFFFFF, 1f);
		SparkSystem held = new SparkSystem(8);
		held.emit(0f, 0f, 0f, 10f, 0f, 0f, 5f, 0.01f, 0xFFFFFFFF, 1f);

		for (int i = 0; i < 100; i++) {
			free.advance(0.01f, 0f, 0f, 0f);
			held.advance(0.01f, 0f, 3f, 0f);
		}

		assertEquals(10f, free.x(0), 1e-3f);
		assertTrue(held.x(0) < 4f, "went " + held.x(0));
	}

	@Test
	void theyBurnOut() {
		SparkSystem sparks = new SparkSystem(8);
		sparks.emit(0f, 0f, 0f, 0f, 0f, 0f, 0.5f, 0.01f, 0xFFFFFFFF, 1f);
		sparks.emit(7f, 0f, 0f, 0f, 0f, 0f, 2f, 0.01f, 0xFFFFFFFF, 1f);

		sparks.advance(1f, 0f, 0f, 0f);

		assertEquals(1, sparks.count());
		assertEquals(7f, sparks.x(0));
	}

	@Test
	void neverHoldsMoreThanItHasRoomFor() {
		SparkSystem sparks = new SparkSystem(16);
		for (int i = 0; i < 1000; i++) {
			sparks.emit(i, 0f, 0f, 0f, 0f, 0f, 1f, 0.01f, 0xFFFFFFFF, 1f);
		}

		assertEquals(16, sparks.count());
		sparks.clear();
		assertEquals(0, sparks.count());
	}

	@Test
	void swirlingSendsEqualSparksDifferentWays() {
		SparkSystem sparks = new SparkSystem(8);
		sparks.emit(0f, 0f, 0f, 0f, 0f, 0f, 5f, 0.01f, 0xFFFFFFFF, 1f);
		sparks.emit(0f, 0f, 0f, 0f, 0f, 0f, 5f, 0.01f, 0xFFFFFFFF, 1f);

		for (int i = 0; i < 100; i++) {
			sparks.advance(0.01f, 0f, 0f, 4f);
		}

		assertTrue(Math.abs(sparks.x(0) - sparks.x(1)) + Math.abs(sparks.z(0) - sparks.z(1)) > 0.01f);
	}

	@Test
	void drawsEachSparkAsALightThatDimsAsItBurns() {
		SparkSystem sparks = new SparkSystem(8);
		sparks.emit(0f, 0f, 0f, 0f, 0f, 0f, 1f, 0.01f, 0xFFFF8000, 1f);
		FxBatch young = new FxBatch();
		young.viewDirection(0f, 0f, 1f);
		FxBatch old = new FxBatch();
		old.viewDirection(0f, 0f, 1f);

		sparks.advance(0.1f, 0f, 0f, 0f);
		sparks.build(young, 0f, 0f, 0f, 0.03f, 1f);
		sparks.advance(0.8f, 0f, 0f, 0f);
		sparks.build(old, 5f, 0f, 0f, 0.03f, 1f);

		assertEquals(1, young.quadCount());
		assertEquals(0xFF8000, young.color(0) & 0xFFFFFF);
		assertTrue((old.color(0) >>> 24) < (young.color(0) >>> 24));
		// Drawn where it is, moved by the offset asked for.
		assertEquals(5f, (old.position(0, 0) + old.position(2, 0)) / 2f, 1e-4f);
	}
}
