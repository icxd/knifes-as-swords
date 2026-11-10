package io.github.icxd.knives_as_swords.visuals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.icxd.knives_as_swords.fx.Gradient;
import io.github.icxd.knives_as_swords.visuals.world.Ambience;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Visuals and their settings: what they hold, and how they go into the config file and come back. */
class VisualTest {
	/** A visual with one of every kind of setting. */
	private static final class Sample extends Visual {
		final Setting.Toggle ring = toggle("ring", true);
		final Setting.Slider size = slider("size", 1f, 0.5f, 2f, 0.1f);
		final Setting.Choice style = choice("style", "burst", "pillar", "bolts");
		final Setting.Color color = color("color", 0x3FC8FF);
		final Setting.Colors colors = colors("colors", 0xFF0000, 0x0000FF);
		int ran;
		final Setting.Action open = action("open", () -> ran++);
		int switches;

		Sample(boolean onAtFirst) {
			super("sample", Category.COMBAT, onAtFirst);
		}

		@Override
		protected void switched() {
			switches++;
		}
	}

	@Test
	void whatIsWrittenComesBack() {
		Sample first = new Sample(false);
		first.set(true);
		first.ring.set(false);
		first.size.set(1.7f);
		first.style.step(true);
		first.color.set(0x123456);
		first.colors.set(Gradient.of(List.of(new Gradient.Stop(0f, 0x00FF00), new Gradient.Stop(0.25f, 0xFFFFFF),
				new Gradient.Stop(1f, 0x000000))));

		Map<String, String> written = first.write();
		Sample second = new Sample(false);
		second.read(written);

		assertTrue(second.on());
		assertFalse(second.ring.on());
		assertEquals(1.7f, second.size.get(), 1e-6f);
		assertEquals("pillar", second.style.get());
		assertEquals(0x123456, second.color.rgb());
		assertEquals(first.colors.get(), second.colors.get());
		// Something to do is not something to save.
		assertNull(first.open.write());
		assertFalse(written.containsKey("open"));
	}

	@Test
	void whatTheFileDoesNotMentionIsAsAtFirst() {
		Sample visual = new Sample(true);
		visual.set(false);
		visual.size.set(2f);
		visual.style.step(false);

		visual.read(Map.of());

		assertTrue(visual.on());
		assertEquals(1f, visual.size.get());
		assertEquals("burst", visual.style.get());
		assertTrue(visual.ring.on());

		Sample off = new Sample(false);
		off.read(Map.of("size", "0.5"));
		assertFalse(off.on());
		assertEquals(0.5f, off.size.get());
	}

	@Test
	void nonsenseInTheFileChangesNothing() {
		Sample visual = new Sample(true);
		Map<String, String> nonsense = new HashMap<>();
		nonsense.put("enabled", "maybe");
		nonsense.put("ring", "yes");
		nonsense.put("size", "big");
		nonsense.put("style", "fireworks");
		nonsense.put("color", "blue");
		nonsense.put("colors", "0.0:#FF0000,oops");

		visual.read(nonsense);

		assertTrue(visual.on());
		assertTrue(visual.ring.on());
		assertEquals(1f, visual.size.get());
		assertEquals("burst", visual.style.get());
		assertEquals(0x3FC8FF, visual.color.rgb());
		assertEquals(Gradient.evenly(0xFF0000, 0x0000FF), visual.colors.get());
	}

	@Test
	void switchingIsNoticedOnceAndOnlyWhenItChangesSomething() {
		Sample visual = new Sample(false);
		visual.set(false);
		assertEquals(0, visual.switches);
		visual.set(true);
		visual.set(true);
		assertEquals(1, visual.switches);
		visual.read(Map.of("enabled", "false"));
		assertEquals(2, visual.switches);
	}

	@Test
	void slidersStayWithinTheirLimitsAndOnTheirSteps() {
		Setting.Slider slider = new Setting.Slider("size", 1f, 0.5f, 2f, 0.1f);

		slider.set(9f);
		assertEquals(2f, slider.get());
		slider.set(-3f);
		assertEquals(0.5f, slider.get());
		slider.set(1.234f);
		assertEquals(1.2f, slider.get(), 1e-6f);
		assertEquals("1.2", slider.text());
		slider.set(Float.NaN);
		assertEquals(1.2f, slider.get(), 1e-6f);

		slider.setFraction(0.5f);
		// Half way is 1.25, which is between two steps; either neighbour will do.
		assertEquals(1.25f, slider.get(), 0.051f);
		assertEquals(0.5f, slider.fraction(), 0.04f);
		slider.setFraction(7f);
		assertEquals(1f, slider.fraction());

		// Whole steps are shown without decimals, fine ones with two.
		assertEquals("5", new Setting.Slider("length", 5f, 1f, 16f, 1f).text());
		assertEquals("0.30", new Setting.Slider("cycle", 0.3f, 0f, 1f, 0.05f).text());
	}

