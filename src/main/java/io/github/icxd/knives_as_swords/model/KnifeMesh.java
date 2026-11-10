package io.github.icxd.knives_as_swords.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.icxd.knives_as_swords.gltf.GlbFile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The triangles of one converted knife, in glTF space: metres, +Y along the blade, the knife
 * lying flat in the YZ plane.
 *
 * <p>All arrays are shared and must not be modified.
 */
public final class KnifeMesh {
	/** Maximum number of bones influencing one vertex. */
	public static final int INFLUENCES = 4;

	private final float[] positions;
	private final float[] normals;
	private final float[] uvs;
	private final int[] indices;
	private final int[] joints;
	private final float[] weights;
	private final int jointCount;
	private final int singleJoint;
	private final Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
	private final Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
	private final Optional<Path> baseColorImage;
	private final Optional<Path> surfaceImage;
	private final Optional<String> material;

	private KnifeMesh(float[] positions, float[] normals, float[] uvs, int[] indices, int[] joints, float[] weights,
			Optional<Path> baseColorImage, Optional<Path> surfaceImage, Optional<String> material) {
		this.positions = positions;
		this.normals = normals;
		this.uvs = uvs;
		this.indices = indices;
		this.joints = joints;
		this.weights = weights;
		this.baseColorImage = baseColorImage;
		this.surfaceImage = surfaceImage;
		this.material = material;

		for (int i = 0; i < positions.length; i += 3) {
			min.min(new Vector3f(positions[i], positions[i + 1], positions[i + 2]));
			max.max(new Vector3f(positions[i], positions[i + 1], positions[i + 2]));
		}
		int highest = -1;
		int single = joints.length > 0 ? joints[0] : 0;
		for (int i = 0; i < joints.length; i++) {
			if (weights[i] > 0f) {
				highest = Math.max(highest, joints[i]);
				if (joints[i] != single) {
					single = -1;
				}
			}
		}
		this.jointCount = highest + 1;
		this.singleJoint = single;
	}

	/** Reads every mesh of the file's scene into one triangle list. */
	public static KnifeMesh load(Path glb) throws IOException {
		return load(GlbFile.read(glb));
	}

	public static KnifeMesh load(GlbFile glb) throws IOException {
		Builder builder = new Builder(glb);
		JsonArray nodes = glb.array("nodes");
		JsonArray scenes = glb.array("scenes");
		if (scenes.isEmpty()) {
			for (int i = 0; i < nodes.size(); i++) {
				builder.node(i, new Matrix4f(), 0);
			}
		} else {
			int scene = Math.clamp(glb.json().has("scene") ? glb.json().get("scene").getAsInt() : 0, 0,
					scenes.size() - 1);
			JsonElement roots = scenes.get(scene).getAsJsonObject().get("nodes");
			if (roots != null) {
				for (JsonElement root : roots.getAsJsonArray()) {
					builder.node(root.getAsInt(), new Matrix4f(), 0);
				}
			}
		}
		return builder.build();
	}

	public int vertexCount() {
		return positions.length / 3;
	}

	public int triangleCount() {
		return indices.length / 3;
	}

	/** {@code x, y, z} per vertex. */
	public float[] positions() {
		return positions;
	}

	/** Unit normals, {@code x, y, z} per vertex. */
	public float[] normals() {
		return normals;
	}

	/** {@code u, v} per vertex, with v pointing down as in image files. */
	public float[] uvs() {
		return uvs;
	}

	/** Three vertex indices per triangle, counter-clockwise seen from outside. */
	public int[] indices() {
		return indices;
	}

	/** {@link #INFLUENCES} bone indices per vertex; empty when the model is not skinned. */
	public int[] joints() {
		return joints;
	}

	/** {@link #INFLUENCES} bone weights per vertex, summing to 1; empty when the model is not skinned. */
	public float[] weights() {
		return weights;
	}

	public boolean skinned() {
		return joints.length > 0;
	}

	/** One more than the highest bone index that actually moves a vertex; 0 when not skinned. */
	public int jointCount() {
		return jointCount;
	}

	/**
	 * The bone every vertex is fully bound to, when the whole knife moves as one piece; -1 when
	 * different parts follow different bones (folding knives). 0 when not skinned.
	 */
	public int singleJoint() {
		return singleJoint;
	}

