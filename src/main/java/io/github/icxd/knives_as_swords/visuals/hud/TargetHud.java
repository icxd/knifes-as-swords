package io.github.icxd.knives_as_swords.visuals.hud;

import io.github.icxd.knives_as_swords.fx.FxClock;
import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.fx.Gradient;
import io.github.icxd.knives_as_swords.ui.Glow;
import io.github.icxd.knives_as_swords.visuals.Setting;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * A small panel naming what the player is fighting and showing how much health it has left.
 *
 * <p><b>This shows information the game does not show by itself</b>, so it is off unless the
 * player switches it on, and some servers do not allow it. It only ever concerns something in
 * plain sight: what the crosshair is on, or what the player hit a moment ago, as long as there is
 * a clear line of sight to it and it is not invisible.
 */
public final class TargetHud extends HudWidget {
	private static final int WIDTH = 118;
	private static final int HEIGHT = 26;
	/** How long after a hit the panel stays on what was hit, without the crosshair on it. */
	private static final float KEEP_SECONDS = 4f;
	/** Further away than this (in blocks) nothing is shown. */
	private static final double RANGE = 24.0;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xFFA8A8B4;

	private final Setting.Colors colors = colors("colors", 0xFF3B3B, 0xFFD23B, 0x4DFF6A);

	private @Nullable LivingEntity target;
	private float hitTime = Float.NEGATIVE_INFINITY;
	/** Where the bar stood before the last blow, catching up with the real one: what was just lost shows for a moment. */
	private float shownShare = 1f;
	private long lastNanos;

	public TargetHud() {
		super("target_hud", false, 0.5f, 0.62f);
	}

	@Override
	public boolean showsInformation() {
		return true;
	}

	@Override
	public void attack(Player player, Entity hit, boolean charged) {
		if (hit instanceof LivingEntity living) {
			target = living;
			hitTime = FxClock.seconds();
		}
	}

	@Override
	public void tick(Minecraft client) {
		// What the crosshair is on comes first; failing that, what was hit a moment ago.
		if (client.crosshairPickEntity instanceof LivingEntity looked) {
			if (looked != target) {
				shownShare = share(looked);
			}
			target = looked;
			hitTime = FxClock.seconds();
		}
		float since = FxClock.seconds() - hitTime;
		if (target != null && (since > KEEP_SECONDS || since < 0f || !inPlainSight(client, target))) {
			target = null;
		}
	}

	/** The rule this visual lives by: nothing about anything the player could not see anyway. */
	private static boolean inPlainSight(Minecraft client, LivingEntity entity) {
		return entity.isAlive() && !entity.isRemoved() && !entity.isInvisible() && entity.level() == client.level
				&& entity.distanceToSqr(client.player) <= RANGE * RANGE && client.player.hasLineOfSight(entity);
	}

	private static float share(LivingEntity entity) {
		float most = entity.getMaxHealth();
		return most > 0f ? Math.clamp(entity.getHealth() / most, 0f, 1f) : 0f;
	}

	@Override
	protected int width(Minecraft client, boolean editing) {
		return target != null || editing ? WIDTH : 0;
	}

	@Override
	protected int height(Minecraft client, boolean editing) {
		return HEIGHT;
	}

	@Override
	protected void draw(GuiGraphicsExtractor graphics, Minecraft client, int width, int height, boolean editing) {
		long now = System.nanoTime();
		float seconds = Math.clamp((now - lastNanos) / 1e9f, 0f, 0.1f);
		lastNanos = now;

		LivingEntity entity = target;
		String name = entity != null ? entity.getDisplayName().getString() : "Target";
		float health = entity != null ? entity.getHealth() : 14f;
		float most = entity != null ? entity.getMaxHealth() : 20f;
		float share = entity != null ? share(entity) : 0.7f;
		shownShare = share >= shownShare ? share : shownShare + (share - shownShare) * Math.min(seconds * 5f, 1f);

		String fitted = client.font.width(name) <= width ? name : client.font.plainSubstrByWidth(name, width - 6) + "..";
		graphics.text(client.font, fitted, 0, 0, TEXT);
		String numbers = String.format(Locale.ROOT, "%.1f / %.0f", health, most);
		graphics.text(client.font, numbers, width - client.font.width(numbers), height - client.font.lineHeight + 1, DIM);

		float barTop = client.font.lineHeight + 2f;
		float barBottom = barTop + 5f;
		Gradient gradient = colors.get();
		int color = gradient.at(share);
		Glow.bar(graphics, 0, barTop, width, barBottom, share, color, FxMath.mix(color, 0xFFFFFFFF, 0.25f));
		if (shownShare - share > 0.004f) {
			// What the last blow took, still glowing white as it goes.
			Glow.light(graphics, width * share, barTop + 1f, width * shownShare, barBottom - 1f,
					FxMath.fade(0xFFFFFFFF, Math.min((shownShare - share) * 6f, 0.7f)));
		}
	}
}
