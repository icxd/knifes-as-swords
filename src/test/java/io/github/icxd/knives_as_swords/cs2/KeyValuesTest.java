package io.github.icxd.knives_as_swords.cs2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class KeyValuesTest {
	@Test
	void parsesNestedBlocksAndEscapes() {
		KeyValues root = KeyValues.parse("""
				// a comment
				"libraryfolders"
				{
					"0"
					{
						"path"		"C:\\\\Program Files (x86)\\\\Steam"
						"label"		"say \\"hi\\""
						"apps"
						{
							"730"		"12345"
						}
					}
				}
				""");

		KeyValues first = root.child("libraryfolders").orElseThrow().child("0").orElseThrow();
		assertEquals("C:\\Program Files (x86)\\Steam", first.string("path"));
		assertEquals("say \"hi\"", first.string("label"));
		assertEquals("12345", first.child("apps").orElseThrow().string("730"));
		assertTrue(first.child("apps").orElseThrow().isBlock());
	}

	@Test
	void keysAreCaseInsensitiveAndMayBeUnquoted() {
		KeyValues root = KeyValues.parse("AppState { installdir \"Some Game\" BuildID 42 }");

		KeyValues state = root.child("appstate").orElseThrow();
		assertEquals("Some Game", state.string("InstallDir"));
		assertEquals("42", state.string("buildid"));
		assertNull(state.string("missing"));
	}

	@Test
	void keepsRepeatedKeys() {
		KeyValues root = KeyValues.parse("\"a\" \"1\" \"a\" \"2\"");

		assertEquals(2, root.children.size());
		assertEquals("1", root.string("a"));
	}

	@Test
	void quotedBracesAreValues() {
		KeyValues root = KeyValues.parse("\"key\" \"{\"");

		assertEquals("{", root.string("key"));
	}

	@Test
	void rejectsMalformedInput() {
		assertThrows(IllegalArgumentException.class, () -> KeyValues.parse("\"a\" {"));
		assertThrows(IllegalArgumentException.class, () -> KeyValues.parse("\"a\""));
		assertThrows(IllegalArgumentException.class, () -> KeyValues.parse("}"));
		assertThrows(IllegalArgumentException.class, () -> KeyValues.parse("\"a\" \"unterminated"));
		assertThrows(IllegalArgumentException.class, () -> KeyValues.parse("a { ".repeat(200)));
	}
}