	/**
	 * Moves every vertex with its bones.
	 *
	 * @param jointMatrices for each bone, the transform from the model's rest pose to its current pose
	 * @param outPositions receives the posed positions; at least as long as {@link #positions()}
	 * @param outNormals receives the posed normals; at least as long as {@link #normals()}
	 */
	public void skin(Matrix4f[] jointMatrices, float[] outPositions, float[] outNormals) {
		if (!skinned()) {
			System.arraycopy(positions, 0, outPositions, 0, positions.length);
			System.arraycopy(normals, 0, outNormals, 0, normals.length);
			return;
		}
		int vertexCount = vertexCount();
		for (int vertex = 0; vertex < vertexCount; vertex++) {
			int p = vertex * 3;
			float x = positions[p], y = positions[p + 1], z = positions[p + 2];
			float nx = normals[p], ny = normals[p + 1], nz = normals[p + 2];
			float px = 0f, py = 0f, pz = 0f, qx = 0f, qy = 0f, qz = 0f;
			for (int k = 0; k < INFLUENCES; k++) {
				float weight = weights[vertex * INFLUENCES + k];
				if (weight <= 0f) {
					continue;
				}
				Matrix4f m = jointMatrices[Math.min(joints[vertex * INFLUENCES + k], jointMatrices.length - 1)];
				px += weight * (m.m00() * x + m.m10() * y + m.m20() * z + m.m30());
				py += weight * (m.m01() * x + m.m11() * y + m.m21() * z + m.m31());
				pz += weight * (m.m02() * x + m.m12() * y + m.m22() * z + m.m32());
				qx += weight * (m.m00() * nx + m.m10() * ny + m.m20() * nz);
				qy += weight * (m.m01() * nx + m.m11() * ny + m.m21() * nz);
				qz += weight * (m.m02() * nx + m.m12() * ny + m.m22() * nz);
			}
			outPositions[p] = px;
			outPositions[p + 1] = py;
			outPositions[p + 2] = pz;
			float length = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
			float inverse = length > 1e-12f ? 1f / length : 0f;
			outNormals[p] = qx * inverse;
			outNormals[p + 1] = qy * inverse;
			outNormals[p + 2] = qz * inverse;
		}
	}

	public Vector3f min() {
		return new Vector3f(min);
	}

	public Vector3f max() {
		return new Vector3f(max);
	}

	/** The colour texture the extractor wrote next to the model, if the material has one. */
	public Optional<Path> baseColorImage() {
		return baseColorImage;
	}

	/**
	 * The texture the extractor wrote for how the surface reflects, if the material has one. As
	 * glTF has it: red is ambient occlusion, green roughness, blue metalness.
	 */
	public Optional<Path> surfaceImage() {
		return surfaceImage;
	}

	/**
	 * The game's name for the knife's material, e.g.
	 * {@code weapons/models/knife/knife_karambit/materials/weapon_knife_karambit.vmat}, if the
	 * extractor recorded it. Finishes are applied to this material.
	 */
	public Optional<String> material() {
		return material;
	}

	private static final class Builder {
		private static final int MAX_DEPTH = 64;

		private final GlbFile glb;
		private final FloatList positions = new FloatList();
		private final FloatList normals = new FloatList();
		private final FloatList uvs = new FloatList();
		private final List<int[]> indexRuns = new ArrayList<>();
		private final List<int[]> jointRuns = new ArrayList<>();
		private final List<float[]> weightRuns = new ArrayList<>();
		private int indexCount;
		private int vertexCount;
		private boolean anySkinned;
		private boolean anyUnskinned;
		private Optional<Path> baseColorImage = Optional.empty();
		private Optional<Path> surfaceImage = Optional.empty();
		private Optional<String> material = Optional.empty();

		Builder(GlbFile glb) {
			this.glb = glb;
		}

		void node(int index, Matrix4f parent, int depth) throws IOException {
			JsonArray nodes = glb.array("nodes");
			if (index < 0 || index >= nodes.size() || depth > MAX_DEPTH) {
				throw new IOException("Broken node hierarchy in " + glb.file());
			}
			JsonObject node = nodes.get(index).getAsJsonObject();
			Matrix4f world = new Matrix4f(parent).mul(GltfMath.localMatrix(node));

			if (node.has("mesh")) {
				mesh(node.get("mesh").getAsInt(), world);
			}
			if (node.has("children")) {
				for (JsonElement child : node.getAsJsonArray("children")) {
					node(child.getAsInt(), world, depth + 1);
				}
			}
		}

		private void mesh(int index, Matrix4f world) throws IOException {
			JsonArray meshes = glb.array("meshes");
			if (index < 0 || index >= meshes.size()) {
				throw new IOException("glTF mesh " + index + " does not exist in " + glb.file());
			}
			for (JsonElement element : meshes.get(index).getAsJsonObject().getAsJsonArray("primitives")) {
				JsonObject primitive = element.getAsJsonObject();
				// 4 = triangles, which is also the default.
				if (primitive.has("mode") && primitive.get("mode").getAsInt() != 4) {
					continue;
				}
				primitive(primitive, world);
			}
		}

