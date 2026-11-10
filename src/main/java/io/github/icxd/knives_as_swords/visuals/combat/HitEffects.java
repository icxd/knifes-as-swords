package io.github.icxd.knives_as_swords.visuals.combat;

import io.github.icxd.knives_as_swords.fx.FxClock;
import io.github.icxd.knives_as_swords.fx.FxStyle;
import io.github.icxd.knives_as_swords.fx.Gradient;
import io.github.icxd.knives_as_swords.fx.WeaponFx;
import io.github.icxd.knives_as_swords.fx.WorldFrame;
import io.github.icxd.knives_as_swords.fx.WorldPulses;
import io.github.icxd.knives_as_swords.fx.WorldSparks;
import io.github.icxd.knives_as_swords.render.KnifeReplacement;
import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import java.util.Random;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Sparks, a flash and a ring where a hit of the player's lands, whatever they hit with.
 *
 * <p>Decoration only: it shows where the player's own attack went, which the game shows anyway.
 */
public final class HitEffects extends Visual {
	private final Setting.Colors colors = colors("colors", 0xFFFFFF, 0xFFD24A, 0xFF5A1F);
	private final Setting.Slider cycle = slider("cycle", 0f, 0f, 1f, 0.05f);
	/** With a knife in hand, use the colours of that knife's effects instead. */
	private final Setting.Toggle matchKnife = toggle("match_knife", true);
	private final Setting.Slider sparks = slider("sparks", 1f, 0f, 2f, 0.1f);
	private final Setting.Toggle ring = toggle("ring", true);
	private final Setting.Toggle flash = toggle("flash", true);
	private final Setting.Slider size = slider("size", 1f, 0.5f, 2f, 0.1f);
	/** What sparks do once in the air: -1 fall like drops, 1 rise like embers. */
	private final Setting.Slider drift = slider("drift", -0.6f, -1f, 1f, 0.1f);

	private final WorldSparks worldSparks = new WorldSparks(768);
	private final WorldPulses pulses = new WorldPulses();
	private final Random random = new Random();

	public HitEffects() {
		super("hit_effects", Category.COMBAT, true);
	}

	/** Where the player's line of sight meets what they hit, or failing that its middle. */
	public static Vec3 point(Player player, Entity target) {
		Vec3 eye = player.getEyePosition();
		Vec3 far = eye.add(player.getViewVector(1f).scale(8.0));
		return target.getBoundingBox().clip(eye, far).orElseGet(() -> target.getBoundingBox().getCenter());
	}

	@Override
	public void attack(Player player, Entity target, boolean charged) {
		Vec3 at = point(player, target);
		float time = FxClock.seconds();
		Gradient gradient = colors.get();
		float cycling = cycle.get();
		float heat = 0.7f;
		if (matchKnife.on()) {
			ItemStack held = player.getMainHandItem();
			FxStyle style = KnifeReplacement.visualFor(held) != null ? WeaponFx.styleFor(held.getItem()) : null;
			if (style != null) {
				gradient = style.colors();
				cycling = style.cycle();
				heat = style.heat();
			}
		}

		float scale = size.get();
		int count = Math.round(sparks.get() * (charged ? 60f : 30f));
		for (int i = 0; i < count; i++) {
			float speed = ((charged ? 2.5f : 1.8f) + (charged ? 6f : 4.5f) * random.nextFloat() * random.nextFloat()) * scale;
			float x = (float) random.nextGaussian();
			float y = (float) random.nextGaussian() + 0.5f;
			float z = (float) random.nextGaussian();
			float length = (float) Math.sqrt(x * x + y * y + z * z);
			if (length < 1e-4f) {
				continue;
			}
			worldSparks.emit(at.x, at.y, at.z, x / length * speed, y / length * speed, z / length * speed,
					0.3f + 0.55f * random.nextFloat(), (0.02f + 0.03f * random.nextFloat()) * scale,
					gradient.at(random.nextFloat(), time, cycling), heat);
		}
		int color = gradient.at(0f, time, cycling);
		float reach = (charged ? 1.3f : 0.9f) * scale;
		if (flash.on()) {
			pulses.add(WorldPulses.Shape.FLASH, at.x, at.y, at.z, time, 0.16f, reach * 0.5f, reach * 0.9f, color, 0.6f);
		}
		if (ring.on()) {
			pulses.add(WorldPulses.Shape.RING, at.x, at.y, at.z, time, 0.3f, reach * 0.15f, reach * 0.85f, color, 0.6f);
		}
	}

	@Override
	public void world(WorldFrame frame) {
		float falling = drift.get();
		float gravity = (falling >= 0f ? falling * 3f : falling * 7f) * 1.6f;
		worldSparks.draw(frame, gravity, 1f + Math.max(falling, -0.5f), 0.4f + 2f * (1f - Math.abs(falling)), 1f);
		pulses.draw(frame);
	}
}
