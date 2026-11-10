package io.github.icxd.knives_as_swords.cs2;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Writes small synthetic VPK directory files for tests. No game data is involved. */
final class TestVpk {
	private final Map<String, byte[]> preloadByPath = new LinkedHashMap<>();
	private int version = 2;

	TestVpk version(int version) {
		this.version = version;
		return this;
	}

	/** Adds an entry whose (dummy) data lives in chunk 0. */
	TestVpk file(String path) {
		return file(path, new byte[0]);
	}

	/** Adds an entry with these preload bytes stored inside the index. */
	TestVpk file(String path, byte[] preload) {
		preloadByPath.put(path, preload);
		return this;
	}

	void write(Path target) throws IOException {
		Files.createDirectories(target.getParent());
		Files.write(target, bytes());
	}

	byte[] bytes() {
		// extension -> directory -> name -> preload
		Map<String, Map<String, Map<String, byte[]>>> tree = new TreeMap<>();
		preloadByPath.forEach((path, preload) -> {
			int slash = path.lastIndexOf('/');
			String directory = slash < 0 ? " " : path.substring(0, slash);
			String file = path.substring(slash + 1);
			int dot = file.lastIndexOf('.');
			String name = dot < 0 ? file : file.substring(0, dot);
			String extension = dot < 0 ? " " : file.substring(dot + 1);
			tree.computeIfAbsent(extension, key -> new TreeMap<>())
					.computeIfAbsent(directory, key -> new TreeMap<>())
					.put(name.isEmpty() ? " " : name, preload);
		});

		ByteArrayOutputStream body = new ByteArrayOutputStream();
		int offset = 0;
		for (var byExtension : tree.entrySet()) {
			writeString(body, byExtension.getKey());
			for (var byDirectory : byExtension.getValue().entrySet()) {
				writeString(body, byDirectory.getKey());
				for (var file : byDirectory.getValue().entrySet()) {
					writeString(body, file.getKey());
					byte[] preload = file.getValue();
					ByteBuffer entry = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN);
					entry.putInt(0xC0FFEE00 + offset); // crc
					entry.putShort((short) preload.length);
					entry.putShort((short) 0); // archive index
					entry.putInt(offset);
					entry.putInt(100); // length
					entry.putShort((short) 0xFFFF);
					body.writeBytes(entry.array());
					body.writeBytes(preload);
					offset += 100;
				}
				body.write(0);
			}
			body.write(0);
		}
		body.write(0);

		int headerSize = version == 1 ? 12 : 28;
		ByteBuffer header = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN);
		header.putInt(0x55AA1234);
		header.putInt(version);
		header.putInt(body.size());

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.writeBytes(header.array());
		out.writeBytes(body.toByteArray());
		return out.toByteArray();
	}

	private static void writeString(ByteArrayOutputStream out, String value) {
		out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
		out.write(0);
	}
}
