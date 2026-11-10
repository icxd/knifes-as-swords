package io.github.icxd.knives_as_swords.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Small helpers for reading glTF node transforms. */
final class GltfMath {
	private GltfMath() {
	}

	/** A node's transform relative to its parent, from either its matrix or its TRS properties. */
	static Matrix4f localMatrix(JsonObject node) {
		if (node.has("matrix")) {
			JsonArray values = node.getAsJsonArray("matrix");
			if (values.size() == 16) {
				float[] m = new float[16];
				for (int i = 0; i < 16; i++) {
					m[i] = values.get(i).getAsFloat();
				}
				// glTF stores matrices column by column, as JOML does.
				return new Matrix4f().set(m);
			}
		}
		return new Matrix4f().translationRotateScale(translation(node), rotation(node), scale(node));
	}

	static Vector3f translation(JsonObject node) {
		return vector(node, "translation", 0f);
	}

	static Vector3f scale(JsonObject node) {
		return vector(node, "scale", 1f);
	}

	static Quaternionf rotation(JsonObject node) {
		if (node.has("rotation")) {
			JsonArray values = node.getAsJsonArray("rotation");
			if (values.size() == 4) {
				return new Quaternionf(values.get(0).getAsFloat(), values.get(1).getAsFloat(),
						values.get(2).getAsFloat(), values.get(3).getAsFloat()).normalize();
			}
		}
		return new Quaternionf();
	}

	private static Vector3f vector(JsonObject node, String name, float fallback) {
		if (node.has(name)) {
			JsonArray values = node.getAsJsonArray(name);
			if (values.size() == 3) {
				return new Vector3f(values.get(0).getAsFloat(), values.get(1).getAsFloat(), values.get(2).getAsFloat());
			}
		}
		return new Vector3f(fallback);
	}
}
