package io.github.icxd.knives_as_swords.fx;

import io.github.icxd.knives_as_swords.model.KnifeMesh;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Where a knife's blade is, worked out from its triangles: effects need to know which part cuts.
 *
 * <p>All points are in the model's own space and rest pose (metres, the blade along +Y, the
 * cutting edge towards +Z). On folding knives the blade follows a bone of its own; {@link #joint}
 * says which, so the points can be moved along with it.
 */
public final class BladeShape {
	/** Most points on the blade's surface kept for effects to start from. */
	private static final int MAX_ANCHORS = 64;
	/** On a knife made of one piece, the blade is taken to start no lower than this fraction of its length. */
	private static final float MIN_BLADE_START = 0.3f;
	private static final float MAX_BLADE_START = 0.6f;

	private final int joint;
	private final Vector3f base = new Vector3f();
	private final Vector3f tip = new Vector3f();
	private final Vector3f center = new Vector3f();
	private final float length;
	private final float lowest;
	private final float highest;
	private final float[] anchors;
	private final float[] anchorNormals;

	private BladeShape(int joint, Vector3f base, Vector3f tip, Vector3f center, float lowest, float highest,
			float[] anchors, float[] anchorNormals) {
		this.joint = joint;
		this.base.set(base);
		this.tip.set(tip);
		this.center.set(center);
		this.length = Math.max(base.distance(tip), 1e-4f);
		this.lowest = lowest;
		this.highest = highest;
		this.anchors = anchors;
		this.anchorNormals = anchorNormals;
	}

	public static BladeShape of(KnifeMesh mesh) {
		return of(mesh.positions(), mesh.normals(), mesh.joints(), mesh.weights(), mesh.singleJoint() < 0);
	}

	/**
	 * @param positions x, y, z per vertex
	 * @param normals unit normals, x, y, z per vertex
	 * @param joints {@link KnifeMesh#INFLUENCES} bone indices per vertex; empty when not skinned
	 * @param weights the matching weights
	 * @param severalParts whether different vertices follow different bones
	 */
	public static BladeShape of(float[] positions, float[] normals, int[] joints, float[] weights,
			boolean severalParts) {
		int vertexCount = positions.length / 3;
		if (vertexCount == 0) {
			return new BladeShape(0, new Vector3f(), new Vector3f(0f, 0.1f, 0f), new Vector3f(0f, 0.05f, 0f), 0f, 0.1f,
					new float[] {0f, 0.05f, 0f}, new float[] {1f, 0f, 0f});
		}
		boolean skinned = joints.length >= vertexCount * KnifeMesh.INFLUENCES;

		// The point of the blade is the vertex furthest along it.
		int tipVertex = 0;
		float lowest = Float.POSITIVE_INFINITY;
		float highest = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < vertexCount; i++) {
			float y = positions[i * 3 + 1];
			lowest = Math.min(lowest, y);
			if (y > highest) {
				highest = y;
				tipVertex = i;
			}
		}
		int joint = skinned ? mainJoint(joints, weights, tipVertex) : 0;

		// What moves with the point's bone is the blade, on a knife of several parts.
		boolean byBone = skinned && severalParts;
		float start;
		if (byBone) {
			float partLowest = Float.POSITIVE_INFINITY;
			for (int i = 0; i < vertexCount; i++) {
				if (mainJoint(joints, weights, i) == joint) {
					partLowest = Math.min(partLowest, positions[i * 3 + 1]);
				}
			}
			start = partLowest + 0.05f * (highest - partLowest);
			// A "part" reaching all the way down is the whole knife after all.
			if (partLowest - lowest < 0.1f * (highest - lowest)) {
				start = wholeKnifeStart(lowest, highest);
			}
		} else {
			start = wholeKnifeStart(lowest, highest);
		}

		int bladeVertices = 0;
		Vector3f center = new Vector3f();
		// Where the cutting edge begins: the vertex furthest towards the edge in the blade's lowest tenth.
		float baseBand = start + 0.1f * (highest - start);
		float baseEdge = Float.NEGATIVE_INFINITY;
		Vector3f base = new Vector3f(positions[tipVertex * 3], start, positions[tipVertex * 3 + 2]);
		for (int i = 0; i < vertexCount; i++) {
			float x = positions[i * 3], y = positions[i * 3 + 1], z = positions[i * 3 + 2];
			if (y < start || (byBone && mainJoint(joints, weights, i) != joint)) {
				continue;
			}
			bladeVertices++;
			center.add(x, y, z);
			if (y <= baseBand && z > baseEdge) {
				baseEdge = z;
				base.set(x, y, z);
			}
		}
		Vector3f tip = new Vector3f(positions[tipVertex * 3], positions[tipVertex * 3 + 1], positions[tipVertex * 3 + 2]);
		if (bladeVertices == 0) {
			center.set(base).add(tip).mul(0.5f);
			bladeVertices = 1;
		} else {
			center.div(bladeVertices);
		}

		// A spread of points on the blade's surface, always the same ones for the same knife.
		int anchorCount = Math.min(bladeVertices, MAX_ANCHORS);
		float[] anchors = new float[anchorCount * 3];
		float[] anchorNormals = new float[anchorCount * 3];
		int seen = 0;
		int taken = 0;
		for (int i = 0; i < vertexCount && taken < anchorCount; i++) {
			float y = positions[i * 3 + 1];
			if (y < start || (byBone && mainJoint(joints, weights, i) != joint)) {
				continue;
			}
			// Take the vertex when it is the next one due, spreading the picks evenly over the blade's vertices.
			if (seen * (long) anchorCount / bladeVertices == taken) {
				System.arraycopy(positions, i * 3, anchors, taken * 3, 3);
				if (normals.length >= (i + 1) * 3) {
					System.arraycopy(normals, i * 3, anchorNormals, taken * 3, 3);
				} else {
					anchorNormals[taken * 3] = 1f;
				}
				taken++;
			}
			seen++;
		}
		if (taken == 0) {
			anchors = new float[] {center.x, center.y, center.z};
			anchorNormals = new float[] {1f, 0f, 0f};
		} else if (taken < anchorCount) {
			anchors = java.util.Arrays.copyOf(anchors, taken * 3);
			anchorNormals = java.util.Arrays.copyOf(anchorNormals, taken * 3);
		}
		return new BladeShape(joint, base, tip, center, lowest, highest, anchors, anchorNormals);
	}

	private static float wholeKnifeStart(float lowest, float highest) {
		float length = highest - lowest;
		// Knife models have their origin where blade and handle meet; trust that within reason.
		return Math.clamp(0f, lowest + MIN_BLADE_START * length, lowest + MAX_BLADE_START * length);
	}

	/** The bone with the most say over a vertex. */
	private static int mainJoint(int[] joints, float[] weights, int vertex) {
		int best = 0;
		float bestWeight = -1f;
		for (int k = 0; k < KnifeMesh.INFLUENCES; k++) {
			int at = vertex * KnifeMesh.INFLUENCES + k;
			if (weights[at] > bestWeight) {
				bestWeight = weights[at];
				best = joints[at];
			}
		}
		return best;
	}

	/** The bone the blade follows; 0 on a knife without bones. */
	public int joint() {
		return joint;
	}

	/** Where the cutting edge begins, next to the handle. */
	public Vector3fc base() {
		return base;
	}

	/** The point of the blade. */
	public Vector3fc tip() {
		return tip;
	}

	/** The middle of the blade. */
	public Vector3fc center() {
		return center;
	}

	/** From {@link #base()} to {@link #tip()}, in metres. */
	public float length() {
		return length;
	}

	/** How far along the whole knife a height is: 0 at the end of the handle, 1 at the point. */
	public float along(float y) {
		return highest > lowest ? (y - lowest) / (highest - lowest) : 0f;
	}

	public int anchorCount() {
		return anchors.length / 3;
	}

	/** Points on the blade's surface, x, y, z each. */
	public float[] anchors() {
		return anchors;
	}

	/** Which way the surface faces at each of {@link #anchors()}. */
	public float[] anchorNormals() {
		return anchorNormals;
	}
}
