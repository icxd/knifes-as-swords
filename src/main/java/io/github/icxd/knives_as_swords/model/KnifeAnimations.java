package io.github.icxd.knives_as_swords.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.icxd.knives_as_swords.gltf.GlbFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A knife's first-person animation clips, baked down to what drawing needs: for every frame, one
 * rigid transform per bone of the knife that takes a vertex of the model straight to where it is
 * in front of the camera.
 *
 * <p>The space those transforms lead to is the game's view space in glTF axes: the eye at the
 * origin looking along +Z, +X to the left, +Y up, in metres.
 */
public final class KnifeAnimations {
	/** Frames per second the clips are stored at (and were authored at). */
	public static final int FPS = 30;
	/** Floats per bone and frame: translation, then rotation as a quaternion. */
	private static final int STRIDE = 7;
	/** The bone of the first-person arms that the knife is attached to. */
	private static final String ATTACH_BONE = "wpn";
	/** Longest clip accepted, as a guard against broken files. */
	private static final float MAX_SECONDS = 120f;

	private final Map<String, Clip> clips;
	private final int jointCount;

	private KnifeAnimations(Map<String, Clip> clips, int jointCount) {
		this.clips = clips;
		this.jointCount = jointCount;
	}

	/**
	 * Reads every clip in a folder of converted clips.
	 *
	 * @param suffix what follows the clip's name in each file name, e.g. {@code _karambit}
	 * @param boneNames the model's bones, in the order its vertices refer to them
	 * @param jointCount how many of those bones the model's vertices actually use
	 */
	public static KnifeAnimations load(Path folder, String suffix, List<String> boneNames, int jointCount)
			throws IOException {
		List<Path> files;
		try (Stream<Path> list = Files.list(folder)) {
			files = list.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".glb"))
					.sorted().toList();
		}

		int joints = Math.max(jointCount, 1);
		Map<String, Clip> clips = new TreeMap<>();
		IOException firstFailure = null;
		for (Path file : files) {
			String name = file.getFileName().toString();
			name = name.substring(0, name.length() - ".glb".length());
			if (name.endsWith(suffix)) {
				name = name.substring(0, name.length() - suffix.length());
			}
			try {
				clips.put(name, readClip(name, GlbFile.read(file), boneNames, joints));
			} catch (IOException | RuntimeException e) {
				// One unreadable clip should not cost the knife all its other animations.
				if (firstFailure == null) {
					firstFailure = e instanceof IOException io ? io : new IOException(file + ": " + e, e);
				}
			}
		}
		if (clips.isEmpty()) {
			throw firstFailure != null ? firstFailure : new IOException("No animation clips in " + folder);
		}
		return new KnifeAnimations(clips, joints);
	}

	/** Number of bones in every {@link Pose} of these clips. */
	public int jointCount() {
		return jointCount;
	}

	public Optional<Clip> clip(String name) {
		return Optional.ofNullable(clips.get(name));
	}

	/** Clips whose name is {@code prefix} followed by nothing or by digits only, e.g. {@code lookat01}. */
	public List<Clip> numbered(String prefix) {
		List<Clip> matches = new ArrayList<>();
		for (Clip clip : clips.values()) {
			if (clip.name().startsWith(prefix) && clip.name().substring(prefix.length()).chars()
					.allMatch(Character::isDigit)) {
				matches.add(clip);
			}
		}
		return matches;
	}

	public List<String> clipNames() {
		return List.copyOf(clips.keySet());
	}

	/** One animation, sampled at {@link #FPS}. */
	public static final class Clip {
		private final String name;
		private final int frames;
		private final int joints;
		private final float[] data;

		private Clip(String name, int frames, int joints, float[] data) {
			this.name = name;
			this.frames = frames;
			this.joints = joints;
			this.data = data;
		}

		public String name() {
			return name;
		}

		/** Length in seconds. */
		public float duration() {
			return (frames - 1) / (float) FPS;
		}

		/** Writes the pose at {@code seconds}, clamped to the clip's length. */
		public void sample(float seconds, Pose out) {
			float position = Math.clamp(seconds * FPS, 0f, frames - 1f);
			int first = (int) position;
			int second = Math.min(first + 1, frames - 1);
			float alpha = position - first;
			int a = first * joints * STRIDE;
			int b = second * joints * STRIDE;
			for (int i = 0; i < joints * STRIDE; i += STRIDE) {
				mix(data, a + i, data, b + i, alpha, out.values, i);
			}
		}
	}

	/** The transforms of all bones at one moment. */
	public static final class Pose {
		private final float[] values;

		public Pose(int jointCount) {
			this.values = new float[jointCount * STRIDE];
			for (int i = 0; i < values.length; i += STRIDE) {
				values[i + 6] = 1f;
			}
		}

		public int jointCount() {
			return values.length / STRIDE;
		}

		public void set(Pose other) {
			System.arraycopy(other.values, 0, values, 0, values.length);
		}

		/** Moves this pose towards {@code target}: 0 leaves it alone, 1 makes it equal. */
		public void blendTowards(Pose target, float amount) {
			for (int i = 0; i < values.length; i += STRIDE) {
				mix(values, i, target.values, i, amount, values, i);
			}
		}

		/** The transform taking a model vertex bound to {@code joint} into view space. */
		public Matrix4f matrix(int joint, Matrix4f out) {
			int i = Math.clamp(joint, 0, jointCount() - 1) * STRIDE;
			return out.translationRotate(values[i], values[i + 1], values[i + 2], values[i + 3], values[i + 4],
					values[i + 5], values[i + 6]);
		}
	}

	/** Interpolates one bone transform; the rotation takes the short way round. */
	private static void mix(float[] a, int ai, float[] b, int bi, float t, float[] out, int oi) {
		for (int k = 0; k < 3; k++) {
			out[oi + k] = a[ai + k] + (b[bi + k] - a[ai + k]) * t;
		}
		float dot = a[ai + 3] * b[bi + 3] + a[ai + 4] * b[bi + 4] + a[ai + 5] * b[bi + 5] + a[ai + 6] * b[bi + 6];
		float sign = dot < 0f ? -1f : 1f;
		float x = a[ai + 3] + (sign * b[bi + 3] - a[ai + 3]) * t;
		float y = a[ai + 4] + (sign * b[bi + 4] - a[ai + 4]) * t;
		float z = a[ai + 5] + (sign * b[bi + 5] - a[ai + 5]) * t;
		float w = a[ai + 6] + (sign * b[bi + 6] - a[ai + 6]) * t;
		float length = (float) Math.sqrt(x * x + y * y + z * z + w * w);
		if (length < 1e-8f) {
			x = y = z = 0f;
			w = 1f;
			length = 1f;
		}
		out[oi + 3] = x / length;
		out[oi + 4] = y / length;
		out[oi + 5] = z / length;
		out[oi + 6] = w / length;
	}

	// ---------------------------------------------------------------------------------------
	// Reading

	/** The keyframes of one property of one node. */
	private record Track(float[] times, float[] values, int components) {
		void sample(float time, float[] out) {
			int last = times.length - 1;
			if (last <= 0 || time <= times[0]) {
				System.arraycopy(values, 0, out, 0, components);
				return;
			}
			if (time >= times[last]) {
				System.arraycopy(values, last * components, out, 0, components);
				return;
			}
			int low = 0;
			int high = last;
			while (high - low > 1) {
				int middle = (low + high) >>> 1;
				if (times[middle] <= time) {
					low = middle;
				} else {
					high = middle;
				}
			}
			float span = times[high] - times[low];
			float alpha = span > 0f ? (time - times[low]) / span : 0f;
			int a = low * components;
			int b = high * components;
			if (components == 4) {
				float dot = 0f;
				for (int k = 0; k < 4; k++) {
					dot += values[a + k] * values[b + k];
				}
				float sign = dot < 0f ? -1f : 1f;
				for (int k = 0; k < 4; k++) {
					out[k] = values[a + k] + (sign * values[b + k] - values[a + k]) * alpha;
				}
			} else {
				for (int k = 0; k < components; k++) {
					out[k] = values[a + k] + (values[b + k] - values[a + k]) * alpha;
				}
			}
		}
	}

	/** A node of the file with its rest transform and, where animated, its tracks. */
	private static final class Node {
		String name = "";
		int parent = -1;
		final Vector3f translation = new Vector3f();
		final Quaternionf rotation = new Quaternionf();
		final Vector3f scale = new Vector3f(1f);
		Track translationTrack;
		Track rotationTrack;
		Track scaleTrack;

		Matrix4f local(float time, float[] scratch, Matrix4f out) {
			float tx = translation.x, ty = translation.y, tz = translation.z;
			float qx = rotation.x, qy = rotation.y, qz = rotation.z, qw = rotation.w;
			float sx = scale.x, sy = scale.y, sz = scale.z;
			if (translationTrack != null) {
				translationTrack.sample(time, scratch);
				tx = scratch[0];
				ty = scratch[1];
				tz = scratch[2];
			}
			if (rotationTrack != null) {
				rotationTrack.sample(time, scratch);
				float length = (float) Math.sqrt(scratch[0] * scratch[0] + scratch[1] * scratch[1]
						+ scratch[2] * scratch[2] + scratch[3] * scratch[3]);
				if (length > 1e-8f) {
					qx = scratch[0] / length;
					qy = scratch[1] / length;
					qz = scratch[2] / length;
					qw = scratch[3] / length;
				}
			}
			if (scaleTrack != null) {
				scaleTrack.sample(time, scratch);
				sx = scratch[0];
				sy = scratch[1];
				sz = scratch[2];
			}
			return out.translationRotateScale(tx, ty, tz, qx, qy, qz, qw, sx, sy, sz);
		}
	}

	private static Clip readClip(String name, GlbFile glb, List<String> boneNames, int joints) throws IOException {
		JsonArray jsonNodes = glb.array("nodes");
		Node[] nodes = new Node[jsonNodes.size()];
		for (int i = 0; i < nodes.length; i++) {
			nodes[i] = new Node();
		}
		for (int i = 0; i < nodes.length; i++) {
			JsonObject json = jsonNodes.get(i).getAsJsonObject();
			Node node = nodes[i];
			node.name = json.has("name") ? json.get("name").getAsString() : "";
			node.translation.set(GltfMath.translation(json));
			node.rotation.set(GltfMath.rotation(json));
			node.scale.set(GltfMath.scale(json));
			if (json.has("children")) {
				for (JsonElement child : json.getAsJsonArray("children")) {
					int index = child.getAsInt();
					if (index >= 0 && index < nodes.length && index != i) {
						nodes[index].parent = i;
					}
				}
			}
		}

		// The knife's own skeleton is the skin whose bones carry the model's bone names.
		JsonArray skins = glb.array("skins");
		int[] skinJoints = null;
		float[] inverseBind = null;
		int bestMatches = 0;
		for (JsonElement element : skins) {
			JsonObject skin = element.getAsJsonObject();
			if (!skin.has("joints") || !skin.has("inverseBindMatrices")) {
				continue;
			}
			JsonArray jointArray = skin.getAsJsonArray("joints");
			int[] candidate = new int[jointArray.size()];
			int matches = 0;
			for (int i = 0; i < candidate.length; i++) {
				candidate[i] = jointArray.get(i).getAsInt();
				if (candidate[i] < 0 || candidate[i] >= nodes.length) {
					throw new IOException("Broken skin in " + glb.file());
				}
				if (boneNames.contains(nodes[candidate[i]].name)) {
					matches++;
				}
			}
			if (matches > bestMatches) {
				float[] matrices = glb.floats(skin.get("inverseBindMatrices").getAsInt());
				if (matrices.length == candidate.length * 16) {
					bestMatches = matches;
					skinJoints = candidate;
					inverseBind = matrices;
				}
			}
		}
		if (skinJoints == null) {
			throw new IOException("No skeleton for this knife in " + glb.file());
		}

		Map<String, Integer> skinIndexByName = new HashMap<>();
		boolean[] inSkin = new boolean[nodes.length];
		for (int i = 0; i < skinJoints.length; i++) {
			skinIndexByName.putIfAbsent(nodes[skinJoints[i]].name, i);
			inSkin[skinJoints[i]] = true;
		}
		int attach = -1;
		for (int i = 0; i < nodes.length; i++) {
			if (!inSkin[i] && nodes[i].name.equals(ATTACH_BONE)) {
				attach = i;
				break;
			}
		}
		if (attach < 0) {
			throw new IOException("No '" + ATTACH_BONE + "' bone to attach the knife to in " + glb.file());
		}

		float duration = readTracks(glb, nodes);
		int frames = Math.round(duration * FPS) + 1;

		// Which skeleton bone each of the model's bones is; unknown ones follow the knife's main bone.
		int fallback = skinIndexByName.getOrDefault("weapon_offset", 0);
		int[] skinIndex = new int[joints];
		for (int joint = 0; joint < joints; joint++) {
			String boneName = joint < boneNames.size() ? boneNames.get(joint) : "";
			skinIndex[joint] = skinIndexByName.getOrDefault(boneName, fallback);
		}

		float[] data = new float[frames * joints * STRIDE];
		float[] scratch = new float[4];
		Matrix4f attachWorld = new Matrix4f();
		Matrix4f local = new Matrix4f();
		Matrix4f chain = new Matrix4f();
		Matrix4f bind = new Matrix4f();
		Vector3f translation = new Vector3f();
		Quaternionf rotation = new Quaternionf();
		for (int frame = 0; frame < frames; frame++) {
			float time = frame / (float) FPS;

			// Where the attachment bone is in view space: its own transform under all its parents'.
			attachWorld.identity();
			int depth = 0;
			for (int node = attach; node >= 0; node = nodes[node].parent) {
				attachWorld.mulLocal(nodes[node].local(time, scratch, local));
				if (++depth > nodes.length) {
					throw new IOException("Broken node hierarchy in " + glb.file());
				}
			}

			for (int joint = 0; joint < joints; joint++) {
				// The knife's root bone sits on the attachment bone; every bone below it moves relative to that.
				chain.identity();
				depth = 0;
				for (int node = skinJoints[skinIndex[joint]]; node >= 0 && inSkin[node]
						&& nodes[node].parent >= 0 && inSkin[nodes[node].parent]; node = nodes[node].parent) {
					chain.mulLocal(nodes[node].local(time, scratch, local));
					if (++depth > nodes.length) {
						throw new IOException("Broken node hierarchy in " + glb.file());
					}
				}
				bind.set(inverseBind, skinIndex[joint] * 16);
				local.set(attachWorld).mul(chain).mul(bind);

				local.getTranslation(translation);
				local.getUnnormalizedRotation(rotation).normalize();
				int at = (frame * joints + joint) * STRIDE;
				data[at] = translation.x;
				data[at + 1] = translation.y;
				data[at + 2] = translation.z;
				data[at + 3] = rotation.x;
				data[at + 4] = rotation.y;
				data[at + 5] = rotation.z;
				data[at + 6] = rotation.w;
			}
		}

		// Neighbouring frames should use the same sign for a rotation, so blending never goes the long way.
		for (int frame = 1; frame < frames; frame++) {
			for (int joint = 0; joint < joints; joint++) {
				int now = (frame * joints + joint) * STRIDE + 3;
				int before = ((frame - 1) * joints + joint) * STRIDE + 3;
				float dot = 0f;
				for (int k = 0; k < 4; k++) {
					dot += data[now + k] * data[before + k];
				}
				if (dot < 0f) {
					for (int k = 0; k < 4; k++) {
						data[now + k] = -data[now + k];
					}
				}
			}
		}
		return new Clip(name, frames, joints, data);
	}

	/** Attaches the file's animation tracks to their nodes; returns the animation's length in seconds. */
	private static float readTracks(GlbFile glb, Node[] nodes) throws IOException {
		JsonArray animations = glb.array("animations");
		if (animations.isEmpty()) {
			throw new IOException("No animation in " + glb.file());
		}
		JsonObject animation = animations.get(0).getAsJsonObject();
		JsonArray samplers = animation.getAsJsonArray("samplers");
		Map<Integer, float[]> timesByAccessor = new HashMap<>();
		float duration = 0f;

		for (JsonElement element : animation.getAsJsonArray("channels")) {
			JsonObject channel = element.getAsJsonObject();
			JsonObject target = channel.getAsJsonObject("target");
			if (target == null || !target.has("node") || !target.has("path")) {
				continue;
			}
			int nodeIndex = target.get("node").getAsInt();
			int samplerIndex = channel.get("sampler").getAsInt();
			if (nodeIndex < 0 || nodeIndex >= nodes.length || samplerIndex < 0 || samplerIndex >= samplers.size()) {
				continue;
			}
			String path = target.get("path").getAsString();
			int components = switch (path) {
				case "translation", "scale" -> 3;
				case "rotation" -> 4;
				default -> 0;
			};
			if (components == 0) {
				continue;
			}

			JsonObject sampler = samplers.get(samplerIndex).getAsJsonObject();
			int input = sampler.get("input").getAsInt();
			float[] times = timesByAccessor.get(input);
			if (times == null) {
				times = glb.floats(input);
				timesByAccessor.put(input, times);
			}
			float[] values = glb.floats(sampler.get("output").getAsInt());
			if (times.length == 0 || values.length != times.length * components) {
				// Cubic-spline samplers carry tangents too; the extractor does not write those.
				throw new IOException("Unsupported animation sampler in " + glb.file());
			}
			duration = Math.max(duration, times[times.length - 1]);

			Track track = new Track(times, values, components);
			switch (path) {
				case "translation" -> nodes[nodeIndex].translationTrack = track;
				case "rotation" -> nodes[nodeIndex].rotationTrack = track;
				default -> nodes[nodeIndex].scaleTrack = track;
			}
		}
		if (!(duration >= 0f) || duration > MAX_SECONDS) {
			throw new IOException("Implausible animation length in " + glb.file());
		}
		return duration;
	}
}