		private void primitive(JsonObject primitive, Matrix4f world) throws IOException {
			JsonObject attributes = primitive.getAsJsonObject("attributes");
			if (attributes == null || !attributes.has("POSITION")) {
				return;
			}
			float[] position = glb.floats(attributes.get("POSITION").getAsInt());
			int count = position.length / 3;
			if (glb.components(attributes.get("POSITION").getAsInt()) != 3 || count == 0) {
				return;
			}

			float[] normal = attributes.has("NORMAL") ? glb.floats(attributes.get("NORMAL").getAsInt()) : null;
			float[] uv = attributes.has("TEXCOORD_0") ? glb.floats(attributes.get("TEXCOORD_0").getAsInt()) : null;
			int[] index;
			if (primitive.has("indices")) {
				index = glb.ints(primitive.get("indices").getAsInt());
			} else {
				index = new int[count];
				for (int i = 0; i < count; i++) {
					index[i] = i;
				}
			}
			index = java.util.Arrays.copyOf(index, index.length - index.length % 3);
			for (int value : index) {
				if (value < 0 || value >= count) {
					throw new IOException("glTF index out of range in " + glb.file());
				}
			}
			if ((normal != null && normal.length != count * 3) || (uv != null && uv.length != count * 2)) {
				throw new IOException("glTF vertex attributes differ in length in " + glb.file());
			}
			if (normal == null) {
				normal = flatNormals(position, index);
			}

			Matrix4f normalMatrix = new Matrix4f(world).invert().transpose();
			Vector3f scratch = new Vector3f();
			for (int i = 0; i < count; i++) {
				world.transformPosition(position[i * 3], position[i * 3 + 1], position[i * 3 + 2], scratch);
				positions.add(scratch.x, scratch.y, scratch.z);

				normalMatrix.transformDirection(normal[i * 3], normal[i * 3 + 1], normal[i * 3 + 2], scratch);
				if (scratch.lengthSquared() > 1e-12f) {
					scratch.normalize();
				} else {
					scratch.set(0f, 1f, 0f);
				}
				normals.add(scratch.x, scratch.y, scratch.z);

				if (uv != null) {
					uvs.add(uv[i * 2], uv[i * 2 + 1]);
				} else {
					uvs.add(0f, 0f);
				}
			}

			// A mirroring node transform turns the triangles inside out; turn them back.
			boolean flip = world.determinant3x3() < 0f;
			int[] shifted = new int[index.length];
			for (int i = 0; i < index.length; i += 3) {
				shifted[i] = index[i] + vertexCount;
				shifted[i + 1] = index[flip ? i + 2 : i + 1] + vertexCount;
				shifted[i + 2] = index[flip ? i + 1 : i + 2] + vertexCount;
			}
			indexRuns.add(shifted);
			indexCount += shifted.length;

			skin(attributes, count);
			vertexCount += count;

			if (baseColorImage.isEmpty() && primitive.has("material")) {
				baseColorImage = image(primitive.get("material").getAsInt(), "baseColorTexture");
			}
			if (surfaceImage.isEmpty() && primitive.has("material")) {
				surfaceImage = image(primitive.get("material").getAsInt(), "metallicRoughnessTexture");
			}
			if (material.isEmpty() && primitive.has("material")) {
				material = materialName(primitive.get("material").getAsInt());
			}
		}

		private void skin(JsonObject attributes, int count) throws IOException {
			int[] joint = new int[count * INFLUENCES];
			float[] weight = new float[count * INFLUENCES];
			if (attributes.has("JOINTS_0") && attributes.has("WEIGHTS_0")) {
				int[] sourceJoints = glb.ints(attributes.get("JOINTS_0").getAsInt());
				float[] sourceWeights = glb.floats(attributes.get("WEIGHTS_0").getAsInt());
				if (sourceJoints.length != count * INFLUENCES || sourceWeights.length != count * INFLUENCES) {
					throw new IOException("glTF skin attributes have an unexpected layout in " + glb.file());
				}
				for (int vertex = 0; vertex < count; vertex++) {
					float sum = 0f;
					for (int k = 0; k < INFLUENCES; k++) {
						sum += Math.max(sourceWeights[vertex * INFLUENCES + k], 0f);
					}
					for (int k = 0; k < INFLUENCES; k++) {
						int at = vertex * INFLUENCES + k;
						float value = Math.max(sourceWeights[at], 0f);
						// Vertices without any weight follow the first bone.
						weight[at] = sum > 0f ? value / sum : (k == 0 ? 1f : 0f);
						joint[at] = weight[at] > 0f ? Math.max(sourceJoints[at], 0) : 0;
					}
				}
				anySkinned = true;
			} else {
				for (int vertex = 0; vertex < count; vertex++) {
					weight[vertex * INFLUENCES] = 1f;
				}
				anyUnskinned = true;
			}
			jointRuns.add(joint);
			weightRuns.add(weight);
		}

