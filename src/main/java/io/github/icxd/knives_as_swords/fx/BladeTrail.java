package io.github.icxd.knives_as_swords.fx;

import org.joml.Vector3fc;

/**
 * Remembers where a blade has just been, and turns that into the band of light it leaves behind.
 *
 * <p>Fed with the blade's base and tip several times per frame; old moments drop out by
 * themselves. A blade that is held still leaves nothing: how visible a stretch of the band is
 * depends on how fast the tip moved there.
 */
public final class BladeTrail {
	/** Floats per remembered moment: time, base, tip, distance the tip has travelled. */
	private static final int STRIDE = 8;
	private static final int CAPACITY = 384;
	/** Moments further apart than this (seconds) are not joined: the knife was away in between. */
	private static final float MAX_GAP = 0.12f;
	/** The tip jumping this many blade lengths between two moments is a cut, not a movement. */
	private static final float MAX_JUMP = 4f;

	/** The band's colour from where it leaves the blade (0) to where it has faded away (1). */
	@FunctionalInterface
	public interface Colors {
		int at(float life);
	}

	private final float[] data = new float[CAPACITY * STRIDE];
	private int newest = -1;
	private int size;

	private final float[] base0 = new float[3];
	private final float[] tip0 = new float[3];
	private final float[] base1 = new float[3];
	private final float[] tip1 = new float[3];

	public void clear() {
		newest = -1;
		size = 0;
	}

	public int size() {
		return size;
	}

	/** Remembers where the blade is at {@code time} (seconds, ever increasing). */
	public void add(float time, Vector3fc base, Vector3fc tip) {
		float travelled = 0f;
		if (size > 0) {
			int last = newest * STRIDE;
			if (time <= data[last]) {
				return;
			}
			float dx = tip.x() - data[last + 4], dy = tip.y() - data[last + 5], dz = tip.z() - data[last + 6];
			travelled = data[last + 7] + (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
		newest = (newest + 1) % CAPACITY;
		size = Math.min(size + 1, CAPACITY);
		int at = newest * STRIDE;
		data[at] = time;
		data[at + 1] = base.x();
		data[at + 2] = base.y();
		data[at + 3] = base.z();
		data[at + 4] = tip.x();
		data[at + 5] = tip.y();
		data[at + 6] = tip.z();
		data[at + 7] = travelled;
	}

	/** How fast the tip is moving right now, in metres per second; averaged over the last few moments. */
	public float tipSpeed() {
		return speedAt(0, 4);
	}

	/** Which way and how fast the tip is moving right now (metres per second), written to {@code out}. */
	public void tipVelocity(float[] out) {
		out[0] = out[1] = out[2] = 0f;
		if (size < 2) {
			return;
		}
		int a = index(Math.min(3, size - 1)) * STRIDE;
		int b = index(0) * STRIDE;
		float span = data[b] - data[a];
		if (span <= 1e-6f || span > MAX_GAP) {
			return;
		}
		out[0] = (data[b + 4] - data[a + 4]) / span;
		out[1] = (data[b + 5] - data[a + 5]) / span;
		out[2] = (data[b + 6] - data[a + 6]) / span;
	}

	/**
	 * Adds the band of light to {@code out}.
	 *
	 * @param now the current time, on the clock {@link #add} is fed with
	 * @param lifetime seconds until a stretch of the band has faded
	 * @param fullSpeed tip speed (metres per second) from which the band shows in full; below a
	 *     third of it nothing shows
	 * @param bladeLength for scale: the band's texture repeats per blade length travelled
	 * @param strength overall visibility, 0 to 1
	 * @param offsetX added to every position, for an owner whose space is not the one drawn in;
	 *     likewise {@code offsetY} and {@code offsetZ}
	 */
	public void build(FxBatch out, float now, float lifetime, float fullSpeed, float bladeLength, Colors colors,
			float strength, float heat, float roughness, float offsetX, float offsetY, float offsetZ) {
		if (size < 2 || strength <= 0f || lifetime <= 0f) {
			return;
		}
		float previousAlpha = 0f;
		float previousAge = 0f;
		boolean havePrevious = false;
		for (int n = 0; n < size; n++) {
			int at = index(n) * STRIDE;
			float age = now - data[at];
			if (age > lifetime) {
				break;
			}
			float life = Math.clamp(age / lifetime, 0f, 1f);
			float speed = speedAt(n, 2);
			float alpha = strength * (1f - life) * (1f - life) * FxMath.smoothstep(fullSpeed / 3f, fullSpeed, speed);

			// The band narrows towards the tip's path as it ages.
			float narrow = 0.55f * life;
			base1[0] = data[at + 1] + (data[at + 4] - data[at + 1]) * narrow + offsetX;
			base1[1] = data[at + 2] + (data[at + 5] - data[at + 2]) * narrow + offsetY;
			base1[2] = data[at + 3] + (data[at + 6] - data[at + 3]) * narrow + offsetZ;
			tip1[0] = data[at + 4] + offsetX;
			tip1[1] = data[at + 5] + offsetY;
			tip1[2] = data[at + 6] + offsetZ;

			if (havePrevious && joined(index(n - 1) * STRIDE, at, bladeLength) && (alpha > 0.004f || previousAlpha > 0.004f)) {
				float v0 = data[index(n - 1) * STRIDE + 7] / bladeLength;
				float v1 = data[at + 7] / bladeLength;
				out.ribbon(base0, tip0, base1, tip1, v0, v1, FxMath.fade(colors.at(previousAge), previousAlpha),
						FxMath.fade(colors.at(life), alpha), heat, roughness);
			}
			System.arraycopy(base1, 0, base0, 0, 3);
			System.arraycopy(tip1, 0, tip0, 0, 3);
			previousAlpha = alpha;
			previousAge = life;
			havePrevious = true;
		}
	}

	/** The slot of the n-th newest moment. */
	private int index(int n) {
		return Math.floorMod(newest - n, CAPACITY);
	}

	/** Whether two neighbouring moments (newer first) belong to one continuous movement. */
	private boolean joined(int newer, int older, float bladeLength) {
		float gap = data[newer] - data[older];
		float travelled = data[newer + 7] - data[older + 7];
		return gap <= MAX_GAP && travelled <= MAX_JUMP * bladeLength;
	}

	/** Tip speed around the n-th newest moment, measured across up to {@code reach} moments either side. */
	private float speedAt(int n, int reach) {
		if (size < 2) {
			return 0f;
		}
		int newer = index(Math.max(n - reach, 0)) * STRIDE;
		int older = index(Math.min(n + reach, size - 1)) * STRIDE;
		float span = data[newer] - data[older];
		if (span <= 1e-6f) {
			return 0f;
		}
		// Path length rather than straight distance, so a fast turn on the spot still counts as fast.
		return (data[newer + 7] - data[older + 7]) / span;
	}
}
