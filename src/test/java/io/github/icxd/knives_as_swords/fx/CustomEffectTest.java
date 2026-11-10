package io.github.icxd.knives_as_swords.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomEffectTest {
	@Test
	void becomesALookWithItsAmountsAndColours() {
		CustomEffect effect = new CustomEffect();
		effect.trail = 0.1f;
		effect.afterimages = 0.2f;
		effect.bolts = 0.3f;
		effect.sparks = 0.4f;
		effect.glow = 0.5f;
		effect.heat = 0.6f;
		effect.raggedness = 0.7f;
		effect.colors = new ArrayList<>(List.of(new CustomEffect.ColorStop(0f, "#FF0000"),
				new CustomEffect.ColorStop(1f, "#0000FF")));

		FxStyle style = effect.toStyle();

		assertEquals(FxStyle.CUSTOM, style.id());
		assertEquals(0.1f, style.trail());
		assertEquals(0.2f, style.echo());
		assertEquals(0.3f, style.arcs());
		assertEquals(0.4f, style.sparks());
		assertEquals(0.5f, style.glow());
		assertEquals(0.6f, style.heat());
		assertEquals(0.7f, style.roughness());
		assertEquals(0xFFFF0000, style.color(5f, 0f));
		assertEquals(0xFF0000FF, style.color(5f, 1f));
		assertEquals(0xFF800080, style.color(5f, 0.5f));
	}

	@Test
	void sparksFallHangOrRise() {
		CustomEffect effect = new CustomEffect();
		effect.sparkDrift = -1f;
		assertTrue(effect.toStyle().sparkGravity() < -3f);
		effect.sparkDrift = 0f;
		assertEquals(0f, effect.toStyle().sparkGravity());
		effect.sparkDrift = 1f;
		assertTrue(effect.toStyle().sparkGravity() > 1f);
		// Whatever they do, the air never speeds them up.
		for (float drift = -1f; drift <= 1f; drift += 0.1f) {
			effect.sparkDrift = drift;
			assertTrue(effect.toStyle().sparkDrag() > 0f);
			assertTrue(effect.toStyle().sparkSwirl() >= 0f);
		}
	}

	@Test
	void coloursCycleOnlyWhenAskedTo() {
		CustomEffect effect = new CustomEffect();
		effect.colors = new ArrayList<>(List.of(new CustomEffect.ColorStop(0f, "#FF0000"),
				new CustomEffect.ColorStop(1f, "#0000FF")));

		assertEquals(effect.toStyle().color(0f, 0.2f), effect.toStyle().color(3.3f, 0.2f));

		effect.colorCycle = 0.5f;
		FxStyle cycling = effect.toStyle();
		boolean moved = false;
		for (float time = 0f; time < 4f; time += 0.1f) {
			moved |= cycling.color(time, 0.2f) != cycling.color(0f, 0.2f);
		}
		assertTrue(moved);
	}

	@Test
	void startsOutAsACopyOfAReadyMadeLook() {
		for (FxStyle look : FxStyle.all()) {
			CustomEffect effect = CustomEffect.from(look);
			FxStyle copy = effect.toStyle();

			assertEquals(look.trail(), copy.trail(), look.id());
			assertEquals(look.echo(), copy.echo(), look.id());
			assertEquals(look.arcs(), copy.arcs(), look.id());
			assertEquals(look.sparks(), copy.sparks(), look.id());
			assertEquals(look.glow(), copy.glow(), look.id());
			assertEquals(look.heat(), copy.heat(), look.id());
			assertEquals(look.roughness(), copy.roughness(), look.id());
			assertEquals(look.cycle(), copy.cycle(), look.id());
			assertEquals(look.colors(), copy.colors(), look.id());
			// Sparks go the same way, if not at exactly the same pace.
			assertEquals(Math.signum(look.sparkGravity()), Math.signum(copy.sparkGravity()), look.id());
		}
	}

	@Test
	void aHandEditedFileIsTidiedUp() {
		CustomEffect effect = new CustomEffect();
		effect.trail = 7f;
		effect.glow = -1f;
		effect.heat = Float.NaN;
		effect.sparkDrift = -9f;
		effect.colorCycle = 50f;
		effect.colors = new ArrayList<>();
		effect.colors.add(new CustomEffect.ColorStop(0.9f, "#00ff00"));
		effect.colors.add(new CustomEffect.ColorStop(0.1f, "not a colour"));
		effect.colors.add(null);
		effect.colors.add(new CustomEffect.ColorStop(-4f, "FF0000"));

		effect.sanitize();

		assertEquals(1f, effect.trail);
		assertEquals(0f, effect.glow);
		assertEquals(0f, effect.heat);
		assertEquals(-1f, effect.sparkDrift);
		assertEquals(CustomEffect.MAX_COLOR_CYCLE, effect.colorCycle);
		// What is not a colour is gone; the rest is in order and spelled one way.
		assertEquals(2, effect.colors.size());
		assertEquals(0f, effect.colors.get(0).at);
		assertEquals("#FF0000", effect.colors.get(0).color);
		assertEquals(0.9f, effect.colors.get(1).at);
		assertEquals("#00FF00", effect.colors.get(1).color);
	}

	@Test
	void withoutAnyColoursItIsWhiteRatherThanBroken() {
		CustomEffect effect = new CustomEffect();
		effect.colors = null;

		assertEquals(0xFFFFFFFF, effect.toStyle().color(0f, 0.5f));
		effect.sanitize();
		assertEquals(1, effect.colors.size());
	}

	@Test
	void aCopySharesNothingWithTheOriginal() {
		CustomEffect effect = CustomEffect.from(FxStyle.byId("void").orElseThrow());
		CustomEffect copy = effect.copy();

		assertNotSame(effect.colors, copy.colors);
		assertNotSame(effect.colors.get(0), copy.colors.get(0));
		assertEquals(effect.toStyle(), copy.toStyle());

		copy.colors.get(0).color = "#000000";
		copy.trail = 0f;
		assertEquals("#B838FF", effect.colors.get(0).color);
		assertEquals(1f, effect.trail);
	}
}
