package io.github.icxd.knives_as_swords.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.icxd.knives_as_swords.model.KnifeMesh;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Feeds a knife's triangles to one of Minecraft's entity render types. */
final class KnifeGeometry {
	private static final int WHITE = 0xFFFFFFFF;

	/** Transformed vertices, reused between draws. Geometry is only ever built on the render thread. */
	private static float[] scratchPositions = new float[0];
	private static float[] scratchNormals = new float[0];

	private KnifeGeometry() {
	}

	/**
	 * @param positions vertex positions to draw: the mesh's own, or a posed copy of them
	 * @param normals the matching normals
	 * @param mirrored whether the caller put a mirroring transform on the pose itself. Triangles
	 *     are then wound the other way round, so they still face outwards. Minecraft's own flipped
	 *     axes (in inventories, for example) are not a case of this: its projection flips back.
	 */
	static void emit(KnifeMesh mesh, float[] positions, float[] normals, PoseStack.Pose pose, VertexConsumer buffer,
			int lightCoords, int overlayCoords, boolean mirrored) {
		int vertexCount = mesh.vertexCount();
		if (scratchPositions.length < vertexCount * 3) {
			scratchPositions = new float[vertexCount * 3];
			scratchNormals = new float[vertexCount * 3];
		}
		float[] outPositions = scratchPositions;
		float[] outNormals = scratchNormals;

		// Every vertex is shared by several triangles, so transform each one once up front.
		Matrix4f matrix = pose.pose();
		Matrix3f normalMatrix = pose.normal();
		float m00 = matrix.m00(), m01 = matrix.m01(), m02 = matrix.m02();
		float m10 = matrix.m10(), m11 = matrix.m11(), m12 = matrix.m12();
		float m20 = matrix.m20(), m21 = matrix.m21(), m22 = matrix.m22();
		float m30 = matrix.m30(), m31 = matrix.m31(), m32 = matrix.m32();
		float n00 = normalMatrix.m00(), n01 = normalMatrix.m01(), n02 = normalMatrix.m02();
		float n10 = normalMatrix.m10(), n11 = normalMatrix.m11(), n12 = normalMatrix.m12();
		float n20 = normalMatrix.m20(), n21 = normalMatrix.m21(), n22 = normalMatrix.m22();
		for (int i = 0; i < vertexCount * 3; i += 3) {
			float x = positions[i], y = positions[i + 1], z = positions[i + 2];
			outPositions[i] = m00 * x + m10 * y + m20 * z + m30;
			outPositions[i + 1] = m01 * x + m11 * y + m21 * z + m31;
			outPositions[i + 2] = m02 * x + m12 * y + m22 * z + m32;

			float nx = normals[i], ny = normals[i + 1], nz = normals[i + 2];
			float tx = n00 * nx + n10 * ny + n20 * nz;
			float ty = n01 * nx + n11 * ny + n21 * nz;
			float tz = n02 * nx + n12 * ny + n22 * nz;
			float length = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
			float inverse = length > 1e-12f ? 1f / length : 0f;
			outNormals[i] = tx * inverse;
			outNormals[i + 1] = ty * inverse;
			outNormals[i + 2] = tz * inverse;
		}

		float[] uvs = mesh.uvs();
		int[] indices = mesh.indices();
		for (int i = 0; i < indices.length; i += 3) {
			int a = indices[i];
			int b = indices[mirrored ? i + 2 : i + 1];
			int c = indices[mirrored ? i + 1 : i + 2];
			// The render type draws quads, so each triangle goes in as a quad with a doubled corner.
			vertex(buffer, outPositions, outNormals, uvs, a, lightCoords, overlayCoords);
			vertex(buffer, outPositions, outNormals, uvs, b, lightCoords, overlayCoords);
			vertex(buffer, outPositions, outNormals, uvs, c, lightCoords, overlayCoords);
			vertex(buffer, outPositions, outNormals, uvs, c, lightCoords, overlayCoords);
		}
	}

	private static void vertex(VertexConsumer buffer, float[] positions, float[] normals, float[] uvs, int index,
			int lightCoords, int overlayCoords) {
		int p = index * 3;
		int t = index * 2;
		buffer.addVertex(positions[p], positions[p + 1], positions[p + 2], WHITE, uvs[t], uvs[t + 1], overlayCoords,
				lightCoords, normals[p], normals[p + 1], normals[p + 2]);
	}
}
