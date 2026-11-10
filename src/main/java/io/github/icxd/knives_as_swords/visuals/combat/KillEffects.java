package io.github.icxd.knives_as_swords.visuals.combat;

import io.github.icxd.knives_as_swords.fx.FxBatch;
import io.github.icxd.knives_as_swords.fx.FxClock;
import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.fx.Gradient;
import io.github.icxd.knives_as_swords.fx.WorldFrame;
import io.github.icxd.knives_as_swords.fx.WorldPulses;
import io.github.icxd.knives_as_swords.fx.WorldSparks;
import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Something to mark the moment when what the player was fighting dies: a burst of sparks, a
 * pillar of light, bolts from above, or wisps rising.
 *
 * <p>Decoration only: the death is there for everyone to see, and the effect appears where it
 * happens, hidden by whatever stands in front like anything else in the world.
 */
public final class KillEffects extends Visual {
	/** An entity not hit again within this many seconds is no longer the player's kill. */
	private static final float CLAIM_SECONDS = 6f;
	private static final float BOLT_SECONDS = 0.4f;
	/** How high above the fallen the bolts start, in blocks. */
	private static final float BOLT_HEIGHT = 14f;
	private static final int BOLT_POINTS = 14;

	private final Setting.Choice style = choice("style", "burst", "pillar", "bolts", "souls");
	private final Setting.Colors colors = colors("colors", 0xFFFFFF, 0x7CE8FF, 0x3B6BFF);
	private final Setting.Slider cycle = slider("cycle", 0f, 0f, 1f, 0.05f);
	private final Setting.Slider size = slider("size", 1f, 0.5f, 2f, 0.1f);

	/** When each entity was last hit by the player, by entity id. */
	private final Map<Integer, Float> hits = new HashMap<>();
	private final WorldSparks sparks = new WorldSparks(1024);
	private final WorldPulses pulses = new WorldPulses();
	private final List<Strike> strikes = new ArrayList<>();
	private final Random random = new Random();
	private final float[] line = new float[BOLT_POINTS * 3];
	/** How the sparks in the air behave; set by the kind of effect that threw the last ones. */
	private float gravity;
	private float drag = 1f;
	private float swirl = 1f;

	/** Bolts coming down on one spot. */
	private record Strike(double x, double y, double z, float born, int seed, int argb) {
	}

	public KillEffects() {
		super("kill_effects", Category.COMBAT, true);
	}

	@Override
	public void attack(Player player, Entity target, boolean charged) {
		if (target instanceof LivingEntity living && living.isAlive()) {
			hits.put(target.getId(), FxClock.seconds());
		}
	}

	@Override
	public void tick(Minecraft client) {
		if (hits.isEmpty()) {
			return;
		}
		float time = FxClock.seconds();
		for (Iterator<Map.Entry<Integer, Float>> all = hits.entrySet().iterator(); all.hasNext();) {
			Map.Entry<Integer, Float> hit = all.next();
			Entity entity = client.level.getEntity(hit.getKey());
			float since = time - hit.getValue();
			if (entity instanceof LivingEntity living && living.isDeadOrDying()) {
				celebrate(living, time);
				all.remove();
			} else if (entity == null || since > CLAIM_SECONDS || since < 0f) {
				all.remove();
			}
		}
	}

