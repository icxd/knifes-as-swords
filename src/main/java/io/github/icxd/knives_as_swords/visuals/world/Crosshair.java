package io.github.icxd.knives_as_swords.visuals.world;

import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;

/**
 * A crosshair of the player's own making, in place of the game's: four arms with a gap between
 * them, in any colour, as shooters have it.
 *
 * <p>It marks the middle of the screen and nothing else. The short flash when a hit goes out is
 * the player's own click made visible; whether the hit landed, the game shows anyway.
 */
public final class Crosshair extends Visual {
	private static final long HIT_MARK_NANOS = 220_000_000L;
	private static final int OUTLINE = 0xFF000000;

	private final Setting.Choice style = choice("style", "cross", "tee", "dot");
	private final Setting.Color color = color("color", 0x5CFF7A);
	private final Setting.Slider length = slider("length", 5f, 1f, 16f, 1f);
	private final Setting.Slider gap = slider("gap", 3f, 0f, 12f, 1f);
	private final Setting.Slider thickness = slider("thickness", 1f, 1f, 5f, 1f);
	private final Setting.Toggle dot = toggle("dot", false);
	private final Setting.Toggle outline = toggle("outline", true);
	private final Setting.Toggle hitMark = toggle("hit_mark", true);

	private long hitNanos;

	public Crosshair() {
		super("crosshair", Category.WORLD, false);
	}

	@Override
	public void attack(Player player, Entity target, boolean charged) {
		hitNanos = System.nanoTime();
	}

	/**
	 * Whether this crosshair should be drawn right now instead of the game's. The game's own has
	 * rules about when there is a crosshair at all; where those are not simply "yes", it is left to
	 * draw itself.
	 */
	public boolean replaces(Minecraft client) {
		return on() && client.player != null && client.gameMode != null
				&& client.options.getCameraType().isFirstPerson()
				&& client.gameMode.getPlayerMode() != GameType.SPECTATOR;
	}

	public void draw(GuiGraphicsExtractor graphics, Minecraft client) {
		graphics.nextStratum();
		int thick = Math.round(thickness.get());
		int arm = Math.round(length.get());
		int space = Math.round(gap.get());
		// The middle of the screen, as the top left of a block as thick as the lines.
		int x = graphics.guiWidth() / 2 - thick / 2;
		int y = graphics.guiHeight() / 2 - thick / 2;
		int paint = color.argb(1f);
		boolean arms = !style.is("dot");

		for (int pass = outline.on() ? 0 : 1; pass < 2; pass++) {
			// First everything a pixel bigger in black, then the crosshair itself on top.
			int grow = pass == 0 ? 1 : 0;
			int ink = pass == 0 ? OUTLINE : paint;
			if (arms) {
				rect(graphics, x - space - arm, y, arm, thick, grow, ink);
				rect(graphics, x + thick + space, y, arm, thick, grow, ink);
				rect(graphics, x, y + thick + space, thick, arm, grow, ink);
				if (!style.is("tee")) {
					rect(graphics, x, y - space - arm, thick, arm, grow, ink);
				}
			}
			if (dot.on() || !arms) {
				int size = arms ? thick : Math.max(thick, 2);
				rect(graphics, graphics.guiWidth() / 2 - size / 2, graphics.guiHeight() / 2 - size / 2, size, size, grow,
						ink);
			}
		}

		long sinceHit = System.nanoTime() - hitNanos;
		if (hitMark.on() && sinceHit >= 0 && sinceHit < HIT_MARK_NANOS) {
			// Four short strokes on the diagonals, flying outwards a little as they fade.
			float t = sinceHit / (float) HIT_MARK_NANOS;
			int ink = FxMath.fade(0xFFFFFFFF, 1f - t * t);
			int from = space + arm / 2 + 2 + Math.round(t * 3f);
			int centerX = graphics.guiWidth() / 2;
			int centerY = graphics.guiHeight() / 2;
			for (int step = 0; step < 4; step++) {
				int away = from + step;
				graphics.fill(centerX + away, centerY + away, centerX + away + 1, centerY + away + 1, ink);
				graphics.fill(centerX - away - 1, centerY + away, centerX - away, centerY + away + 1, ink);
				graphics.fill(centerX + away, centerY - away - 1, centerX + away + 1, centerY - away, ink);
				graphics.fill(centerX - away - 1, centerY - away - 1, centerX - away, centerY - away, ink);
			}
		}

		// The game shows how far a swing has wound up again, if asked to; keep saying that.
		if (client.options.attackIndicator().get() == AttackIndicatorStatus.CROSSHAIR) {
			float wound = client.player.getAttackStrengthScale(0f);
			if (wound < 1f) {
				int barLeft = graphics.guiWidth() / 2 - 8;
				int barTop = graphics.guiHeight() / 2 + space + arm + 5;
				graphics.fill(barLeft - 1, barTop - 1, barLeft + 17, barTop + 3, 0x90000000);
				graphics.fill(barLeft, barTop, barLeft + Math.round(16f * wound), barTop + 2, paint);
			}
		}
	}

	private static void rect(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int grow, int ink) {
		graphics.fill(x - grow, y - grow, x + width + grow, y + height + grow, ink);
	}
}
