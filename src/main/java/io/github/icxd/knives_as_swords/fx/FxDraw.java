package io.github.icxd.knives_as_swords.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.icxd.knives_as_swords.model.KnifeMesh;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Hands effects to Minecraft's renderer: the quads of a batch, and knives drawn as light. */
public final class FxDraw {
	/** Transformed vertices, reused between draws. Geometry is only ever built on the render thread. */
	private static float[] scratchPositions = new float[0];
	private static float[] scratchNormals = new float[0];

	private FxDraw() {
	}

	/**
	 * Draws a batch where the pose stack currently is. The batch must stay as it is until the
	 * frame has been drawn.
	 */
	public static void batch(FxBatch batch, PoseStack poseStack, SubmitNodeCollector collector) {
		if (batch.isEmpty()) {
			return;
		}
		collector.submitCustomGeometry(poseStack, FxPipelines.energy(), (pose, buffer) -> {
			Matrix4f matrix = pose.pose();
			int count = batch.vertexCount();
			for (int i = 0; i < count; i++) {
				// The slot for a normal carries what the shape is instead; see the shader.
				buffer.addVertex(matrix, batch.position(i, 0), batch.position(i, 1), batch.position(i, 2))
						.setUv(batch.u(i), batch.v(i))
						.setColor(batch.color(i))
						.setNormal(batch.kind(i), batch.heat(i) * 2f - 1f, batch.roughness(i) * 2f - 1f);
			}
		});
	}

	/**
	 * Draws a knife as a shell of light where the pose stack currently is.
	 *
	 * @param positions vertex positions to draw: the mesh's own, or a posed copy of them. Must stay
	 *     as they are until the frame has been drawn
	 * @param normals the matching normals
	 * @param argb colour, and in its alpha how strong the light is
	 * @param swell how far the shell stands off the knife's surface, in the units of {@code positions}
	 * @param bands how many bands of brightness lie along the knife
	 * @param phase where the bands are; counting up moves them towards the point
	 * @param fill how much the whole surface glows, on top of its bright outline
	 * @param mirrored whether the pose stack holds a mirroring transform of the caller's own
	 */
	public static void ghost(KnifeMesh mesh, BladeShape blade, float[] positions, float[] normals, PoseStack poseStack,
			SubmitNodeCollector collector, int argb, float swell, float bands, float phase, float fill,
			boolean mirrored) {
		if ((argb >>> 24) == 0) {
			return;
		}
		collector.submitCustomGeometry(poseStack, FxPipelines.ghost(), (pose, buffer) -> emitGhost(mesh, blade,
				positions, normals, pose, buffer, argb, swell, bands, phase, fill, mirrored));
	}

	private static void emitGhost(KnifeMesh mesh, BladeShape blade, float[] positions, float[] normals,
			PoseStack.Pose pose, VertexConsumer buffer, int argb, float swell, float bands, float phase, float fill,
			boolean mirrored) {
		int vertexCount = mesh.vertexCount();
		if (scratchPositions.length < vertexCount * 3) {
			scratchPositions = new float[vertexCount * 3];
			scratchNormals = new float[vertexCount * 3];
		}
		float[] outPositions = scratchPositions;
		float[] outNormals = scratchNormals;

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
			float nx = normals[i], ny = normals[i + 1], nz = normals[i + 2];
			float x = positions[i] + nx * swell, y = positions[i + 1] + ny * swell, z = positions[i + 2] + nz * swell;
			outPositions[i] = m00 * x + m10 * y + m20 * z + m30;
			outPositions[i + 1] = m01 * x + m11 * y + m21 * z + m31;
			outPositions[i + 2] = m02 * x + m12 * y + m22 * z + m32;

			float tx = n00 * nx + n10 * ny + n20 * nz;
			float ty = n01 * nx + n11 * ny + n21 * nz;
			float tz = n02 * nx + n12 * ny + n22 * nz;
			float length = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
			float inverse = length > 1e-12f ? 1f / length : 0f;
			outNormals[i] = tx * inverse;
			outNormals[i + 1] = ty * inverse;
			outNormals[i + 2] = tz * inverse;
		}

		// The bands lie along the knife as it is at rest, whatever pose it is drawn in.
		float[] rest = mesh.positions();
		int[] indices = mesh.indices();
		for (int i = 0; i < indices.length; i += 3) {
			ghostVertex(buffer, outPositions, outNormals, rest, blade, indices[i], argb, bands, phase, fill);
			ghostVertex(buffer, outPositions, outNormals, rest, blade, indices[mirrored ? i + 2 : i + 1], argb, bands,
					phase, fill);
			ghostVertex(buffer, outPositions, outNormals, rest, blade, indices[mirrored ? i + 1 : i + 2], argb, bands,
					phase, fill);
		}
	}

	private static void ghostVertex(VertexConsumer buffer, float[] positions, float[] normals, float[] rest,
			BladeShape blade, int index, int argb, float bands, float phase, float fill) {
		int p = index * 3;
		buffer.addVertex(positions[p], positions[p + 1], positions[p + 2])
				.setUv(blade.along(rest[p + 1]) * bands - phase, fill)
				.setColor(argb)
				.setNormal(normals[p], normals[p + 1], normals[p + 2]);
	}
}
