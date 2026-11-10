package io.github.icxd.knives_as_swords.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.icxd.knives_as_swords.fx.Gradient;
import io.github.icxd.knives_as_swords.visuals.Visual;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ThemeTest {
	@Test
	void everyCategoryHasColoursOfItsOwn() {
		Set<Gradient> seen = new HashSet<>();
		for (Visual.Category category : Visual.Category.values()) {
			assertTrue(seen.add(Theme.colors(category)), category + " shares its colours with another");
		}
	}

	@Test
	void aLightsColourIsOpaqueAndComesFromItsGradient() {
		Gradient colors = Gradient.evenly(0xFF0000, 0x0000FF);

		assertEquals(0xFFFF0000, Theme.now(colors, 0f, 0f));
		for (float time = 0f; time < 60f; time += 1.3f) {
			int color = Theme.now(colors, time, 0.25f);
			assertEquals(0xFF, color >>> 24);
			// Between red and blue there is no green.
			assertEquals(0, (color >> 8) & 0xFF);
		}
	}

	@Test
	void lightsWanderAndNeighboursDiffer() {
		Gradient colors = Gradient.evenly(0xFF0000, 0x0000FF);

		assertNotEquals(Theme.now(colors, 0f, 0f), Theme.now(colors, 3f, 0f));
		assertNotEquals(Theme.now(colors, 0f, 0f), Theme.now(colors, 0f, 0.5f));
	}

	@Test
	void theClockRunsForwards() {
		float before = Theme.seconds();

		assertTrue(before >= 0f);
		assertTrue(Theme.seconds() >= before);
	}
}
