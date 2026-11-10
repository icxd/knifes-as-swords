package io.github.icxd.knives_as_swords.fx;

/**
 * Sparks that have a place in the world: thrown off where something happens, flying and burning
 * out there.
 *
 * <p>The world is too big for the small numbers sparks are counted in, so their positions are
 * kept relative to a spot nearby, which moves along when things happen somewhere else.
 */
public final class WorldSparks {
	/** Sparks further than this from where they are counted from are dropped rather than moved. */
	private static final double RANGE = 128.0;

	private final SparkSystem sparks;
	private double originX;
	private double originY;
	private double originZ;

	public WorldSparks(int capacity) {
		sparks = new SparkSystem(capacity);
	}

	public int count() {
		return sparks.count();
	}

	public void clear() {
		sparks.clear();
	}

	/**
	 * Starts a spark at a place in the world.
	 *
	 * @param life seconds until it has burnt out
	 * @param size its radius, in blocks
	 * @param heat how white-hot it looks, 0 to 1
	 */
	public void emit(double x, double y, double z, float velocityX, float velocityY, float velocityZ, float life,
			float size, int argb, float heat) {
		if (sparks.count() == 0 || Math.abs(x - originX) + Math.abs(y - originY) + Math.abs(z - originZ) > RANGE) {
			sparks.clear();
			originX = x;
			originY = y;
			originZ = z;
		}
		sparks.emit((float) (x - originX), (float) (y - originY), (float) (z - originZ), velocityX, velocityY,
				velocityZ, life, size, argb, heat);
	}

	/**
	 * Lets the frame's time pass and adds the sparks to the frame.
	 *
	 * @param gravity acceleration along y, in blocks per second squared: negative makes sparks
	 *     fall, positive makes them rise
	 * @param drag how quickly sparks lose their speed
	 * @param swirl how strongly sparks wander sideways
	 * @param strength overall visibility, 0 to 1
	 */
	public void draw(WorldFrame frame, float gravity, float drag, float swirl, float strength) {
		if (sparks.count() == 0) {
			return;
		}
		sparks.advance(frame.seconds(), gravity, drag, swirl);
		sparks.build(frame.batch(), frame.x(originX), frame.y(originY), frame.z(originZ), 0.04f, strength);
	}
}
