package io.github.icxd.knives_as_swords.finish;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Reads the kind of OpenEXR file the extractor writes for floating-point textures: one part,
 * scan lines, uncompressed or ZIP-compressed, with half or float channels named R, G, B and A.
 */
final class ExrReader {
	private static final int MAGIC = 20000630;
	private static final int MAX_SIDE = 8192;
	private static final int COMPRESSION_NONE = 0;
	private static final int COMPRESSION_ZIPS = 2;
	private static final int COMPRESSION_ZIP = 3;
	private static final int TYPE_UINT = 0;
	private static final int TYPE_HALF = 1;
	private static final int TYPE_FLOAT = 2;

	private record Channel(String name, int type) {
		int bytes() {
			return type == TYPE_HALF ? 2 : 4;
		}
	}

	private ExrReader() {
	}

	static Raster read(Path file) throws IOException {
		ByteBuffer data = ByteBuffer.wrap(Files.readAllBytes(file)).order(ByteOrder.LITTLE_ENDIAN);
		try {
			return read(data, file);
		} catch (IndexOutOfBoundsException | java.nio.BufferUnderflowException | IllegalArgumentException e) {
			throw new IOException("Corrupt OpenEXR file: " + file, e);
		}
	}

	private static Raster read(ByteBuffer data, Path file) throws IOException {
		if (data.remaining() < 8 || data.getInt() != MAGIC) {
			throw new IOException("Not an OpenEXR file: " + file);
		}
		int version = data.getInt();
		// Bits 9, 11 and 12: tiled, deep data, multi-part. None of those is handled.
		if ((version & 0xFF) != 2 || (version & 0x1A00) != 0) {
			throw new IOException("Unsupported kind of OpenEXR file: " + file);
		}

		List<Channel> channels = new ArrayList<>();
		int compression = -1;
		int minX = 0, minY = 0, maxX = -1, maxY = -1;
		while (true) {
			String name = string(data);
			if (name.isEmpty()) {
				break;
			}
			String type = string(data);
			int size = data.getInt();
			int next = data.position() + size;
			switch (name) {
				case "channels" -> {
					while (data.get(data.position()) != 0) {
						String channel = string(data);
						int pixelType = data.getInt();
						data.position(data.position() + 4); // linear flag and padding
						int xSampling = data.getInt();
						int ySampling = data.getInt();
						if (xSampling != 1 || ySampling != 1 || pixelType < TYPE_UINT || pixelType > TYPE_FLOAT) {
							throw new IOException("Unsupported OpenEXR channel layout: " + file);
						}
						channels.add(new Channel(channel, pixelType));
					}
				}
				case "compression" -> compression = data.get(data.position()) & 0xFF;
				case "dataWindow" -> {
					minX = data.getInt(data.position());
					minY = data.getInt(data.position() + 4);
					maxX = data.getInt(data.position() + 8);
					maxY = data.getInt(data.position() + 12);
				}
				default -> {
					// Nothing else matters for reading the pixels (type: unused).
					if (type.isEmpty()) {
						throw new IOException("Corrupt OpenEXR header: " + file);
					}
				}
			}
			data.position(next);
		}

		int width = maxX - minX + 1;
		int height = maxY - minY + 1;
		if (width < 1 || height < 1 || width > MAX_SIDE || height > MAX_SIDE || channels.isEmpty()) {
			throw new IOException("Unexpected OpenEXR image size: " + file);
		}
		int linesPerBlock = switch (compression) {
			case COMPRESSION_NONE, COMPRESSION_ZIPS -> 1;
			case COMPRESSION_ZIP -> 16;
			default -> throw new IOException("Unsupported OpenEXR compression " + compression + ": " + file);
		};

		int bytesPerLine = 0;
		for (Channel channel : channels) {
			bytesPerLine += channel.bytes() * width;
		}
		int blocks = (height + linesPerBlock - 1) / linesPerBlock;
		long[] offsets = new long[blocks];
		for (int i = 0; i < blocks; i++) {
			offsets[i] = data.getLong();
		}

		float[] rgba = new float[width * height * 4];
		for (int i = 3; i < rgba.length; i += 4) {
			rgba[i] = 1f;
		}
		byte[] block = new byte[bytesPerLine * linesPerBlock];
		byte[] scratch = new byte[block.length];
		for (long offset : offsets) {
			if (offset < 0 || offset > data.capacity() - 8) {
				throw new IOException("Corrupt OpenEXR file: " + file);
			}
			data.position((int) offset);
			int firstLine = data.getInt() - minY;
			int packedSize = data.getInt();
			int lines = Math.min(linesPerBlock, height - firstLine);
			if (firstLine < 0 || lines < 1 || packedSize < 0 || packedSize > data.remaining()) {
				throw new IOException("Corrupt OpenEXR file: " + file);
			}
			int size = lines * bytesPerLine;
			if (compression == COMPRESSION_NONE || packedSize == size) {
				data.get(block, 0, size);
			} else {
				inflate(data.array(), data.position(), packedSize, scratch, size, file);
				unfilter(scratch, block, size);
			}

			ByteBuffer pixels = ByteBuffer.wrap(block, 0, size).order(ByteOrder.LITTLE_ENDIAN);
			for (int line = 0; line < lines; line++) {
				int row = (firstLine + line) * width * 4;
				// Within a scan line every channel is stored in full, one after the other.
				for (Channel channel : channels) {
					int target = switch (channel.name()) {
						case "R" -> 0;
						case "G" -> 1;
						case "B" -> 2;
						case "A" -> 3;
						default -> -1;
					};
					for (int x = 0; x < width; x++) {
						float value = switch (channel.type()) {
							case TYPE_HALF -> Float.float16ToFloat(pixels.getShort());
							case TYPE_FLOAT -> pixels.getFloat();
							default -> (float) (pixels.getInt() & 0xFFFFFFFFL);
						};
						if (target >= 0) {
							rgba[row + x * 4 + target] = Float.isFinite(value) ? value : 0f;
						}
					}
				}
			}
		}
		return Raster.ofFloats(width, height, rgba);
	}

	private static void inflate(byte[] source, int offset, int length, byte[] out, int size, Path file)
			throws IOException {
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(source, offset, length);
			int done = 0;
			while (done < size && !inflater.finished()) {
				int read = inflater.inflate(out, done, size - done);
				if (read == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
					break;
				}
				done += read;
			}
			if (done != size) {
				throw new IOException("Corrupt OpenEXR file: " + file);
			}
		} catch (DataFormatException e) {
			throw new IOException("Corrupt OpenEXR file: " + file, e);
		} finally {
			inflater.end();
		}
	}

	/** Undoes the two steps OpenEXR applies before compressing: delta coding and byte de-interleaving. */
	private static void unfilter(byte[] in, byte[] out, int size) {
		for (int i = 1; i < size; i++) {
			in[i] = (byte) (in[i - 1] + in[i] - 128);
		}
		int half = (size + 1) / 2;
		for (int i = 0; i < size; i++) {
			out[i] = (i & 1) == 0 ? in[i / 2] : in[half + i / 2];
		}
	}

	private static String string(ByteBuffer data) {
		int start = data.position();
		while (data.get() != 0) {
			// advance to the terminator
		}
		return new String(data.array(), start, data.position() - 1 - start, StandardCharsets.US_ASCII);
	}
}
