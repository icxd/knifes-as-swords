package io.github.icxd.knives_as_swords.gltf;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A binary glTF 2.0 file: its JSON document and its single embedded buffer.
 *
 * <p>Only what the extractor's exports use is supported: one binary chunk, accessors without
 * sparse storage.
 */
public final class GlbFile {
	private static final int MAGIC = 0x46546C67; // "glTF"
	private static final int CHUNK_JSON = 0x4E4F534A;
	private static final int CHUNK_BIN = 0x004E4942;

	private static final int BYTE = 5120;
	private static final int UNSIGNED_BYTE = 5121;
	private static final int SHORT = 5122;
	private static final int UNSIGNED_SHORT = 5123;
	private static final int UNSIGNED_INT = 5125;
	private static final int FLOAT = 5126;

	private final Path file;
	private final JsonObject json;
	private final ByteBuffer bin;

	private GlbFile(Path file, JsonObject json, ByteBuffer bin) {
		this.file = file;
		this.json = json;
		this.bin = bin;
	}

	public static GlbFile read(Path file) throws IOException {
		ByteBuffer data = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
		if (data.remaining() < 20 || data.getInt() != MAGIC) {
			throw new IOException("Not a binary glTF file: " + file);
		}
		int version = data.getInt();
		if (version != 2) {
			throw new IOException("Unsupported glTF version " + version + " in " + file);
		}
		data.getInt(); // total length; the chunk headers are what is actually followed

		JsonObject json = null;
		ByteBuffer bin = ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);
		while (data.remaining() >= 8) {
			int length = data.getInt();
			int type = data.getInt();
			if (length < 0 || length > data.remaining()) {
				throw new IOException("Corrupt glTF chunk in " + file);
			}
			if (type == CHUNK_JSON && json == null) {
				String text = new String(data.array(), data.position(), length, StandardCharsets.UTF_8);
				try {
					json = JsonParser.parseString(text).getAsJsonObject();
				} catch (JsonParseException | IllegalStateException e) {
					throw new IOException("Corrupt glTF document in " + file, e);
				}
			} else if (type == CHUNK_BIN && bin.capacity() == 0) {
				bin = data.slice(data.position(), length).order(ByteOrder.LITTLE_ENDIAN);
			}
			data.position(data.position() + length);
		}
		if (json == null) {
			throw new IOException("glTF file has no document: " + file);
		}
		return new GlbFile(file, json, bin);
	}

	public Path file() {
		return file;
	}

	public JsonObject json() {
		return json;
	}

	/** A top-level array such as {@code nodes}; empty when the document has none. */
	public JsonArray array(String name) {
		JsonElement element = json.get(name);
		return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
	}

	/** Number of elements (not components) in an accessor. */
	public int count(int accessor) throws IOException {
		return accessor(accessor).get("count").getAsInt();
	}

	/** Number of components per element of an accessor, e.g. 3 for {@code VEC3}. */
	public int components(int accessor) throws IOException {
		return componentCount(accessor(accessor).get("type").getAsString());
	}

	/**
	 * Reads an accessor as floats, {@code count * components} long. Integer data is converted,
	 * and scaled to 0..1 (or -1..1) when the accessor is marked as normalized.
	 */
	public float[] floats(int accessorIndex) throws IOException {
		View view = view(accessorIndex);
		float[] out = new float[view.count * view.components];
		for (int element = 0; element < view.count; element++) {
			int offset = view.offset + element * view.stride;
			for (int component = 0; component < view.components; component++) {
				int at = offset + component * view.componentSize;
				float value = switch (view.componentType) {
					case FLOAT -> bin.getFloat(at);
					case BYTE -> view.normalized ? Math.max(bin.get(at) / 127f, -1f) : bin.get(at);
					case UNSIGNED_BYTE -> view.normalized ? (bin.get(at) & 0xFF) / 255f : bin.get(at) & 0xFF;
					case SHORT -> view.normalized ? Math.max(bin.getShort(at) / 32767f, -1f) : bin.getShort(at);
					case UNSIGNED_SHORT -> view.normalized ? (bin.getShort(at) & 0xFFFF) / 65535f
							: bin.getShort(at) & 0xFFFF;
					case UNSIGNED_INT -> bin.getInt(at) & 0xFFFFFFFFL;
					default -> throw new IOException("Unsupported component type " + view.componentType);
				};
				out[element * view.components + component] = value;
			}
		}
		return out;
	}

	/** Reads an integer accessor (indices, joints), {@code count * components} long. */
	public int[] ints(int accessorIndex) throws IOException {
		View view = view(accessorIndex);
		int[] out = new int[view.count * view.components];
		for (int element = 0; element < view.count; element++) {
			int offset = view.offset + element * view.stride;
			for (int component = 0; component < view.components; component++) {
				int at = offset + component * view.componentSize;
				int value = switch (view.componentType) {
					case BYTE -> bin.get(at);
					case UNSIGNED_BYTE -> bin.get(at) & 0xFF;
					case SHORT -> bin.getShort(at);
					case UNSIGNED_SHORT -> bin.getShort(at) & 0xFFFF;
					case UNSIGNED_INT -> bin.getInt(at);
					default -> throw new IOException("Accessor " + accessorIndex + " does not hold integers");
				};
				out[element * view.components + component] = value;
			}
		}
		return out;
	}

	private record View(int offset, int stride, int count, int components, int componentType, int componentSize,
			boolean normalized) {
	}

	private JsonObject accessor(int index) throws IOException {
		JsonArray accessors = array("accessors");
		if (index < 0 || index >= accessors.size()) {
			throw new IOException("glTF accessor " + index + " does not exist in " + file);
		}
		return accessors.get(index).getAsJsonObject();
	}

	private View view(int accessorIndex) throws IOException {
		JsonObject accessor = accessor(accessorIndex);
		if (accessor.has("sparse") || !accessor.has("bufferView")) {
			throw new IOException("Unsupported glTF accessor " + accessorIndex + " in " + file);
		}
		JsonArray bufferViews = array("bufferViews");
		int bufferViewIndex = accessor.get("bufferView").getAsInt();
		if (bufferViewIndex < 0 || bufferViewIndex >= bufferViews.size()) {
			throw new IOException("glTF buffer view " + bufferViewIndex + " does not exist in " + file);
		}
		JsonObject bufferView = bufferViews.get(bufferViewIndex).getAsJsonObject();

		int componentType = accessor.get("componentType").getAsInt();
		int componentSize = switch (componentType) {
			case BYTE, UNSIGNED_BYTE -> 1;
			case SHORT, UNSIGNED_SHORT -> 2;
			case UNSIGNED_INT, FLOAT -> 4;
			default -> throw new IOException("Unsupported component type " + componentType + " in " + file);
		};
		int components = componentCount(accessor.get("type").getAsString());
		int count = accessor.get("count").getAsInt();
		int elementSize = componentSize * components;
		int stride = bufferView.has("byteStride") ? bufferView.get("byteStride").getAsInt() : elementSize;
		long offset = (long) intOr(bufferView, "byteOffset", 0) + intOr(accessor, "byteOffset", 0);

		if (count < 0 || stride < elementSize || offset < 0
				|| (count > 0 && offset + (long) (count - 1) * stride + elementSize > bin.capacity())) {
			throw new IOException("glTF accessor " + accessorIndex + " points outside the buffer in " + file);
		}
		boolean normalized = accessor.has("normalized") && accessor.get("normalized").getAsBoolean();
		return new View((int) offset, stride, count, components, componentType, componentSize, normalized);
	}

	private static int componentCount(String type) throws IOException {
		return switch (type) {
			case "SCALAR" -> 1;
			case "VEC2" -> 2;
			case "VEC3" -> 3;
			case "VEC4" -> 4;
			case "MAT4" -> 16;
			default -> throw new IOException("Unsupported glTF accessor type " + type);
		};
	}

	static int intOr(JsonObject object, String name, int fallback) {
		JsonElement element = object.get(name);
		return element == null || element.isJsonNull() ? fallback : element.getAsInt();
	}
}
