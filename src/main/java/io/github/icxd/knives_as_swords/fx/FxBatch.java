package io.github.icxd.knives_as_swords.fx;

import java.util.Arrays;

/**
 * A list of glowing quads, built on the client thread and drawn later in the same frame.
 *
 * <p>Everything an effect draws apart from the knife itself ends up here: ribbons, bolts, sparks
 * and soft lights. Each vertex carries a position, a colour, a place within its shape (u, v) and
 * what the shape is; the shader turns that into light with soft edges.
 *
 * <p>Shapes that should face the viewer need to know where the viewer is; see {@link #eye} and
 * {@link #viewDirection}.
 */
public final class FxBatch {
	/** A band swept by the blade: u runs from the blade's base (0) to its tip (1), v along the sweep. */
	public static final float RIBBON = -1f;
	/** A line of light: brightest along its middle (u = 0.5), v along its length. */
	public static final float BEAM = 0f;
	/** A round light: brightest at u = v = 0.5. */
	public static final float DOT = 1f;
	/** A thin circle of light around u = v = 0.5, reaching most of the way to the quad's edges. */
	public static final float RING = 0.5f;
	/** Light of one strength all over the quad: for filling in faces. */
	public static final float PLAIN = -0.5f;

	/** Floats per vertex: position, u, v, kind, heat, roughness. */
	static final int STRIDE = 8;

	private float[] data = new float[STRIDE * 4 * 64];
	private int[] colors = new int[4 * 64];
	private int vertices;

	private boolean perspective = true;
	private float eyeX;
	private float eyeY;
	private float eyeZ;

	public void clear() {
		vertices = 0;
	}

	public boolean isEmpty() {
		return vertices == 0;
	}

	public int vertexCount() {
		return vertices;
	}

	public int quadCount() {
		return vertices / 4;
	}

	/** The viewer sits at this point (in the space the batch is built in), as in the world. */
	public void eye(float x, float y, float z) {
		perspective = true;
		eyeX = x;
		eyeY = y;
		eyeZ = z;
	}

	/** The viewer is far away in this direction (pointing from the scene to the viewer), as in menus. */
	public void viewDirection(float x, float y, float z) {
		perspective = false;
		float length = (float) Math.sqrt(x * x + y * y + z * z);
		float inverse = length > 1e-9f ? 1f / length : 0f;
		eyeX = x * inverse;
		eyeY = y * inverse;
		eyeZ = length > 1e-9f ? z * inverse : 1f;
	}

	/**
	 * One quad of a ribbon, between two moments of the blade's sweep.
	 *
	 * @param base0 the blade's base at the first moment (x, y, z); likewise the others
	 * @param v0 how far along the sweep the first moment is; continues from quad to quad
	 * @param color0 colour at the first moment; its alpha is how much of the ribbon is left there
	 */
	public void ribbon(float[] base0, float[] tip0, float[] base1, float[] tip1, float v0, float v1, int color0,
			int color1, float heat, float roughness) {
		vertex(base0[0], base0[1], base0[2], 0f, v0, color0, RIBBON, heat, roughness);
		vertex(tip0[0], tip0[1], tip0[2], 1f, v0, color0, RIBBON, heat, roughness);
		vertex(tip1[0], tip1[1], tip1[2], 1f, v1, color1, RIBBON, heat, roughness);
		vertex(base1[0], base1[1], base1[2], 0f, v1, color1, RIBBON, heat, roughness);
	}

