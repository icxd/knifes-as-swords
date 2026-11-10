package io.github.icxd.knives_as_swords.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GradientTest {
	@Test
	void blendsFromOneColourToTheNext() {
		Gradient gradient = Gradient.evenly(0x000000, 0xFFFFFF);

		assertEquals(0xFF000000, gradient.at(0f));
		assertEquals(0xFFFFFFFF, gradient.at(1f));
		assertEquals(0xFF808080, gradient.at(0.5f));
		assertEquals(0xFF404040, gradient.at(0.25f));
	}

	@Test
	void coloursSitWhereTheyArePut() {
		Gradient gradient = Gradient.of(List.of(new Gradient.Stop(0.25f, 0xFF0000), new Gradient.Stop(0.75f, 0x0000FF)));

		// Before the first colour and after the last there is nothing to blend with.
		assertEquals(0xFFFF0000, gradient.at(0f));
		assertEquals(0xFFFF0000, gradient.at(0.25f));
		assertEquals(0xFF0000FF, gradient.at(0.75f));
		assertEquals(0xFF0000FF, gradient.at(1f));
		assertEquals(0xFF800080, gradient.at(0.5f));
	}

	@Test
	void theOrderColoursAreGivenInDoesNotMatter() {
		Gradient forwards = Gradient.of(List.of(new Gradient.Stop(0f, 0x112233), new Gradient.Stop(0.5f, 0x445566),
				new Gradient.Stop(1f, 0x778899)));
		Gradient jumbled = Gradient.of(List.of(new Gradient.Stop(1f, 0x778899), new Gradient.Stop(0f, 0x112233),
				new Gradient.Stop(0.5f, 0x445566)));

		assertEquals(forwards, jumbled);
		assertEquals(forwards.hashCode(), jumbled.hashCode());
		assertEquals(List.of(new Gradient.Stop(0f, 0x112233), new Gradient.Stop(0.5f, 0x445566),
				new Gradient.Stop(1f, 0x778899)), jumbled.stops());
		assertNotEquals(forwards, Gradient.evenly(0x112233, 0x778899));
	}

	@Test
	void oneColourIsThatColourEverywhereAndNoneIsWhite() {
		Gradient plain = Gradient.evenly(0x33CC66);
		assertEquals(0xFF33CC66, plain.at(0f));
		assertEquals(0xFF33CC66, plain.at(0.7f));
		assertEquals(1, plain.size());

		assertEquals(0xFFFFFFFF, Gradient.of(List.of()).at(0.3f));
	}

	@Test
	void oddPlacesAreBroughtIntoRangeAndTooManyColoursCutOff() {
		Gradient gradient = Gradient.of(List.of(new Gradient.Stop(-3f, 0xFF0000), new Gradient.Stop(Float.NaN, 0x00FF00),
				new Gradient.Stop(9f, 0x0000FF)));
		assertEquals(0f, gradient.stops().get(0).at());
		assertEquals(1f, gradient.stops().get(2).at());
		// Not a number at all: asking for a colour there gives the first one rather than failing.
		assertEquals(gradient.at(0f), gradient.at(Float.NaN));

		List<Gradient.Stop> many = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			many.add(new Gradient.Stop(i / 29f, i));
		}
		assertEquals(Gradient.MAX_STOPS, Gradient.of(many).size());
	}

	@Test
	void twoColoursAtOnePlaceMakeAHardEdge() {
		Gradient gradient = Gradient.of(List.of(new Gradient.Stop(0f, 0xFF0000), new Gradient.Stop(0.5f, 0xFF0000),
				new Gradient.Stop(0.5f, 0x0000FF), new Gradient.Stop(1f, 0x0000FF)));

		assertEquals(0xFFFF0000, gradient.at(0.49f));
		assertEquals(0xFF0000FF, gradient.at(0.51f));
	}

	@Test
	void runsBackAndForthBeyondItsEnd() {
		Gradient gradient = Gradient.evenly(0x000000, 0xFFFFFF);

		assertEquals(gradient.at(0.25f), gradient.backAndForth(0.25f));
		// Past the end it comes back the way it went, so there is never a jump.
		assertEquals(gradient.at(0.25f), gradient.backAndForth(1.75f));
		assertEquals(gradient.at(0.25f), gradient.backAndForth(2.25f));
		assertEquals(gradient.at(0.25f), gradient.backAndForth(-0.25f));
		assertEquals(gradient.at(1f), gradient.backAndForth(1f));
		assertEquals(gradient.at(0f), gradient.backAndForth(2f));
		int before = gradient.backAndForth(0.999f) & 0xFF;
		int after = gradient.backAndForth(1.001f) & 0xFF;
		assertTrue(Math.abs(before - after) <= 1);
	}

	@Test
	void coloursAreWrittenAndReadAsHex() {
		assertEquals("#33F2FF", Gradient.hex(0x33F2FF));
		assertEquals("#00000A", Gradient.hex(0xFF00000A));

		assertEquals(0x33F2FF, Gradient.parse("#33F2FF"));
		assertEquals(0x33F2FF, Gradient.parse(" 33f2ff "));
		assertEquals(-1, Gradient.parse("#33F2F"));
		assertEquals(-1, Gradient.parse("#GGGGGG"));
		assertEquals(-1, Gradient.parse("red"));
		assertEquals(-1, Gradient.parse(null));
	}

	@Test
	void hueSaturationAndBrightnessGoBothWays() {
		assertEquals(0xFF0000, Gradient.fromHsv(0f, 1f, 1f));
		assertEquals(0x00FF00, Gradient.fromHsv(1f / 3f, 1f, 1f));
		assertEquals(0xFFFFFF, Gradient.fromHsv(0.4f, 0f, 1f));
		assertEquals(0x000000, Gradient.fromHsv(0.4f, 1f, 0f));
		assertEquals(0x800000, Gradient.fromHsv(0f, 1f, 0.5f));
		assertEquals(0xFF8080, Gradient.fromHsv(0f, 0.5f, 1f));

		for (int rgb : new int[] {0x33F2FF, 0xB838FF, 0xFF7314, 0x123456, 0x808080, 0x000000, 0xFFFFFF}) {
			float[] hsv = Gradient.toHsv(rgb);
			int back = Gradient.fromHsv(hsv[0], hsv[1], hsv[2]);
			for (int shift = 0; shift <= 16; shift += 8) {
				assertTrue(Math.abs(((rgb >> shift) & 0xFF) - ((back >> shift) & 0xFF)) <= 1,
						Gradient.hex(rgb) + " came back as " + Gradient.hex(back));
			}
		}
		assertEquals(0f, Gradient.toHsv(0x808080)[1]);
		assertEquals(1f, Gradient.toHsv(0x00FF00)[2]);
	}
}
