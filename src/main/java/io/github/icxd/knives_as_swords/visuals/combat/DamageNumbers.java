package io.github.icxd.knives_as_swords.visuals.combat;

import io.github.icxd.knives_as_swords.fx.FxClock;
import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.fx.WorldFrame;
import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Numbers that float up from what the player is fighting, saying how much health each hit took.
 *
 * <p><b>This shows information the game does not show by itself</b>, so it is off unless the
 * player switches it on, and some servers do not allow it. It only concerns what the player hit
 * themselves a moment ago, and only while there is a clear line of sight to it and it is not
 * invisible. The numbers are in the world, hidden by whatever stands in front of them.
 */
public final class DamageNumbers extends Visual {
	/** How long after the player's own hit an entity's losses are still shown, in seconds. */
	private static final float WATCH_SECONDS = 5f;
	private static final float RISE_SECONDS = 1.1f;
	private static final int MAX_NUMBERS = 48;

	private final Setting.Colors colors = colors("colors", 0xFFFFFF, 0xFFD23B, 0xFF4D4D);
	private final Setting.Slider size = slider("size", 1f, 0.5f, 2.5f, 0.1f);
	/** The loss at which a number is as red as it gets, in health points. */
	private final Setting.Slider heavy = slider("heavy", 10f, 2f, 40f, 1f);

	/** What is being watched, by entity id: the health it had, and when the player last hit it. */
	private final Map<Integer, float[]> watched = new HashMap<>();
	private final List<Number> numbers = new ArrayList<>();
	private final Random random = new Random();

	private record Number(double x, double y, double z, float born, String text, int argb, float drift) {
	}

	public DamageNumbers() {
		super("damage_numbers", Category.COMBAT, false);
	}

	@Override
	public boolean showsInformation() {
		return true;
	}

	@Override
	public void attack(Player player, Entity target, boolean charged) {
		if (target instanceof LivingEntity living && living.isAlive()) {
			float[] known = watched.get(target.getId());
			float health = known != null ? known[0] : living.getHealth();
			watched.put(target.getId(), new float[] {health, FxClock.seconds()});
		}
	}

	@Override
	public void tick(Minecraft client) {
		float time = FxClock.seconds();
		for (Iterator<Map.Entry<Integer, float[]>> all = watched.entrySet().iterator(); all.hasNext();) {
			Map.Entry<Integer, float[]> entry = all.next();
			float since = time - entry.getValue()[1];
			if (!(client.level.getEntity(entry.getKey()) instanceof LivingEntity living) || since > WATCH_SECONDS
					|| since < 0f) {
				all.remove();
				continue;
			}
			float health = Math.max(living.getHealth(), 0f);
			float lost = entry.getValue()[0] - health;
			entry.getValue()[0] = health;
			// Only what could be seen happening: in line of sight, and not invisible.
			if (lost >= 0.05f && !living.isInvisible() && client.player.hasLineOfSight(living)) {
				float weight = Math.clamp(lost / heavy.get(), 0f, 1f);
				if (numbers.size() >= MAX_NUMBERS) {
					numbers.remove(0);
				}
				numbers.add(new Number(living.getX() + 0.5 * (random.nextDouble() - 0.5),
						living.getY() + living.getBbHeight() * 0.85, living.getZ() + 0.5 * (random.nextDouble() - 0.5), time,
						lost >= 10f ? String.format(Locale.ROOT, "%.0f", lost) : String.format(Locale.ROOT, "%.1f", lost),
						colors.get().at(weight), 0.5f * (random.nextFloat() - 0.5f)));
			}
			if (living.isDeadOrDying()) {
				all.remove();
			}
		}
	}

	@Override
	public void world(WorldFrame frame) {
		for (int i = numbers.size() - 1; i >= 0; i--) {
			Number number = numbers.get(i);
			float age = frame.time() - number.born();
			if (age < 0f || age >= RISE_SECONDS) {
				numbers.remove(i);
				continue;
			}
			float t = age / RISE_SECONDS;
			// Pops up, rises while slowing, and fades at the end.
			float pop = t < 0.12f ? 0.6f + 0.4f * (t / 0.12f) * 1.25f : 1f;
			float rise = 0.9f * (1f - (1f - t) * (1f - t));
			float fade = t < 0.6f ? 1f : 1f - (t - 0.6f) / 0.4f;
			frame.text(number.x() + number.drift() * t, number.y() + rise, number.z(), number.text(),
					size.get() * Math.min(pop, 1.15f), FxMath.fade(number.argb(), fade));
		}
	}
}