	/**
	 * A line of light through the given points, turned to face the viewer.
	 *
	 * @param points x, y, z per point
	 * @param count number of points; fewer than two draw nothing
	 * @param halfWidth half the width of the glow around the line
	 * @param taper whether the line thins out and fades towards both ends
	 */
	public void polyline(float[] points, int count, float halfWidth, int argb, float heat, float roughness,
			boolean taper) {
		if (count < 2) {
			return;
		}
		float lastLeftX = 0f, lastLeftY = 0f, lastLeftZ = 0f, lastRightX = 0f, lastRightY = 0f, lastRightZ = 0f;
		int lastColor = 0;
		for (int i = 0; i < count; i++) {
			int p = i * 3;
			int before = Math.max(i - 1, 0) * 3;
			int after = Math.min(i + 1, count - 1) * 3;
			float tx = points[after] - points[before];
			float ty = points[after + 1] - points[before + 1];
			float tz = points[after + 2] - points[before + 2];

			// Sideways is at right angles to both the line and the line of sight.
			float vx = perspective ? eyeX - points[p] : eyeX;
			float vy = perspective ? eyeY - points[p + 1] : eyeY;
			float vz = perspective ? eyeZ - points[p + 2] : eyeZ;
			float sx = ty * vz - tz * vy;
			float sy = tz * vx - tx * vz;
			float sz = tx * vy - ty * vx;
			float length = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
			if (length < 1e-9f) {
				// Seen end-on; any sideways direction will do.
				sx = 1f;
				sy = 0f;
				sz = 0f;
				length = 1f;
			}
			float along = i / (float) (count - 1);
			float ends = taper ? (float) Math.sin(Math.PI * Math.clamp(along, 0.04f, 0.96f)) : 1f;
			float width = halfWidth * (taper ? 0.45f + 0.55f * ends : 1f) / length;
			sx *= width;
			sy *= width;
			sz *= width;

			float leftX = points[p] - sx, leftY = points[p + 1] - sy, leftZ = points[p + 2] - sz;
			float rightX = points[p] + sx, rightY = points[p + 1] + sy, rightZ = points[p + 2] + sz;
			int color = taper ? FxMath.fade(argb, 0.25f + 0.75f * ends) : argb;
			if (i > 0) {
				float v0 = (i - 1) / (float) (count - 1);
				vertex(lastLeftX, lastLeftY, lastLeftZ, 0f, v0, lastColor, BEAM, heat, roughness);
				vertex(lastRightX, lastRightY, lastRightZ, 1f, v0, lastColor, BEAM, heat, roughness);
				vertex(rightX, rightY, rightZ, 1f, along, color, BEAM, heat, roughness);
				vertex(leftX, leftY, leftZ, 0f, along, color, BEAM, heat, roughness);
			}
			lastLeftX = leftX;
			lastLeftY = leftY;
			lastLeftZ = leftZ;
			lastRightX = rightX;
			lastRightY = rightY;
			lastRightZ = rightZ;
			lastColor = color;
		}
	}

	/**
	 * A quad lying wherever it is put rather than facing the viewer: a ring on the ground, a face
	 * of a block.
	 *
	 * @param x the quad's middle; likewise {@code y} and {@code z}
	 * @param ux from the middle to the middle of one side; {@code vx} to the middle of a side next to it
	 * @param kind {@link #DOT}, {@link #RING} or {@link #PLAIN}
	 */
	public void flat(float x, float y, float z, float ux, float uy, float uz, float vx, float vy, float vz, int argb,
			float heat, float kind) {
		vertex(x - ux - vx, y - uy - vy, z - uz - vz, 0f, 0f, argb, kind, heat, 0f);
		vertex(x + ux - vx, y + uy - vy, z + uz - vz, 1f, 0f, argb, kind, heat, 0f);
		vertex(x + ux + vx, y + uy + vy, z + uz + vz, 1f, 1f, argb, kind, heat, 0f);
		vertex(x - ux + vx, y - uy + vy, z - uz + vz, 0f, 1f, argb, kind, heat, 0f);
	}

	/**
	 * A quad of plain light between four corners, each with a colour of its own, blending across.
	 * Two corners at the same place make a triangle.
	 *
	 * @param corners x, y, z of each of the four corners, going round
	 * @param colors the colour at each corner
	 */
	public void plain(float[] corners, int[] colors) {
		for (int i = 0; i < 4; i++) {
			vertex(corners[i * 3], corners[i * 3 + 1], corners[i * 3 + 2], i == 1 || i == 2 ? 1f : 0f, i >= 2 ? 1f : 0f,
					colors[i], PLAIN, 0f, 0f);
		}
	}

	/** A round, soft light facing the viewer. */
	public void dot(float x, float y, float z, float radius, int argb, float heat) {
		sprite(x, y, z, 0f, 0f, 0f, radius, argb, heat, DOT);
	}

	/** A thin circle of light facing the viewer: a shock running outwards, when drawn growing. */
	public void ring(float x, float y, float z, float radius, int argb, float heat) {
		sprite(x, y, z, 0f, 0f, 0f, radius, argb, heat, RING);
	}

