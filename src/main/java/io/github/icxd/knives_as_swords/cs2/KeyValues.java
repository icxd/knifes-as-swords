package io.github.icxd.knives_as_swords.cs2;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Minimal reader for Valve's text KeyValues ("KV1" / VDF) format, as used by Steam's
 * {@code libraryfolders.vdf} and {@code appmanifest_*.acf}.
 *
 * <p>A node is either a leaf ({@link #value} set, no children) or a block ({@link #value} null).
 * Keys are matched case-insensitively and may repeat, so children are kept as an ordered list.
 */
public final class KeyValues {
	private static final int MAX_DEPTH = 64;

	public final String key;
	/** Leaf value, or {@code null} for a block. */
	public final String value;
	public final List<KeyValues> children;

	private KeyValues(String key, String value, List<KeyValues> children) {
		this.key = key;
		this.value = value;
		this.children = children;
	}

	/** Parses a document; the returned root has an empty key and holds the top-level nodes. */
	public static KeyValues parse(String text) {
		Parser parser = new Parser(text);
		List<KeyValues> top = parser.parseBlock(0, true);
		return new KeyValues("", null, top);
	}

	public Optional<KeyValues> child(String name) {
		for (KeyValues child : children) {
			if (child.key.equalsIgnoreCase(name)) {
				return Optional.of(child);
			}
		}
		return Optional.empty();
	}

	/** Value of the first leaf child with this key, or {@code null}. */
	public String string(String name) {
		for (KeyValues child : children) {
			if (child.value != null && child.key.equalsIgnoreCase(name)) {
				return child.value;
			}
		}
		return null;
	}

	public boolean isBlock() {
		return value == null;
	}

	private static final class Parser {
		private final String text;
		private int pos;
		private boolean lastTokenQuoted;

		Parser(String text) {
			// Strip a UTF-8 BOM if present.
			this.text = text.startsWith("﻿") ? text.substring(1) : text;
		}

		List<KeyValues> parseBlock(int depth, boolean topLevel) {
			if (depth > MAX_DEPTH) {
				throw new IllegalArgumentException("KeyValues nested deeper than " + MAX_DEPTH + " levels");
			}

			List<KeyValues> nodes = new ArrayList<>();
			while (true) {
				String key = nextToken();
				if (key == null) {
					if (!topLevel) {
						throw new IllegalArgumentException("Unexpected end of KeyValues data: missing '}'");
					}
					return nodes;
				}
				if (!lastTokenQuoted && key.equals("}")) {
					if (topLevel) {
						throw new IllegalArgumentException("Unexpected '}' at offset " + pos);
					}
					return nodes;
				}
				if (!lastTokenQuoted && key.equals("{")) {
					throw new IllegalArgumentException("Unexpected '{' at offset " + pos);
				}

				String value = nextToken();
				if (value == null) {
					throw new IllegalArgumentException("Key '" + key + "' has no value");
				}

				if (!lastTokenQuoted && value.equals("{")) {
					nodes.add(new KeyValues(key, null, parseBlock(depth + 1, false)));
				} else if (!lastTokenQuoted && value.equals("}")) {
					throw new IllegalArgumentException("Key '" + key + "' has no value");
				} else {
					nodes.add(new KeyValues(key, value, List.of()));
				}
			}
		}

		/** Returns the next token, or {@code null} at end of input. Sets {@link #lastTokenQuoted}. */
		private String nextToken() {
			skipWhitespaceAndComments();
			if (pos >= text.length()) {
				return null;
			}

			char c = text.charAt(pos);
			if (c == '"') {
				pos++;
				lastTokenQuoted = true;
				StringBuilder sb = new StringBuilder();
				while (true) {
					if (pos >= text.length()) {
						throw new IllegalArgumentException("Unterminated quoted string");
					}
					c = text.charAt(pos++);
					if (c == '"') {
						return sb.toString();
					}
					if (c == '\\' && pos < text.length()) {
						char escaped = text.charAt(pos);
						switch (escaped) {
							case '\\', '"' -> {
								sb.append(escaped);
								pos++;
							}
							case 'n' -> {
								sb.append('\n');
								pos++;
							}
							case 't' -> {
								sb.append('\t');
								pos++;
							}
							default -> sb.append('\\');
						}
					} else {
						sb.append(c);
					}
				}
			}

			lastTokenQuoted = false;
			if (c == '{' || c == '}') {
				pos++;
				return String.valueOf(c);
			}

			int start = pos;
			while (pos < text.length()) {
				c = text.charAt(pos);
				if (Character.isWhitespace(c) || c == '{' || c == '}' || c == '"') {
					break;
				}
				pos++;
			}
			return text.substring(start, pos);
		}

		private void skipWhitespaceAndComments() {
			while (pos < text.length()) {
				char c = text.charAt(pos);
				if (Character.isWhitespace(c)) {
					pos++;
				} else if (c == '/' && pos + 1 < text.length() && text.charAt(pos + 1) == '/') {
					while (pos < text.length() && text.charAt(pos) != '\n') {
						pos++;
					}
				} else {
					return;
				}
			}
		}
	}
}
