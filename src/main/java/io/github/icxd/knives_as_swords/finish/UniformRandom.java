package io.github.icxd.knives_as_swords.finish;

/**
 * The "minimal standard" random number generator with a shuffle table (Park and Miller, with the
 * Bays-Durham shuffle), which is what the pattern seed feeds in the game these finishes come
 * from. The same seed always produces the same sequence.
 */
final class UniformRandom {
	private static final int IA = 16807;
	private static final int IM = 2147483647;
	private static final int IQ = 127773;
	private static final int IR = 2836;
	private static final int TABLE_SIZE = 32;
	private static final int DIVISOR = 1 + (IM - 1) / TABLE_SIZE;
	private static final float SCALE = 1.0f / IM;
	private static final float BELOW_ONE = 1.0f - 1.2e-7f;

	private final int[] table = new int[TABLE_SIZE];
	private int state;
	private int last;

	UniformRandom(int seed) {
		state = seed < 0 ? seed : -seed;
		last = 0;
	}

	private int next() {
		if (state <= 0 || last == 0) {
			state = -state < 1 ? 1 : -state;
			for (int j = TABLE_SIZE + 7; j >= 0; j--) {
				int k = state / IQ;
				state = IA * (state - k * IQ) - IR * k;
				if (state < 0) {
					state += IM;
				}
				if (j < TABLE_SIZE) {
					table[j] = state;
				}
			}
			last = table[0];
		}
		int k = state / IQ;
		state = IA * (state - k * IQ) - IR * k;
		if (state < 0) {
			state += IM;
		}
		int j = last / DIVISOR;
		last = table[j];
		table[j] = state;
		return last;
	}

	/** A value between {@code low} and {@code high}, which may be given in either order. */
	float between(float low, float high) {
		float unit = Math.min(SCALE * next(), BELOW_ONE);
		return unit * (high - low) + low;
	}
}
