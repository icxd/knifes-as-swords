package io.github.icxd.knives_as_swords.visuals.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClickCounterTest {
	@Test
	void countsTheClicksOfTheLastSecondOnly() {
		// Clicks at 0 s, 0.5 s and 1.2 s.
		ArrayDeque<Long> clicks = new ArrayDeque<>(List.of(0L, 500_000_000L, 1_200_000_000L));

		assertEquals(2, ClickCounter.count(clicks, 1_400_000_000L));
		assertEquals(1, ClickCounter.count(clicks, 2_000_000_000L));
		assertEquals(0, ClickCounter.count(clicks, 5_000_000_000L));
		assertEquals(0, ClickCounter.count(new ArrayDeque<>(), 5_000_000_000L));
	}
}
