package io.github.icxd.knives_as_swords.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.icxd.knives_as_swords.gltf.TestGlb;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnifeMeshTest {
	@TempDir
	Path temp;

	/** A single triangle in the XY plane, bound half to bone 1 and, for one corner, to bone 2. */
	private static TestGlb triangle(boolean skinned, boolean withNormals) {
		TestGlb glb = new TestGlb();
		JsonObject attributes = new JsonObject();
		attributes.addProperty("POSITION", glb.floats("VEC3", 0, 0, 0, 1, 0, 0, 0, 2, 0));
		attributes.addProperty("TEXCOORD_0", glb.floats("VEC2", 0, 0, 1, 0, 0, 1));
		if (withNormals) {
			attributes.addProperty("NORMAL", glb.floats("VEC3", 0, 0, 1, 0, 0, 1, 0, 0, 1));
		}
		if (skinned) {
			attributes.addProperty("JOINTS_0", glb.shorts("VEC4", 1, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0));
			attributes.addProperty("WEIGHTS_0", glb.floats("VEC4", 1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0));
		}
		JsonObject primitive = new JsonObject();
		primitive.add("attributes", attributes);
		primitive.addProperty("indices", glb.ints("SCALAR", 0, 1, 2));
		primitive.addProperty("material", 0);
		JsonArray primitives = new JsonArray();
		primitives.add(primitive);
		JsonObject mesh = new JsonObject();
		mesh.add("primitives", primitives);
		glb.add("meshes", mesh);

		JsonObject node = new JsonObject();
		node.addProperty("mesh", 0);
		glb.add("nodes", node);
		JsonObject scene = new JsonObject();
		scene.add("nodes", TestGlb.array(0));
		glb.add("scenes", scene);

		JsonObject vmat = new JsonObject();
		vmat.addProperty("Name", "things/materials/thing.vmat");
		JsonObject extras = new JsonObject();
		extras.add("vmat", vmat);
		JsonObject baseColor = new JsonObject();
		baseColor.addProperty("index", 0);
		JsonObject surface = new JsonObject();
		surface.addProperty("index", 1);
		JsonObject pbr = new JsonObject();
		pbr.add("baseColorTexture", baseColor);
		pbr.add("metallicRoughnessTexture", surface);
		JsonObject material = new JsonObject();
		material.add("extras", extras);
		material.add("pbrMetallicRoughness", pbr);
		glb.add("materials", material);
		JsonObject texture = new JsonObject();
		texture.addProperty("source", 0);
		glb.add("textures", texture);
		JsonObject image = new JsonObject();
		image.addProperty("uri", "thing_color.png");
		glb.add("images", image);
		JsonObject surfaceTexture = new JsonObject();
		surfaceTexture.addProperty("source", 1);
		glb.add("textures", surfaceTexture);
		JsonObject surfaceImage = new JsonObject();
		surfaceImage.addProperty("uri", "thing_orm.png");
		glb.add("images", surfaceImage);
		return glb;
	}

	@Test
	void readsGeometryBoundsAndMaterial() throws IOException {
		Path file = triangle(false, true).write(temp.resolve("model/thing.glb"));

		KnifeMesh mesh = KnifeMesh.load(file);

		assertEquals(3, mesh.vertexCount());
		assertEquals(1, mesh.triangleCount());
		assertArrayEquals(new float[] {0, 0, 0, 1, 0, 0, 0, 2, 0}, mesh.positions());
		assertArrayEquals(new int[] {0, 1, 2}, mesh.indices());
		assertEquals(0f, mesh.min().x);
		assertEquals(2f, mesh.max().y);
		assertFalse(mesh.skinned());
		assertEquals(0, mesh.singleJoint());
		assertEquals("things/materials/thing.vmat", mesh.material().orElseThrow());
		assertEquals(file.getParent().resolve("thing_color.png"), mesh.baseColorImage().orElseThrow());
		assertEquals(file.getParent().resolve("thing_orm.png"), mesh.surfaceImage().orElseThrow());
	}

	@Test
	void makesUpNormalsWhenTheFileHasNone() throws IOException {
		KnifeMesh mesh = KnifeMesh.load(triangle(false, false).write(temp.resolve("thing.glb")));

		// Counter-clockwise in the XY plane faces +Z.
		assertArrayEquals(new float[] {0, 0, 1, 0, 0, 1, 0, 0, 1}, mesh.normals(), 1e-6f);
	}

	@Test
	void appliesNodeTransforms() throws IOException {
		TestGlb glb = triangle(false, true);
		glb.json().getAsJsonArray("nodes").get(0).getAsJsonObject().add("translation", TestGlb.array(10f, 0f, 0f));

		KnifeMesh mesh = KnifeMesh.load(glb.write(temp.resolve("thing.glb")));

		assertArrayEquals(new float[] {10, 0, 0, 11, 0, 0, 10, 2, 0}, mesh.positions(), 1e-6f);
	}

	@Test
	void knowsWhichBonesMoveIt() throws IOException {
		KnifeMesh mesh = KnifeMesh.load(triangle(true, true).write(temp.resolve("thing.glb")));

		assertTrue(mesh.skinned());
		assertEquals(3, mesh.jointCount());
		// Two different bones are in use, so the knife does not move as one piece.
		assertEquals(-1, mesh.singleJoint());
	}

	@Test
	void movesVerticesWithTheirBones() throws IOException {
		KnifeMesh mesh = KnifeMesh.load(triangle(true, true).write(temp.resolve("thing.glb")));
		Matrix4f[] bones = {new Matrix4f(), new Matrix4f().translation(0, 0, 5), new Matrix4f().translation(0, 7, 0)};
		float[] positions = new float[9];
		float[] normals = new float[9];

		mesh.skin(bones, positions, normals);

		assertArrayEquals(new float[] {0, 0, 5, 1, 0, 5, 0, 9, 0}, positions, 1e-6f);
		assertArrayEquals(new float[] {0, 0, 1, 0, 0, 1, 0, 0, 1}, normals, 1e-6f);
	}

	@Test
	void rejectsIndicesPointingOutsideTheVertices() throws IOException {
		TestGlb glb = triangle(false, true);
		JsonObject primitive = glb.json().getAsJsonArray("meshes").get(0).getAsJsonObject()
				.getAsJsonArray("primitives").get(0).getAsJsonObject();
		primitive.addProperty("indices", glb.ints("SCALAR", 0, 1, 9));
		Path file = glb.write(temp.resolve("thing.glb"));

		assertThrows(IOException.class, () -> KnifeMesh.load(file));
	}

	@Test
	void rejectsFilesThatAreNotGlb() throws IOException {
		Path file = temp.resolve("thing.glb");
		Files.writeString(file, "not a model");

		assertThrows(IOException.class, () -> KnifeMesh.load(file));
	}
}
