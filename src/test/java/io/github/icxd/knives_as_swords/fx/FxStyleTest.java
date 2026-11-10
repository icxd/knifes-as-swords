package io.github.icxd.knives_as_swords.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The looks on offer, the helpers they are made with, and which sword gets which. */
class FxStyleTest {
	@Test
	void everyLookHasANameOfItsOwnAndSaneAmounts() {
		Set<String> names = new HashSet<>();
		for (FxStyle style : FxStyle.all()) {
			assertTrue(names.add(style.id()), "twice: " + style.id());
			assertTrue(style.id().matches("[a-z]+"), style.id());
			assertNotEquals(FxStyle.NONE, style.id());
			for (float amount : new float[] {style.trail(), style.echo(), style.arcs(), style.sparks(), style.glow(),
					style.heat(), style.roughness()}) {
				assertTrue(amount >= 0f && amount <= 1f, style.id() + ": " + amount);
			}
			assertTrue(style.colors().size() >= 2, style.id());
			assertTrue(style.cycle() >= 0f, style.id());
			assertEquals(style, FxStyle.byId(style.id()).orElseThrow());
		}
		assertTrue(names.size() >= 5);
		assertTrue(FxStyle.byId(FxStyle.NONE).isEmpty());
		assertTrue(FxStyle.byId(FxStyle.CUSTOM).isEmpty());
		assertTrue(FxStyle.byId("no such look").isEmpty());
	}

	@Test
	void aLooksColoursStayPutUnlessItIsARainbow() {
		FxStyle steady = FxStyle.byId("storm").orElseThrow();
		assertEquals(steady.color(0f, 0f), steady.color(12.3f, 0f));
		assertEquals(0xFF, steady.color(0f, 0f) >>> 24);
		// From the first of its colours to the last.
		assertEquals(steady.colors().at(0f), steady.color(0f, 0f));
		assertEquals(steady.colors().at(1f), steady.color(0f, 1f));
		assertEquals(steady.colors().at(0.4f), steady.color(99f, 0.4f));

		FxStyle rainbow = FxStyle.byId("prism").orElseThrow();
		assertNotEquals(rainbow.color(0f, 0f), rainbow.color(1f, 0f));
		assertNotEquals(rainbow.color(0f, 0f), rainbow.color(0f, 0.5f));
		// The same moment gives the same colour, wherever it is asked for.
		assertEquals(rainbow.color(4.5f, 0.2f), rainbow.color(4.5f, 0.2f));
	}

	@Test
	void swordsGetALookThatSuitsThemUnlessToldOtherwise() {
		assertEquals(FxStyle.NONE, WeaponFx.effectId("minecraft:wooden_sword", ""));
		assertEquals(FxStyle.NONE, WeaponFx.effectId("some_mod:odd_sword", ""));
		String precious = WeaponFx.effectId("minecraft:netherite_sword", "");
		assertTrue(FxStyle.byId(precious).isPresent(), precious);

		assertEquals("storm", WeaponFx.effectId("minecraft:wooden_sword", "storm"));
		assertEquals(FxStyle.NONE, WeaponFx.effectId("minecraft:netherite_sword", FxStyle.NONE));
	}

	@Test
	void theDemonstrationSwingIsOneSmoothRound() {
		float step = 0.001f;
		float previous = PreviewFx.angle(0f);
		float fastest = 0f;
		for (float moment = step; moment < PreviewFx.period(); moment += step) {
			float angle = PreviewFx.angle(moment);
			// No jumps anywhere, not even where one part of the swing hands over to the next.
			assertTrue(Math.abs(angle - previous) < 0.03f, "jump at " + moment);
			fastest = Math.max(fastest, Math.abs(angle - previous) / step);
			previous = angle;
		}
		// It ends where it begins, and in between there is a real slash: several turns per second at its fastest.
		assertEquals(PreviewFx.angle(0f), previous, 0.01f);
		assertTrue(fastest > 10f, "fastest " + fastest + " radians per second");
	}

	@Test
	void hashesAreRepeatableAndSpreadOut() {
		assertEquals(FxMath.hash(1234, 5), FxMath.hash(1234, 5));
		assertNotEquals(FxMath.hash(1234, 5), FxMath.hash(1234, 6));
		assertNotEquals(FxMath.hash(1, 2), FxMath.hash(2, 1));

		float sum = 0f;
		for (int i = 0; i < 10_000; i++) {
			float value = FxMath.unit(FxMath.hash(i));
			assertTrue(value >= 0f && value < 1f);
			sum += value;
		}
		assertEquals(0.5f, sum / 10_000, 0.02f);
	}

	@Test
	void noiseWandersWithoutJumps() {
		float previous = FxMath.noise(0f, 3);
		for (int i = 1; i <= 2000; i++) {
			float value = FxMath.noise(i * 0.01f, 3);
			assertTrue(value >= -1f && value <= 1f);
			assertTrue(Math.abs(value - previous) < 0.05f, "jump at " + i);
			previous = value;
		}
		assertNotEquals(FxMath.noise(0.5f, 3), FxMath.noise(0.5f, 4));
	}

	@Test
	void coloursArePackedMixedAndFaded() {
		assertEquals(0xFFFF8000, FxMath.argb(1f, 0.5f, 0f, 1f));
		// Out of range is cut off, not wrapped round.
		assertEquals(0xFFFF0000, FxMath.argb(7f, -1f, 0f, 3f));

		assertEquals(0x80FF8000, FxMath.fade(0xFFFF8000, 0.5f));
		assertEquals(0x00FF8000, FxMath.fade(0xFFFF8000, 0f));

		assertEquals(0xFF000000, FxMath.mix(0xFF000000, 0xFFFFFFFF, 0f));
		assertEquals(0xFFFFFFFF, FxMath.mix(0xFF000000, 0xFFFFFFFF, 1f));
		assertEquals(0xFF808080, FxMath.mix(0xFF000000, 0xFFFFFFFF, 0.5f));

		assertEquals(0xFFFF0000, FxMath.hue(0f));
		assertEquals(0xFF00FF00, FxMath.hue(1f / 3f));
		assertEquals(0xFF0000FF, FxMath.hue(2f / 3f));
		assertEquals(FxMath.hue(0.25f), FxMath.hue(1.25f));
	}

	@Test
	void smoothstepEasesFromNoughtToOne() {
		assertEquals(0f, FxMath.smoothstep(1f, 2f, 0.5f));
		assertEquals(1f, FxMath.smoothstep(1f, 2f, 2.5f));
		assertEquals(0.5f, FxMath.smoothstep(1f, 2f, 1.5f), 1e-6f);
		assertTrue(FxMath.smoothstep(1f, 2f, 1.1f) < 0.1f);
	}
}
