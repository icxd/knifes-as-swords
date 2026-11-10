package io.github.icxd.knives_as_swords.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.icxd.knives_as_swords.gltf.TestGlb;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnifeAnimationsTest {
	private static final List<String> BONES = List.of("weapon", "weapon_offset", "blade");

	@TempDir
	Path temp;

	private static JsonObject node(String name, int... children) {
		JsonObject node = new JsonObject();
		node.addProperty("name", name);
		if (children.length > 0) {
			node.add("children", TestGlb.array(children));
		}
		return node;
	}

	/**
	 * A one-second clip laid out like the extractor's: the arms' skeleton with the bone the knife
	 * hangs on, and the knife's own skeleton next to it. The attachment bone slides from x=1 to
	 * x=3 while the blade, hinged one unit up the knife, swings a quarter turn about Z.
	 */
	private static TestGlb clip() {
		TestGlb glb = new TestGlb();
		glb.add("nodes", node("arms", 1));
		glb.add("nodes", node("root_motion", 2));
		JsonObject attach = node("wpn");
		attach.add("translation", TestGlb.array(1f, 0f, 0f));
		glb.add("nodes", attach);
		glb.add("nodes", node("knife skeleton", 4));
		glb.add("nodes", node("weapon", 5));
		glb.add("nodes", node("weapon_offset", 6));
		JsonObject blade = node("blade");
		blade.add("translation", TestGlb.array(0f, 1f, 0f));
		glb.add("nodes", blade);
		JsonObject scene = new JsonObject();
		scene.add("nodes", TestGlb.array(0, 3));
		glb.add("scenes", scene);

		JsonObject skin = new JsonObject();
		skin.add("joints", TestGlb.array(4, 5, 6));
		skin.addProperty("inverseBindMatrices", glb.floats("MAT4",
				1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1,
				1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1,
				1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, -1, 0, 1));
		glb.add("skins", skin);

		int times = glb.floats("SCALAR", 0f, 1f);
		float quarter = (float) Math.sqrt(0.5);
		JsonArray samplers = new JsonArray();
		JsonArray channels = new JsonArray();
		addChannel(samplers, channels, times, glb.floats("VEC3", 1, 0, 0, 3, 0, 0), 2, "translation");
		addChannel(samplers, channels, times, glb.floats("VEC4", 0, 0, 0, 1, 0, 0, quarter, quarter), 6, "rotation");
		JsonObject animation = new JsonObject();
		animation.add("samplers", samplers);
		animation.add("channels", channels);
		glb.add("animations", animation);
		return glb;
	}

	private static void addChannel(JsonArray samplers, JsonArray channels, int input, int output, int node,
			String path) {
		JsonObject sampler = new JsonObject();
		sampler.addProperty("input", input);
		sampler.addProperty("output", output);
		samplers.add(sampler);
		JsonObject target = new JsonObject();
		target.addProperty("node", node);
		target.addProperty("path", path);
		JsonObject channel = new JsonObject();
		channel.addProperty("sampler", samplers.size() - 1);
		channel.add("target", target);
		channels.add(channel);
	}

	private static Vector3f pose(KnifeAnimations.Clip clip, float seconds, int joint, float x, float y, float z) {
		KnifeAnimations.Pose pose = new KnifeAnimations.Pose(BONES.size());
		clip.sample(seconds, pose);
		return pose.matrix(joint, new Matrix4f()).transformPosition(new Vector3f(x, y, z));
	}

	private static void assertNear(float x, float y, float z, Vector3f actual) {
		assertTrue(actual.distance(x, y, z) < 1e-4f, () -> "expected (" + x + ", " + y + ", " + z + ") but got " + actual);
	}

	@Test
	void namesClipsAfterTheirFilesWithoutTheKnifeSuffix() throws IOException {
		clip().write(temp.resolve("draw_test.glb"));
		clip().write(temp.resolve("lookat01_test.glb"));
		clip().write(temp.resolve("lookat01_draw_test.glb"));

		KnifeAnimations animations = KnifeAnimations.load(temp, "_test", BONES, BONES.size());

		assertEquals(List.of("draw", "lookat01", "lookat01_draw"), animations.clipNames());
		assertEquals(1f, animations.clip("draw").orElseThrow().duration(), 1e-6f);
		// "lookat" followed by digits only: the plain inspect animations, not their special variants.
		assertEquals(List.of("lookat01"),
				animations.numbered("lookat").stream().map(KnifeAnimations.Clip::name).toList());
	}

	@Test
	void theKnifeFollowsTheBoneItHangsOn() throws IOException {
		clip().write(temp.resolve("draw_test.glb"));
		KnifeAnimations.Clip clip = KnifeAnimations.load(temp, "_test", BONES, BONES.size()).clip("draw").orElseThrow();

		assertNear(1f, 0f, 0f, pose(clip, 0f, 1, 0f, 0f, 0f));
		assertNear(2f, 0f, 0f, pose(clip, 0.5f, 1, 0f, 0f, 0f));
		assertNear(3f, 0.5f, 0f, pose(clip, 1f, 1, 0f, 0.5f, 0f));
		// Sampling past either end holds the first or last pose.
		assertNear(3f, 0f, 0f, pose(clip, 5f, 0, 0f, 0f, 0f));
	}

	@Test
	void partsMoveAboutTheirOwnHinge() throws IOException {
		clip().write(temp.resolve("draw_test.glb"));
		KnifeAnimations.Clip clip = KnifeAnimations.load(temp, "_test", BONES, BONES.size()).clip("draw").orElseThrow();

		// The hinge itself only follows the knife.
		assertNear(3f, 1f, 0f, pose(clip, 1f, 2, 0f, 1f, 0f));
		// A point one unit beyond the hinge has swung from above it to its left.
		assertNear(1f, 2f, 0f, pose(clip, 0f, 2, 0f, 2f, 0f));
		assertNear(2f, 1f, 0f, pose(clip, 1f, 2, 0f, 2f, 0f));
	}

	@Test
	void blendingMeetsHalfway() throws IOException {
		clip().write(temp.resolve("draw_test.glb"));
		KnifeAnimations.Clip clip = KnifeAnimations.load(temp, "_test", BONES, BONES.size()).clip("draw").orElseThrow();
		KnifeAnimations.Pose start = new KnifeAnimations.Pose(BONES.size());
		KnifeAnimations.Pose end = new KnifeAnimations.Pose(BONES.size());
		clip.sample(0f, start);
		clip.sample(1f, end);

		start.blendTowards(end, 0.5f);

		assertNear(2f, 0f, 0f, start.matrix(1, new Matrix4f()).transformPosition(new Vector3f()));
	}

	@Test
	void aFolderWithoutClipsIsAnError() throws IOException {
		Files.createDirectories(temp.resolve("empty"));

		assertThrows(IOException.class, () -> KnifeAnimations.load(temp.resolve("empty"), "_test", BONES, 3));
	}

	@Test
	void oneBrokenClipDoesNotCostTheOthers() throws IOException {
		clip().write(temp.resolve("draw_test.glb"));
		Files.writeString(temp.resolve("idle1_test.glb"), "not a clip");

		assertEquals(List.of("draw"), KnifeAnimations.load(temp, "_test", BONES, BONES.size()).clipNames());
	}
}