		/** The file of one of the material's textures, e.g. {@code baseColorTexture}. */
		private Optional<Path> image(int materialIndex, String which) {
			try {
				JsonObject material = glb.array("materials").get(materialIndex).getAsJsonObject();
				JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");
				int texture = pbr.getAsJsonObject(which).get("index").getAsInt();
				int image = glb.array("textures").get(texture).getAsJsonObject().get("source").getAsInt();
				String uri = glb.array("images").get(image).getAsJsonObject().get("uri").getAsString();
				if (uri.startsWith("data:") || uri.contains("://")) {
					return Optional.empty();
				}
				Path folder = glb.file().toAbsolutePath().getParent();
				Path resolved = folder.resolve(java.net.URLDecoder.decode(uri, java.nio.charset.StandardCharsets.UTF_8))
						.normalize();
				// The texture has to sit next to the model; anything else is not ours to open.
				return resolved.startsWith(folder) ? Optional.of(resolved) : Optional.empty();
			} catch (RuntimeException e) {
				// Untextured, or a document shape this reader does not know.
				return Optional.empty();
			}
		}

		/** The extractor notes the original material in the glTF material's extras. */
		private Optional<String> materialName(int materialIndex) {
			try {
				String name = glb.array("materials").get(materialIndex).getAsJsonObject().getAsJsonObject("extras")
						.getAsJsonObject("vmat").get("Name").getAsString();
				return name.isBlank() ? Optional.empty() : Optional.of(name.replace('\\', '/'));
			} catch (RuntimeException e) {
				return Optional.empty();
			}
		}

		KnifeMesh build() throws IOException {
			if (vertexCount == 0 || indexCount == 0) {
				throw new IOException("No triangles in " + glb.file());
			}
			int[] indices = new int[indexCount];
			int at = 0;
			for (int[] run : indexRuns) {
				System.arraycopy(run, 0, indices, at, run.length);
				at += run.length;
			}

			int[] joints = new int[0];
			float[] weights = new float[0];
			if (anySkinned) {
				joints = new int[vertexCount * INFLUENCES];
				weights = new float[vertexCount * INFLUENCES];
				at = 0;
				for (int i = 0; i < jointRuns.size(); i++) {
					System.arraycopy(jointRuns.get(i), 0, joints, at, jointRuns.get(i).length);
					System.arraycopy(weightRuns.get(i), 0, weights, at, weightRuns.get(i).length);
					at += jointRuns.get(i).length;
				}
			}
			return new KnifeMesh(positions.toArray(), normals.toArray(), uvs.toArray(), indices, joints, weights,
					baseColorImage, surfaceImage, material);
		}

		private static float[] flatNormals(float[] position, int[] index) {
			float[] normal = new float[position.length];
			Vector3f a = new Vector3f();
			Vector3f b = new Vector3f();
			Vector3f c = new Vector3f();
			for (int i = 0; i < index.length; i += 3) {
				a.set(position, index[i] * 3);
				b.set(position, index[i + 1] * 3).sub(a);
				c.set(position, index[i + 2] * 3).sub(a);
				b.cross(c);
				for (int k = 0; k < 3; k++) {
					int vertex = index[i + k] * 3;
					normal[vertex] += b.x;
					normal[vertex + 1] += b.y;
					normal[vertex + 2] += b.z;
				}
			}
			return normal;
		}
	}

	/** A growable float array. */
	private static final class FloatList {
		private float[] values = new float[4096];
		private int size;

		void add(float a, float b) {
			ensure(2);
			values[size++] = a;
			values[size++] = b;
		}

		void add(float a, float b, float c) {
			ensure(3);
			values[size++] = a;
			values[size++] = b;
			values[size++] = c;
		}

		private void ensure(int extra) {
			if (size + extra > values.length) {
				values = java.util.Arrays.copyOf(values, Math.max(values.length * 2, size + extra));
			}
		}

		float[] toArray() {
			return java.util.Arrays.copyOf(values, size);
		}
	}
}
