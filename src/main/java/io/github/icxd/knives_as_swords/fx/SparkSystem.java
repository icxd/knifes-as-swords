package io.github.icxd.knives_as_swords.fx;

/**
 * A swarm of sparks: points of light that are thrown off, fly, slow down and burn out.
 *
 * <p>Knows nothing about knives or the game; whoever owns it decides where sparks start and which
 * way is down. Positions are in whatever space the owner works in (metres).
 */
public final class SparkSystem {
	private final int capacity;
	private final float[] x;
	private final float[] y;
	private final float[] z;
	private final float[] vx;
	private final float[] vy;
	private final float[] vz;
	private final float[] age;
	private final float[] life;
	private final float[] size;
	private final float[] heat;
	private final int[] color;
	private final int[] seed;
	private int count;
	private int emitted;

	public SparkSystem(int capacity) {
		this.capacity = Math.max(capacity, 1);
		x = new float[this.capacity];
		y = new float[this.capacity];
		z = new float[this.capacity];
		vx = new float[this.capacity];
		vy = new float[this.capacity];
		vz = new float[this.capacity];
		age = new float[this.capacity];
		life = new float[this.capacity];
		size = new float[this.capacity];
		heat = new float[this.capacity];
		color = new int[this.capacity];
		seed = new int[this.capacity];
	}

	public void clear() {
		count = 0;
	}

	public int count() {
		return count;
	}

	/** Moves every spark by the same amount: for an owner whose space has shifted under it. */
	public void shift(float dx, float dy, float dz) {
		for (int i = 0; i < count; i++) {
			x[i] += dx;
			y[i] += dy;
			z[i] += dz;
		}
	}

	/**
	 * Starts a spark.
	 *
	 * @param life seconds until it has burnt out
	 * @param size its radius, in metres
	 * @param heat how white-hot it looks, 0 to 1
	 */
	public void emit(float px, float py, float pz, float velocityX, float velocityY, float velocityZ, float life,
			float size, int argb, float heat) {
		if (life <= 0f) {
			return;
		}
		// When full, the newcomer takes the place of an arbitrary older spark.
		int at = count < capacity ? count++ : Math.floorMod(FxMath.hash(emitted), capacity);
		x[at] = px;
		y[at] = py;
		z[at] = pz;
		vx[at] = velocityX;
		vy[at] = velocityY;
		vz[at] = velocityZ;
		age[at] = 0f;
		this.life[at] = life;
		this.size[at] = size;
		this.heat[at] = heat;
		color[at] = argb;
		seed[at] = FxMath.hash(emitted++);
	}

	/**
	 * Lets {@code seconds} pass, in a space whose up is +Y.
	 *
	 * @param gravity acceleration along y, in metres per second squared: negative makes sparks
	 *     fall, positive makes them rise like embers
	 * @param drag how quickly sparks lose their speed; 0 for none
	 * @param swirl how strongly sparks are pushed about sideways as they fly
	 */
	public void advance(float seconds, float gravity, float drag, float swirl) {
		advance(seconds, 0f, gravity, 0f, drag, swirl);
	}

	/**
	 * Lets {@code seconds} pass.
	 *
	 * @param gravityX the steady acceleration on every spark, in metres per second squared;
	 *     likewise {@code gravityY} and {@code gravityZ}
	 * @param drag how quickly sparks lose their speed; 0 for none
	 * @param swirl how strongly sparks are pushed about sideways as they fly
	 */
	public void advance(float seconds, float gravityX, float gravityY, float gravityZ, float drag, float swirl) {
		if (seconds <= 0f) {
			return;
		}
		float keep = (float) Math.exp(-drag * seconds);
		for (int i = 0; i < count; i++) {
			age[i] += seconds;
			if (age[i] >= life[i]) {
				// Burnt out: the last spark takes its place.
				int last = --count;
				x[i] = x[last];
				y[i] = y[last];
				z[i] = z[last];
				vx[i] = vx[last];
				vy[i] = vy[last];
				vz[i] = vz[last];
				age[i] = age[last];
				life[i] = life[last];
				size[i] = size[last];
				heat[i] = heat[last];
				color[i] = color[last];
				seed[i] = seed[last];
				i--;
				continue;
			}
			vx[i] += gravityX * seconds;
			vy[i] += gravityY * seconds;
			vz[i] += gravityZ * seconds;
			if (swirl != 0f) {
				float t = age[i] * 3.1f;
				vx[i] += FxMath.noise(t, seed[i]) * swirl * seconds;
				vy[i] += FxMath.noise(t, seed[i] + 1) * swirl * seconds * 0.5f;
				vz[i] += FxMath.noise(t, seed[i] + 2) * swirl * seconds;
			}
			vx[i] *= keep;
			vy[i] *= keep;
			vz[i] *= keep;
			x[i] += vx[i] * seconds;
			y[i] += vy[i] * seconds;
			z[i] += vz[i] * seconds;
		}
	}

	/**
	 * Adds every spark to {@code out}, moved by the given offset.
	 *
	 * @param streak seconds of flight a spark is smeared over: fast sparks become short lines
	 * @param strength overall visibility, 0 to 1
	 */
	public void build(FxBatch out, float offsetX, float offsetY, float offsetZ, float streak, float strength) {
		for (int i = 0; i < count; i++) {
			float t = age[i] / life[i];
			// Flares up at once, then dims and shrinks as it burns out.
			float glow = Math.min(1f, age[i] * 40f) * (1f - t) * (1f - t * t);
			float radius = size[i] * (1f - 0.5f * t);
			float half = streak * 0.5f;
			out.streak(x[i] + offsetX - vx[i] * half, y[i] + offsetY - vy[i] * half, z[i] + offsetZ - vz[i] * half,
					vx[i] * half, vy[i] * half, vz[i] * half, radius, FxMath.fade(color[i], glow * strength),
					heat[i]);
		}
	}

	public float x(int spark) {
		return x[spark];
	}

	public float y(int spark) {
		return y[spark];
	}

	public float z(int spark) {
		return z[spark];
	}

	public float age(int spark) {
		return age[spark];
	}
}