	@Test
	void choicesGoRoundAndRound() {
		Setting.Choice choice = new Setting.Choice("style", "halo", "hat", "wisps");

		assertTrue(choice.is("halo"));
		choice.step(false);
		assertEquals("wisps", choice.get());
		choice.step(true);
		choice.step(true);
		assertEquals("hat", choice.get());
		choice.reset();
		assertEquals("halo", choice.get());
		assertThrows(IllegalArgumentException.class, () -> new Setting.Choice("empty"));
	}

	@Test
	void aColourKeepsItsHueWhenSlidToGreyOrBlack() {
		Setting.Color color = new Setting.Color("color", 0xFF0000);

		color.setPart(2, 0f);
		assertEquals(0x000000, color.rgb());
		color.setPart(2, 1f);
		assertEquals(0xFF0000, color.rgb());
		color.setPart(1, 0f);
		assertEquals(0xFFFFFF, color.rgb());
		color.setPart(1, 1f);
		assertEquals(0xFF0000, color.rgb());

		color.setPart(0, 1f / 3f);
		assertEquals(0x00FF00, color.rgb());
		assertEquals(0x8000FF00, color.argb(0.5f));
		assertEquals("#00FF00", color.write());
	}

	@Test
	void settingsCanBeOfferedOnlyWhenTheyMakeSense() {
		Sample visual = new Sample(true);
		visual.size.onlyWhen(visual.ring::on);

		assertTrue(visual.size.shown());
		visual.ring.set(false);
		assertFalse(visual.size.shown());
	}

	@Test
	void twoSettingsCannotShareAName() {
		assertThrows(IllegalArgumentException.class, () -> new Visual("clash", Visual.Category.HUD, true) {
			final Setting.Toggle first = toggle("same", true);
			final Setting.Toggle second = toggle("same", false);
		});
		assertThrows(IllegalArgumentException.class, () -> new Visual("taken", Visual.Category.HUD, true) {
			final Setting.Toggle enabled = toggle("enabled", true);
		});
	}

	@Test
	void theListKnowsItsVisualsByKindAndCategory() {
		Sample sample = new Sample(true);
		// The list is shared by the whole program; add to it only what is not there yet.
		if (Visuals.all().stream().noneMatch(visual -> visual.id().equals("sample"))) {
			Visuals.add(sample);
		}

		assertEquals("sample", Visuals.get(Sample.class).id());
		assertTrue(Visuals.in(Visual.Category.COMBAT).stream().anyMatch(visual -> visual.id().equals("sample")));
		assertTrue(Visuals.in(Visual.Category.HUD).stream().noneMatch(visual -> visual.id().equals("sample")));
		assertThrows(IllegalArgumentException.class, () -> Visuals.add(new Sample(true)));
		assertEquals("combat", Visual.Category.COMBAT.key());
	}

	@Test
	void aTintChangesTheColourButNeverTheBrightness() {
		// A dark night sky, tinted fully towards a bright violet: still as dark as it was.
		float[] night = Ambience.tinted(0.02f, 0.03f, 0.06f, 0xB060FF, 1f);
		assertEquals(brightness(0.02f, 0.03f, 0.06f), brightness(night[0], night[1], night[2]), 1e-4f);
		assertTrue(night[2] > night[1], "violet has more blue than green");

		// Half way is half way.
		float[] day = Ambience.tinted(0.5f, 0.7f, 1f, 0xFF0000, 0.5f);
		assertTrue(day[0] > 0.5f);
		assertTrue(day[2] < 1f);

		// Nothing asked for, nothing changed; and a black tint has no hue to give.
		float[] same = Ambience.tinted(0.5f, 0.7f, 1f, 0xFF0000, 0f);
		assertEquals(0.5f, same[0], 1e-6f);
		assertEquals(1f, same[2], 1e-6f);
		float[] black = Ambience.tinted(0.5f, 0.7f, 1f, 0x000000, 1f);
		assertEquals(0.7f, black[1], 1e-6f);

		// No channel is ever asked for more than it can give.
		float[] bright = Ambience.tinted(1f, 1f, 1f, 0x0000FF, 1f);
		assertTrue(bright[0] <= 1f && bright[1] <= 1f && bright[2] <= 1f);
	}

	private static float brightness(float red, float green, float blue) {
		return 0.299f * red + 0.587f * green + 0.114f * blue;
	}
}
