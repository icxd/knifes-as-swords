package io.github.icxd.knives_as_swords.fx;

import java.util.ArrayList;
import java.util.List;

/**
 * Short-lived lights at places in the world: the flash of a hit, a ring running outwards over the
 * ground, a beam standing where something died. Each one grows and fades by itself once started.
 */
public final class WorldPulses {
	/** The shapes a pulse can have. */
	public enum Shape {
		/** A soft round light facing the viewer. */
		FLASH,
		/** A circle of light facing the viewer. */
		RING,
		/** A circle of light lying on the ground. */
		GROUND_RING,
		/** A line of light standing upright; its size is its height. */
		BEAM
	}

	private static final int MAX = 64;

	private static final class Pulse {
		Shape shape;
		double x;
		double y;
		double z;
		float born;
		float life;
		float from;
		float to;
		int argb;
		float heat;
	}

	private final List<Pulse> pulses = new ArrayList<>();
	private final float[] line = new float[6];

	public int count() {
		return pulses.size();
	}

	public void clear() {
		pulses.clear();
	}

	/**
	 * Starts a pulse.
	 *
	 * @param time now, on the clock the frames are drawn by
	 * @param life seconds until it is gone
	 * @param from its size when it starts, in blocks (a radius; for a beam its height); {@code to}
	 *     when it ends
	 */
	public void add(Shape shape, double x, double y, double z, float time, float life, float from, float to, int argb,
			float heat) {
		if (life <= 0f) {
			return;
		}
		Pulse pulse = pulses.size() < MAX ? new Pulse() : pulses.remove(0);
		pulse.shape = shape;
		pulse.x = x;
		pulse.y = y;
		pulse.z = z;
		pulse.born = time;
		pulse.life = life;
		pulse.from = from;
		pulse.to = to;
		pulse.argb = argb;
		pulse.heat = heat;
		pulses.add(pulse);
	}

	/** Adds the pulses of this moment to the frame, and forgets those that are over. */
	public void draw(WorldFrame frame) {
		FxBatch batch = frame.batch();
		for (int i = pulses.size() - 1; i >= 0; i--) {
			Pulse pulse = pulses.get(i);
			float age = frame.time() - pulse.born;
			if (age < 0f || age >= pulse.life) {
				// Over, or started on a clock that has since begun anew.
				pulses.remove(i);
				continue;
			}
			float t = age / pulse.life;
			// Fast at first, slowing as it spreads; fading all the while.
			float size = pulse.from + (pulse.to - pulse.from) * (float) Math.sqrt(t);
			int color = FxMath.fade(pulse.argb, (1f - t) * (1f - t));
			float x = frame.x(pulse.x), y = frame.y(pulse.y), z = frame.z(pulse.z);
			switch (pulse.shape) {
				case FLASH -> batch.dot(x, y, z, size, color, pulse.heat);
				case RING -> batch.ring(x, y, z, size, color, pulse.heat);
				case GROUND_RING -> batch.flat(x, y, z, size, 0f, 0f, 0f, 0f, size, color, pulse.heat, FxBatch.RING);
				case BEAM -> {
					line[0] = x;
					line[1] = y;
					line[2] = z;
					line[3] = x;
					line[4] = y + size;
					line[5] = z;
					batch.polyline(line, 2, 0.12f + 0.25f * (1f - t), color, pulse.heat, 0.3f, false);
				}
			}
		}
	}
}
