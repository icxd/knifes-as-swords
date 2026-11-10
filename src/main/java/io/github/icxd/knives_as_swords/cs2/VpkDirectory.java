package io.github.icxd.knives_as_swords.cs2;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Reads the file index of a Valve Pak directory file ({@code *_dir.vpk}, format v1 or v2).
 *
 * <p>Only the index is read; file contents stay in the game's own archives. Conversion of the
 * actual resources is left to the external extractor.
 */
public final class VpkDirectory {
	private static final int SIGNATURE = 0x55AA1234;
	private static final int ENTRY_TERMINATOR = 0xFFFF;
	private static final String DIRECTORY_SUFFIX = "_dir.vpk";
	/** Sanity cap; CS2's index is a few tens of megabytes at most. */
	private static final long MAX_TREE_SIZE = 512L * 1024 * 1024;

	/**
	 * One file in the package.
	 *
	 * @param path full path inside the package, with forward slashes
	 * @param crc32 CRC-32 of the file contents
	 * @param archiveIndex index of the {@code _NNN.vpk} chunk holding the data, or {@link #IN_DIRECTORY}
	 * @param offset offset of the data within that chunk
	 * @param length size of the data in the chunk, excluding preload bytes
	 * @param preloadBytes number of leading bytes stored in the directory file itself
	 * @param preloadOffset where in the directory file those leading bytes are
	 */
	public record Entry(String path, int crc32, int archiveIndex, long offset, long length, int preloadBytes,
			long preloadOffset) {
		public static final int IN_DIRECTORY = 0x7FFF;

		public long size() {
			return length + preloadBytes;
		}
	}

	private VpkDirectory() {
	}

	/** Lists every entry whose full path is accepted by {@code filter}. */
	public static List<Entry> list(Path dirVpk, Predicate<String> filter) throws IOException {
		Tree index = readTree(dirVpk);
		ByteBuffer tree = index.data();
		List<Entry> entries = new ArrayList<>();

		try {
			while (true) {
				String extension = readString(tree);
				if (extension.isEmpty()) {
					break;
				}
				while (true) {
					String directory = readString(tree);
					if (directory.isEmpty()) {
						break;
					}
					while (true) {
						String name = readString(tree);
						if (name.isEmpty()) {
							break;
						}

						int crc32 = tree.getInt();
						int preloadBytes = tree.getShort() & 0xFFFF;
						int archiveIndex = tree.getShort() & 0xFFFF;
						long offset = tree.getInt() & 0xFFFFFFFFL;
						long length = tree.getInt() & 0xFFFFFFFFL;
						int terminator = tree.getShort() & 0xFFFF;
						if (terminator != ENTRY_TERMINATOR) {
							throw new IOException("Corrupt VPK index in " + dirVpk + ": bad entry terminator");
						}
						if (preloadBytes > tree.remaining()) {
							throw new EOFException("Corrupt VPK index in " + dirVpk + ": truncated preload data");
						}
						long preloadOffset = index.headerSize() + tree.position();
						tree.position(tree.position() + preloadBytes);

						String path = buildPath(directory, name, extension);
						if (filter.test(path)) {
							entries.add(new Entry(path, crc32, archiveIndex, offset, length, preloadBytes, preloadOffset));
						}
					}
				}
			}
		} catch (java.nio.BufferUnderflowException e) {
			throw new EOFException("Corrupt VPK index in " + dirVpk + ": unexpected end of index");
		}

		return entries;
	}

	/**
	 * Reads one file out of the package. Meant for the game's plain text files (item definitions,
	 * translations); compiled resources are left to the external extractor.
	 *
	 * @param maxSize refuse anything larger than this many bytes
	 */
	public static byte[] read(Path dirVpk, Entry entry, int maxSize) throws IOException {
		if (entry.size() > maxSize) {
			throw new IOException(entry.path() + " is larger than expected (" + entry.size() + " bytes)");
		}
		ByteBuffer out = ByteBuffer.allocate((int) entry.size());

		if (entry.preloadBytes() > 0) {
			try (FileChannel channel = FileChannel.open(dirVpk, StandardOpenOption.READ)) {
				out.limit(entry.preloadBytes());
				readFully(channel.position(entry.preloadOffset()), out, dirVpk);
				out.limit(out.capacity());
			}
		}
		if (entry.length() > 0) {
			Path file;
			long offset = entry.offset();
			if (entry.archiveIndex() == Entry.IN_DIRECTORY) {
				// Stored in the directory file itself, after the index.
				file = dirVpk;
				Tree index = readTree(dirVpk);
				offset += index.headerSize() + index.data().capacity();
			} else {
				String name = dirVpk.getFileName().toString();
				if (!name.endsWith(DIRECTORY_SUFFIX)) {
					throw new IOException("Not a VPK directory file: " + dirVpk);
				}
				String prefix = name.substring(0, name.length() - DIRECTORY_SUFFIX.length());
				file = dirVpk.resolveSibling(String.format(java.util.Locale.ROOT, "%s_%03d.vpk", prefix,
						entry.archiveIndex()));
			}
			try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
				readFully(channel.position(offset), out, file);
			}
		}

		java.util.zip.CRC32 crc = new java.util.zip.CRC32();
		crc.update(out.array());
		if ((int) crc.getValue() != entry.crc32()) {
			throw new IOException("Checksum mismatch reading " + entry.path() + " from " + dirVpk);
		}
		return out.array();
	}

	private record Tree(ByteBuffer data, long headerSize) {
	}

	private static Tree readTree(Path dirVpk) throws IOException {
		try (FileChannel channel = FileChannel.open(dirVpk, StandardOpenOption.READ)) {
			ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
			readFully(channel, header, dirVpk);
			header.flip();

			if (header.getInt() != SIGNATURE) {
				throw new IOException("Not a VPK directory file: " + dirVpk);
			}
			int version = header.getInt();
			long treeSize = header.getInt() & 0xFFFFFFFFL;

			long headerSize = switch (version) {
				case 1 -> 12;
				case 2 -> 28;
				default -> throw new IOException("Unsupported VPK version " + version + " in " + dirVpk);
			};
			if (treeSize > MAX_TREE_SIZE || headerSize + treeSize > channel.size()) {
				throw new IOException("Corrupt VPK header in " + dirVpk + ": index size " + treeSize);
			}

			ByteBuffer tree = ByteBuffer.allocate((int) treeSize).order(ByteOrder.LITTLE_ENDIAN);
			channel.position(headerSize);
			readFully(channel, tree, dirVpk);
			tree.flip();
			return new Tree(tree, headerSize);
		}
	}

	private static void readFully(FileChannel channel, ByteBuffer buffer, Path file) throws IOException {
		while (buffer.hasRemaining()) {
			if (channel.read(buffer) < 0) {
				throw new EOFException("Unexpected end of file in " + file);
			}
		}
	}

	private static String readString(ByteBuffer buffer) {
		int start = buffer.position();
		while (buffer.get() != 0) {
			// advance to the terminator
		}
		int length = buffer.position() - 1 - start;
		return new String(buffer.array(), buffer.arrayOffset() + start, length, StandardCharsets.UTF_8);
	}

	/** A single space stands for "empty" in each of the three path components. */
	private static String buildPath(String directory, String name, String extension) {
		StringBuilder sb = new StringBuilder(directory.length() + name.length() + extension.length() + 2);
		if (!directory.equals(" ")) {
			sb.append(directory).append('/');
		}
		if (!name.equals(" ")) {
			sb.append(name);
		}
		if (!extension.equals(" ")) {
			sb.append('.').append(extension);
		}
		return sb.toString();
	}
}
