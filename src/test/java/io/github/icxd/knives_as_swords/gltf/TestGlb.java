package io.github.icxd.knives_as_swords.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Builds small made-up binary glTF files for tests. No game data is involved. */
public final class TestGlb {
	public static final int FLOAT = 5126;
	public static final int UNSIGNED_SHORT = 5123;
	public static final int UNSIGNED_INT = 5125;

	private final JsonObject json = new JsonObject();
	private final ByteArrayOutputStream bin = new ByteArrayOutputStream();

	public TestGlb() {
		JsonObject asset = new JsonObject();
		asset.addProperty("version", "2.0");
		json.add("asset", asset);
	}

	/** The document, to add nodes, meshes and so on to. */
	public JsonObject json() {
		return json;
	}

	/** Appends {@code object} to the top-level array {@code name} and returns its index. */
	public int add(String name, JsonObject object) {
		if (!json.has(name)) {
			json.add(name, new JsonArray());
		}
		JsonArray array = json.getAsJsonArray(name);
		array.add(object);
		return array.size() - 1;
	}

	public int floats(String type, float... values) {
		ByteBuffer data = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
		for (float value : values) {
			data.putFloat(value);
		}
		return accessor(type, FLOAT, values.length / components(type), data.array());
	}

	public int shorts(String type, int... values) {
		ByteBuffer data = ByteBuffer.allocate(values.length * 2).order(ByteOrder.LITTLE_ENDIAN);
		for (int value : values) {
			data.putShort((short) value);
		}
		return accessor(type, UNSIGNED_SHORT, values.length / components(type), data.array());
	}

	public int ints(String type, int... values) {
		ByteBuffer data = ByteBuffer.allocate(values.length * 4).order(ByteOrder.LITTLE_ENDIAN);
		for (int value : values) {
			data.putInt(value);
		}
		return accessor(type, UNSIGNED_INT, values.length / components(type), data.array());
	}

	private int accessor(String type, int componentType, int count, byte[] data) {
		// Buffer views have to start on a multiple of four bytes.
		while (bin.size() % 4 != 0) {
			bin.write(0);
		}
		JsonObject view = new JsonObject();
		view.addProperty("buffer", 0);
		view.addProperty("byteOffset", bin.size());
		view.addProperty("byteLength", data.length);
		bin.writeBytes(data);

		JsonObject accessor = new JsonObject();
		accessor.addProperty("bufferView", add("bufferViews", view));
		accessor.addProperty("componentType", componentType);
		accessor.addProperty("count", count);
		accessor.addProperty("type", type);
		return add("accessors", accessor);
	}

	private static int components(String type) {
		return switch (type) {
			case "SCALAR" -> 1;
			case "VEC2" -> 2;
			case "VEC3" -> 3;
			case "VEC4" -> 4;
			case "MAT4" -> 16;
			default -> throw new IllegalArgumentException(type);
		};
	}

	public static JsonArray array(float... values) {
		JsonArray array = new JsonArray();
		for (float value : values) {
			array.add(value);
		}
		return array;
	}

	public static JsonArray array(int... values) {
		JsonArray array = new JsonArray();
		for (int value : values) {
			array.add(value);
		}
		return array;
	}

	public byte[] bytes() {
		JsonObject buffer = new JsonObject();
		buffer.addProperty("byteLength", bin.size());
		JsonArray buffers = new JsonArray();
		buffers.add(buffer);
		json.add("buffers", buffers);

		byte[] document = pad(json.toString().getBytes(StandardCharsets.UTF_8), (byte) ' ');
		byte[] binary = pad(bin.toByteArray(), (byte) 0);
		ByteBuffer out = ByteBuffer.allocate(12 + 8 + document.length + 8 + binary.length).order(ByteOrder.LITTLE_ENDIAN);
		out.putInt(0x46546C67).putInt(2).putInt(out.capacity());
		out.putInt(document.length).putInt(0x4E4F534A).put(document);
		out.putInt(binary.length).putInt(0x004E4942).put(binary);
		return out.array();
	}

	public Path write(Path file) throws IOException {
		Files.createDirectories(file.getParent());
		Files.write(file, bytes());
		return file;
	}

	private static byte[] pad(byte[] data, byte filler) {
		byte[] padded = new byte[(data.length + 3) / 4 * 4];
		System.arraycopy(data, 0, padded, 0, data.length);
		java.util.Arrays.fill(padded, data.length, padded.length, filler);
		return padded;
	}
}
