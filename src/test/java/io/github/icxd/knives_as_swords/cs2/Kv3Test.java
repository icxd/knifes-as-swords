package io.github.icxd.knives_as_swords.cs2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Kv3Test {
	private static final String MATERIAL = """
			<!-- kv3 encoding:text:version{e21c7f3c-8a33-41c5-9977-a76d3a32aa0d} format:generic:version{7412167c-06e9-4698-aff2-e63eb59037e7} -->
			{
				m_materialName = "paints/example.vmat"
				m_intParams =\s
				[
					{
						m_name = "F_PAINT_STYLE"
						m_nValue = 5
					},
				]
				m_vectorParams = [ { m_name = "g_vColor0", m_value = [ 0.5, 1.0, -0.25, 1.0 ] } ]
				m_textureParams =
				[
					{
						m_name = "g_tPattern"
						m_pValue = resource:"paints/example_pattern.vtex"
					},
				]
				m_dynamicParams = [  ]
				m_bFlag = true
				m_nothing = null
				// a comment
				m_blob = #[ 00 FF ]
			}
			""";

	@Test
	void readsWhatTheExtractorPrintsForAMaterial() {
		Map<String, Object> root = Kv3.parse(MATERIAL);

		assertEquals("paints/example.vmat", root.get("m_materialName"));
		Map<String, Object> style = Kv3.asObject(Kv3.asList(root.get("m_intParams")).get(0));
		assertEquals("F_PAINT_STYLE", style.get("m_name"));
		assertEquals(5.0, Kv3.asNumber(style.get("m_nValue"), -1));
		Map<String, Object> color = Kv3.asObject(Kv3.asList(root.get("m_vectorParams")).get(0));
		assertEquals(List.of(0.5, 1.0, -0.25, 1.0), color.get("m_value"));
		// A flag in front of a value (here "resource:") is dropped.
		Map<String, Object> pattern = Kv3.asObject(Kv3.asList(root.get("m_textureParams")).get(0));
		assertEquals("paints/example_pattern.vtex", pattern.get("m_pValue"));
		assertEquals(List.of(), root.get("m_dynamicParams"));
		assertEquals(1.0, Kv3.asNumber(root.get("m_bFlag"), 0));
		assertNull(root.get("m_nothing"));
		assertNull(root.get("m_blob"));
	}

	@Test
	void missingOrMistypedValuesFallBack() {
		Map<String, Object> root = Kv3.parse("{ a = \"text\" b = [ 1, 2 ] }");

		assertEquals(7.0, Kv3.asNumber(root.get("a"), 7));
		assertEquals("fallback", Kv3.asString(root.get("b"), "fallback"));
		assertEquals(Map.of(), Kv3.asObject(root.get("missing")));
		assertEquals(List.of(), Kv3.asList(root.get("a")));
	}

	@Test
	void rejectsBrokenInput() {
		assertThrows(IllegalArgumentException.class, () -> Kv3.parse("{ a = "));
		assertThrows(IllegalArgumentException.class, () -> Kv3.parse("{ a 1 }"));
		assertThrows(IllegalArgumentException.class, () -> Kv3.parse("[ 1, 2 ]"));
		assertThrows(IllegalArgumentException.class, () -> Kv3.parse("{ a = \"unterminated }"));
	}

	@Test
	void refusesBottomlessNesting() {
		String deep = "{ a = " + "[".repeat(200) + "]".repeat(200) + " }";

		assertThrows(IllegalArgumentException.class, () -> Kv3.parse(deep));
	}
}
