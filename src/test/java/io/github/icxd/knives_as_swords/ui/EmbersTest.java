package io.github.icxd.knives_as_swords.ui;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EmbersTest {
	@Test
	void everyLightStaysOnOrJustBesideTheScreen() {
		Embers embers = new Embers(40, 7);
		for (int n = 0; n < 40; n++) {
			for (float time = 0f; time < 120f; time += 0.37f) {
				float[] place = embers.place(n, time);
				assertTrue(place[0] >= -0.03f && place[0] <= 1.03f, "across: " + place[0]);
				assertTrue(place[1] >= 0f && place[1] <= 1f, "down: " + place[1]);
			}
		}
	}

	@Test
	void lightsRise() {
		Embers embers = new Embers(40, 7);
		for (int n = 0; n < 40; n++) {
			float before = embers.place(n, 10f)[1];
			float after = embers.place(n, 10.1f)[1];
			// Higher up, unless it has just started again from the bottom.
			assertTrue(after < before || after > 0.9f, "light " + n + ": " + before + " -> " + after);
		}
	}

	@Test
	void whereALightIsFollowsFromTheTimeAlone() {
		assertArrayEquals(new Embers(40, 7).place(3, 12.5f), new Embers(40, 7).place(3, 12.5f));
	}

	@Test
	void lightsDoNotAllSitInOneLane() {
		Embers embers = new Embers(40, 7);
		float least = 1f;
		float most = 0f;
		for (int n = 0; n < 40; n++) {
			float across = embers.place(n, 0f)[0];
			least = Math.min(least, across);
			most = Math.max(most, across);
		}

		assertTrue(most - least > 0.6f, "spread: " + least + " to " + most);
	}
}