	/**
	 * A soft light stretched along a direction, facing the viewer: a spark in flight.
	 *
	 * @param ax half the stretch (x, y, z), from the middle to one end; zero gives a round dot
	 * @param radius half its width, and at least half its length
	 */
	public void streak(float x, float y, float z, float ax, float ay, float az, float radius, int argb, float heat) {
		sprite(x, y, z, ax, ay, az, radius, argb, heat, DOT);
	}

	private void sprite(float x, float y, float z, float ax, float ay, float az, float radius, int argb, float heat,
			float kind) {
		float vx = perspective ? eyeX - x : eyeX;
		float vy = perspective ? eyeY - y : eyeY;
		float vz = perspective ? eyeZ - z : eyeZ;
		float viewLength = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
		if (viewLength < 1e-9f) {
			return;
		}
		vx /= viewLength;
		vy /= viewLength;
		vz /= viewLength;

		// The long axis: the stretch as the viewer sees it, or simply "up" for a round dot.
		float along = ax * vx + ay * vy + az * vz;
		float lx = ax - vx * along, ly = ay - vy * along, lz = az - vz * along;
		float longLength = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
		if (longLength < radius * 0.05f) {
			// Round, or flying straight at the viewer: pick an axis that is not the line of sight.
			float ux = Math.abs(vy) < 0.9f ? 0f : 1f;
			float uy = Math.abs(vy) < 0.9f ? 1f : 0f;
			float dot = ux * vx + uy * vy;
			lx = ux - vx * dot;
			ly = uy - vy * dot;
			lz = -vz * dot;
			longLength = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
			float scale = radius / longLength;
			lx *= scale;
			ly *= scale;
			lz *= scale;
		} else {
			float scale = Math.max(longLength, radius) / longLength;
			lx *= scale;
			ly *= scale;
			lz *= scale;
		}
		float halfLength = (float) Math.sqrt(lx * lx + ly * ly + lz * lz);
		float sx = (ly * vz - lz * vy) / halfLength * radius;
		float sy = (lz * vx - lx * vz) / halfLength * radius;
		float sz = (lx * vy - ly * vx) / halfLength * radius;

		vertex(x - lx - sx, y - ly - sy, z - lz - sz, 0f, 0f, argb, kind, heat, 0f);
		vertex(x - lx + sx, y - ly + sy, z - lz + sz, 1f, 0f, argb, kind, heat, 0f);
		vertex(x + lx + sx, y + ly + sy, z + lz + sz, 1f, 1f, argb, kind, heat, 0f);
		vertex(x + lx - sx, y + ly - sy, z + lz - sz, 0f, 1f, argb, kind, heat, 0f);
	}

	private void vertex(float x, float y, float z, float u, float v, int argb, float kind, float heat,
			float roughness) {
		if (vertices == colors.length) {
			colors = Arrays.copyOf(colors, colors.length * 2);
			data = Arrays.copyOf(data, data.length * 2);
		}
		int at = vertices * STRIDE;
		data[at] = x;
		data[at + 1] = y;
		data[at + 2] = z;
		data[at + 3] = u;
		data[at + 4] = v;
		data[at + 5] = kind;
		data[at + 6] = heat;
		data[at + 7] = roughness;
		colors[vertices++] = argb;
	}

	/** Position of a vertex: {@code axis} 0, 1 or 2 for x, y, z. */
	public float position(int vertex, int axis) {
		return data[vertex * STRIDE + axis];
	}

	public float u(int vertex) {
		return data[vertex * STRIDE + 3];
	}

	public float v(int vertex) {
		return data[vertex * STRIDE + 4];
	}

	/** {@link #RIBBON}, {@link #PLAIN}, {@link #BEAM}, {@link #RING} or {@link #DOT}. */
	public float kind(int vertex) {
		return data[vertex * STRIDE + 5];
	}

	/** How white-hot the brightest part burns, 0 to 1. */
	public float heat(int vertex) {
		return data[vertex * STRIDE + 6];
	}

	/** How ragged the shape is, 0 to 1. */
	public float roughness(int vertex) {
		return data[vertex * STRIDE + 7];
	}

	public int color(int vertex) {
		return colors[vertex];
	}
}
