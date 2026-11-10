package io.github.icxd.knives_as_swords.cs2;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal reader for Valve's text KeyValues3 format, as printed by the extractor for the data
 * block of materials and composite materials.
 *
 * <p>Values come back as plain Java objects: {@link Map} (insertion ordered, keyed by name) for
 * objects, {@link List} for arrays, {@link String}, {@link Double} and {@link Boolean}; nulls
 * for {@code null}. Flagged values such as {@code resource_name:"a/b.vmat"} lose their flag.
 */
public final class Kv3 {
	private static final int MAX_DEPTH = 64;

	private final String text;
	private int pos;

	private Kv3(String text) {
		this.text = text;
	}

	/** Parses a document whose root is an object. */
	public static Map<String, Object> parse(String text) {
		Kv3 parser = new Kv3(text);
		parser.skipSpace();
		Object root = parser.value(0);
		if (!(root instanceof Map<?, ?>)) {
			throw new IllegalArgumentException("KV3 document does not start with an object");
		}
		return asObject(root);
	}

	/** The object at {@code value}, or an empty one when it is something else. */
	@SuppressWarnings("unchecked")
	public static Map<String, Object> asObject(Object value) {
		return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
	}

	/** The array at {@code value}, or an empty one when it is something else. */
	@SuppressWarnings("unchecked")
	public static List<Object> asList(Object value) {
		return value instanceof List<?> list ? (List<Object>) list : List.of();
	}

	public static String asString(Object value, String fallback) {
		return value instanceof String string ? string : fallback;
	}

	public static double asNumber(Object value, double fallback) {
		if (value instanceof Double number) {
			return number;
		}
		if (value instanceof Boolean flag) {
			return flag ? 1.0 : 0.0;
		}
		return fallback;
	}

	private Object value(int depth) {
		if (depth > MAX_DEPTH) {
			throw new IllegalArgumentException("KV3 nested deeper than " + MAX_DEPTH + " levels");
		}
		skipSpace();
		if (pos >= text.length()) {
			throw new IllegalArgumentException("Unexpected end of KV3 data");
		}
		char c = text.charAt(pos);
		if (c == '{') {
			pos++;
			Map<String, Object> object = new LinkedHashMap<>();
			while (true) {
				skipSpace();
				if (peek() == '}') {
					pos++;
					return object;
				}
				String key = peek() == '"' ? quoted() : bare();
				skipSpace();
				if (peek() != '=') {
					throw new IllegalArgumentException("Expected '=' after KV3 key '" + key + "' at offset " + pos);
				}
				pos++;
				object.put(key, value(depth + 1));
				skipSpace();
				if (peek() == ',') {
					pos++;
				}
			}
		}
		if (c == '[') {
			pos++;
			List<Object> list = new ArrayList<>();
			while (true) {
				skipSpace();
				if (peek() == ']') {
					pos++;
					return list;
				}
				list.add(value(depth + 1));
				skipSpace();
				if (peek() == ',') {
					pos++;
				}
			}
		}
		if (c == '"') {
			return quoted();
		}
		if (c == '#' && pos + 1 < text.length() && text.charAt(pos + 1) == '[') {
			// A binary blob, written as hex bytes; nothing here needs its contents.
			int end = text.indexOf(']', pos);
			if (end < 0) {
				throw new IllegalArgumentException("Unterminated KV3 binary blob");
			}
			pos = end + 1;
			return null;
		}

		String word = bare();
		if (peek() == ':') {
			// A flag such as resource_name: in front of the actual value.
			pos++;
			return value(depth + 1);
		}
		switch (word) {
			case "true":
				return Boolean.TRUE;
			case "false":
				return Boolean.FALSE;
			case "null":
				return null;
			default:
				try {
					return Double.valueOf(word);
				} catch (NumberFormatException e) {
					return word;
				}
		}
	}

	private char peek() {
		if (pos >= text.length()) {
			throw new IllegalArgumentException("Unexpected end of KV3 data");
		}
		return text.charAt(pos);
	}

	private String bare() {
		int start = pos;
		while (pos < text.length()) {
			char c = text.charAt(pos);
			if (Character.isWhitespace(c) || c == '=' || c == ',' || c == ']' || c == '}' || c == '[' || c == '{'
					|| c == ':' || c == '"') {
				break;
			}
			pos++;
		}
		if (pos == start) {
			throw new IllegalArgumentException("Unexpected '" + text.charAt(pos) + "' in KV3 data at offset " + pos);
		}
		return text.substring(start, pos);
	}

	private String quoted() {
		// Triple quotes start a multi-line string that runs to the next triple quotes, unescaped.
		if (text.startsWith("\"\"\"", pos)) {
			int end = text.indexOf("\"\"\"", pos + 3);
			if (end < 0) {
				throw new IllegalArgumentException("Unterminated KV3 multi-line string");
			}
			String value = text.substring(pos + 3, end);
			pos = end + 3;
			return value.strip();
		}

		pos++;
		StringBuilder sb = new StringBuilder();
		while (true) {
			if (pos >= text.length()) {
				throw new IllegalArgumentException("Unterminated KV3 string");
			}
			char c = text.charAt(pos++);
			if (c == '"') {
				return sb.toString();
			}
			if (c == '\\' && pos < text.length()) {
				char escaped = text.charAt(pos++);
				switch (escaped) {
					case 'n' -> sb.append('\n');
					case 't' -> sb.append('\t');
					default -> sb.append(escaped);
				}
			} else {
				sb.append(c);
			}
		}
	}

	private void skipSpace() {
		while (pos < text.length()) {
			char c = text.charAt(pos);
			if (Character.isWhitespace(c)) {
				pos++;
			} else if (text.startsWith("//", pos)) {
				int end = text.indexOf('\n', pos);
				pos = end < 0 ? text.length() : end;
			} else if (text.startsWith("/*", pos)) {
				int end = text.indexOf("*/", pos);
				pos = end < 0 ? text.length() : end + 2;
			} else if (text.startsWith("<!--", pos)) {
				// The header line naming the encoding and format.
				int end = text.indexOf("-->", pos);
				pos = end < 0 ? text.length() : end + 3;
			} else {
				return;
			}
		}
	}
}