	private void celebrate(LivingEntity fallen, float time) {
		Gradient gradient = colors.get();
		float cycling = cycle.get();
		float scale = size.get();
		double x = fallen.getX();
		double ground = fallen.getY();
		double middle = ground + fallen.getBbHeight() * 0.5;
		double z = fallen.getZ();
		int color = gradient.at(0f, time, cycling);

		switch (style.get()) {
			case "pillar" -> {
				pulses.add(WorldPulses.Shape.BEAM, x, ground, z, time, 0.9f, 1.5f * scale, 7f * scale, color, 0.8f);
				pulses.add(WorldPulses.Shape.GROUND_RING, x, ground + 0.03, z, time, 0.6f, 0.2f * scale, 1.8f * scale,
						color, 0.5f);
				pulses.add(WorldPulses.Shape.FLASH, x, middle, z, time, 0.25f, 1.2f * scale, 2f * scale, color, 0.6f);
				scatter(x, middle, z, 30, 1f, 3.5f, 0.6f, 1.2f, gradient, time, cycling, scale);
				behave(2.5f, 1.5f, 1.5f);
			}
			case "bolts" -> {
				strikes.add(new Strike(x, ground, z, time, random.nextInt(), color));
				pulses.add(WorldPulses.Shape.GROUND_RING, x, ground + 0.03, z, time, 0.5f, 0.2f * scale, 2.2f * scale,
						color, 0.9f);
				pulses.add(WorldPulses.Shape.FLASH, x, middle, z, time, 0.3f, 1.5f * scale, 2.5f * scale, color, 1f);
				scatter(x, ground + 0.2, z, 50, 2f, 7f, 0.3f, 0.7f, gradient, time, cycling, scale);
				behave(-9f, 0.8f, 0.4f);
			}
			case "souls" -> {
				pulses.add(WorldPulses.Shape.FLASH, x, middle, z, time, 0.5f, 0.8f * scale, 1.6f * scale, color, 0.2f);
				for (int i = 0; i < 28; i++) {
					double angle = random.nextDouble() * Math.PI * 2.0;
					double radius = 0.1 + 0.35 * random.nextDouble();
					sparks.emit(x + Math.cos(angle) * radius, ground + random.nextDouble() * fallen.getBbHeight(),
							z + Math.sin(angle) * radius, 0.3f * (float) random.nextGaussian(), 0.4f + 0.6f * random.nextFloat(),
							0.3f * (float) random.nextGaussian(), 1.2f + 1.2f * random.nextFloat(),
							(0.035f + 0.04f * random.nextFloat()) * scale, gradient.at(random.nextFloat(), time, cycling), 0.3f);
				}
				behave(1.4f, 1.6f, 2.8f);
			}
			default -> {
				pulses.add(WorldPulses.Shape.RING, x, middle, z, time, 0.45f, 0.3f * scale, 2.4f * scale, color, 0.7f);
				pulses.add(WorldPulses.Shape.GROUND_RING, x, ground + 0.03, z, time, 0.6f, 0.2f * scale, 2f * scale, color,
						0.5f);
				pulses.add(WorldPulses.Shape.FLASH, x, middle, z, time, 0.22f, 1.4f * scale, 2.4f * scale, color, 0.8f);
				scatter(x, middle, z, 90, 2f, 8f, 0.4f, 1f, gradient, time, cycling, scale);
				behave(-6f, 1f, 0.8f);
			}
		}
	}

	/** Throws sparks in all directions, a little more upwards than not. */
	private void scatter(double x, double y, double z, int count, float slowest, float fastest, float shortest,
			float longest, Gradient gradient, float time, float cycling, float scale) {
		for (int i = 0; i < count; i++) {
			float speed = (slowest + (fastest - slowest) * random.nextFloat() * random.nextFloat()) * scale;
			float vx = (float) random.nextGaussian();
			float vy = (float) random.nextGaussian() + 0.4f;
			float vz = (float) random.nextGaussian();
			float length = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
			if (length < 1e-4f) {
				continue;
			}
			sparks.emit(x, y, z, vx / length * speed, vy / length * speed, vz / length * speed,
					shortest + (longest - shortest) * random.nextFloat(), (0.025f + 0.035f * random.nextFloat()) * scale,
					gradient.at(random.nextFloat(), time, cycling), 0.7f);
		}
	}

	private void behave(float gravity, float drag, float swirl) {
		this.gravity = gravity;
		this.drag = drag;
		this.swirl = swirl;
	}

	@Override
	public void world(WorldFrame frame) {
		sparks.draw(frame, gravity, drag, swirl, 1f);
		pulses.draw(frame);

		FxBatch batch = frame.batch();
		for (int i = strikes.size() - 1; i >= 0; i--) {
			Strike strike = strikes.get(i);
			float age = frame.time() - strike.born();
			if (age < 0f || age >= BOLT_SECONDS) {
				strikes.remove(i);
				continue;
			}
			float t = age / BOLT_SECONDS;
			// Three bolts, each finding a new way down many times a second.
			int flicker = (int) (age * 28f);
			for (int bolt = 0; bolt < 3; bolt++) {
				int path = FxMath.hash(strike.seed(), bolt, flicker);
				float lean = 2.5f * FxMath.signed(FxMath.hash(path, 1));
				float leanZ = 2.5f * FxMath.signed(FxMath.hash(path, 2));
				for (int k = 0; k < BOLT_POINTS; k++) {
					float down = k / (float) (BOLT_POINTS - 1);
					// Jagged all the way, but meeting the ground exactly where it happened.
					float wander = (1f - down) * 0.9f + 0.25f * (float) Math.sin(Math.PI * down);
					line[k * 3] = frame.x(strike.x()) + lean * (1f - down)
							+ wander * FxMath.signed(FxMath.hash(path, k, 3)) * (k == BOLT_POINTS - 1 ? 0f : 1f);
					line[k * 3 + 1] = frame.y(strike.y()) + BOLT_HEIGHT * (1f - down);
					line[k * 3 + 2] = frame.z(strike.z()) + leanZ * (1f - down)
							+ wander * FxMath.signed(FxMath.hash(path, k, 4)) * (k == BOLT_POINTS - 1 ? 0f : 1f);
				}
				float brightness = (1f - t) * (0.6f + 0.4f * FxMath.unit(FxMath.hash(path, 5)));
				batch.polyline(line, BOLT_POINTS, 0.22f * size.get(), FxMath.fade(strike.argb(), brightness), 1f, 0.4f,
						false);
			}
		}
	}
}
