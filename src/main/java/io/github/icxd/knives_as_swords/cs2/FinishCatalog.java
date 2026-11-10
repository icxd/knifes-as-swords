package io.github.icxd.knives_as_swords.cs2;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Which finishes (paint kits) the game has for which knife, and what the knives and finishes
 * are called. Read from the game's own item definitions and English texts, both plain text
 * files inside the main package.
 */
public final class FinishCatalog {
	private static final String ITEMS_FILE = "scripts/items/items_game.txt";
	private static final String TEXT_FILE = "resource/csgo_english.txt";
	/** The game ships a picture of every weapon in every finish it can have; that is the list of pairs. */
	private static final String ICON_FOLDER = "panorama/images/econ/default_generated/";
	private static final String ICON_SUFFIX = "_light_png.vtex_c";
	private static final String PAINT_FOLDER = "weapons/paints/";
	private static final String COMPOSITE_EXTENSION = ".vcompmat_c";
	private static final int MAX_TEXT_FILE_BYTES = 64 * 1024 * 1024;

	/**
	 * One finish.
	 *
	 * @param name the game's internal name, e.g. {@code aa_fade}; what the config stores
	 * @param displayName what players call it, e.g. {@code Fade}
	 * @param wearMin least wear this finish can have
	 * @param wearMax most wear this finish can have
	 * @param compositePath the compiled composite material describing it, inside the main package
	 */
	public record Finish(String name, String displayName, float wearMin, float wearMax, String compositePath) {
		/** Keeps a wear value inside what this finish allows. */
		public float clampWear(float wear) {
			return Math.clamp(wear, wearMin, Math.max(wearMin, wearMax));
		}
	}

	private static final FinishCatalog EMPTY = new FinishCatalog(Map.of(), Map.of());

	private final Map<String, List<Finish>> finishesByKnife;
	private final Map<String, String> knifeNames;

	private FinishCatalog(Map<String, List<Finish>> finishesByKnife, Map<String, String> knifeNames) {
		this.finishesByKnife = finishesByKnife;
		this.knifeNames = knifeNames;
	}

	public static FinishCatalog empty() {
		return EMPTY;
	}

	/** The finishes available for a knife, sorted by display name; empty for knives without any. */
	public List<Finish> finishes(String knifeId) {
		return finishesByKnife.getOrDefault(knifeId, List.of());
	}

	public Optional<Finish> finish(String knifeId, String finishName) {
		return finishes(knifeId).stream().filter(finish -> finish.name().equals(finishName)).findFirst();
	}

	/** What players call this knife, e.g. {@code Karambit}; falls back to a tidied-up id. */
	public String knifeName(String knifeId) {
		String name = knifeNames.get(knifeId);
		if (name != null) {
			return name;
		}
		StringBuilder pretty = new StringBuilder();
		for (String word : knifeId.split("_")) {
			if (!word.isEmpty()) {
				pretty.append(pretty.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0)))
						.append(word.substring(1));
			}
		}
		return pretty.toString();
	}

	public static FinishCatalog load(Cs2Install install, List<KnifeModel> knives) throws IOException {
		List<VpkDirectory.Entry> entries = VpkDirectory.list(install.mainPackage(),
				path -> path.equals(ITEMS_FILE) || path.equals(TEXT_FILE)
						|| (path.startsWith(ICON_FOLDER) && path.endsWith(ICON_SUFFIX))
						|| (path.startsWith(PAINT_FOLDER) && path.endsWith(COMPOSITE_EXTENSION)));

		VpkDirectory.Entry itemsEntry = null;
		VpkDirectory.Entry textEntry = null;
		List<String> icons = new ArrayList<>();
		Map<String, String> compositeByFileName = new HashMap<>();
		Map<String, String> composites = new HashMap<>();
		for (VpkDirectory.Entry entry : entries) {
			String path = entry.path();
			if (path.equals(ITEMS_FILE)) {
				itemsEntry = entry;
			} else if (path.equals(TEXT_FILE)) {
				textEntry = entry;
			} else if (path.startsWith(ICON_FOLDER)) {
				icons.add(path.substring(ICON_FOLDER.length(), path.length() - ICON_SUFFIX.length()));
			} else {
				composites.put(path, path);
				String file = path.substring(path.lastIndexOf('/') + 1, path.length() - COMPOSITE_EXTENSION.length());
				// The older finishes all live in one folder; prefer that one if a name exists twice.
				if (path.startsWith(PAINT_FOLDER + "legacy/") || !compositeByFileName.containsKey(file)) {
					compositeByFileName.put(file, path);
				}
			}
		}
		if (itemsEntry == null) {
			return EMPTY;
		}

		KeyValues items;
		try {
			String text = new String(VpkDirectory.read(install.mainPackage(), itemsEntry, MAX_TEXT_FILE_BYTES),
					StandardCharsets.UTF_8);
			items = KeyValues.parse(text).child("items_game").orElse(null);
		} catch (IllegalArgumentException e) {
			throw new IOException("Could not read " + ITEMS_FILE + ": " + e.getMessage(), e);
		}
		if (items == null) {
			return EMPTY;
		}
		Map<String, String> texts = textEntry == null ? Map.of()
				: readTexts(new String(VpkDirectory.read(install.mainPackage(), textEntry, MAX_TEXT_FILE_BYTES),
						StandardCharsets.UTF_8));

		// Which item each knife model belongs to: the item's name is what the icons are named after.
		Map<String, String> knifeByModel = new HashMap<>();
		for (KnifeModel knife : knives) {
			knifeByModel.put(knife.modelPath(), knife.id());
		}
		Map<String, String> itemByKnife = new HashMap<>();
		Map<String, String> knifeNames = new HashMap<>();
		// The sections of this file each occur several times; all of them count.
		for (KeyValues section : items.children) {
			if (!section.key.equalsIgnoreCase("items") || !section.isBlock()) {
				continue;
			}
			for (KeyValues item : section.children) {
				String model = item.string("model_player");
				String name = item.string("name");
				if (model == null || name == null) {
					continue;
				}
				String knife = knifeByModel.get(model + "_c");
				if (knife == null || itemByKnife.containsKey(knife)) {
					continue;
				}
				itemByKnife.put(knife, name);
				String displayName = translate(texts, item.string("item_name"));
				if (displayName != null) {
					knifeNames.put(knife, displayName);
				}
			}
		}

		record Kit(String name, String tag, float wearMin, float wearMax, String composite) {
		}
		float defaultMin = 0.06f;
		float defaultMax = 0.8f;
		Map<String, Kit> kits = new HashMap<>();
		for (KeyValues section : items.children) {
			if (!section.key.equalsIgnoreCase("paint_kits") || !section.isBlock()) {
				continue;
			}
			for (KeyValues kit : section.children) {
				String name = kit.string("name");
				if (name == null) {
					continue;
				}
				if (name.equals("default")) {
					defaultMin = number(kit.string("wear_remap_min"), defaultMin);
					defaultMax = number(kit.string("wear_remap_max"), defaultMax);
				}
				kits.put(name.toLowerCase(Locale.ROOT), new Kit(name, kit.string("description_tag"),
						number(kit.string("wear_remap_min"), Float.NaN), number(kit.string("wear_remap_max"), Float.NaN),
						kit.string("composite_material_path")));
			}
		}

		Map<String, List<Finish>> finishesByKnife = new TreeMap<>();
		for (Map.Entry<String, String> knife : itemByKnife.entrySet()) {
			String prefix = knife.getValue().toLowerCase(Locale.ROOT) + "_";
			List<Finish> finishes = new ArrayList<>();
			for (String icon : icons) {
				// "weapon_knife_" is also how other knives' icons start; only real finish names count.
				if (!icon.startsWith(prefix)) {
					continue;
				}
				Kit kit = kits.get(icon.substring(prefix.length()));
				if (kit == null) {
					continue;
				}
				String composite = kit.composite() != null ? composites.get(kit.composite() + "_c") : null;
				if (composite == null) {
					composite = compositeByFileName.get(kit.name());
				}
				if (composite == null) {
					continue;
				}
				String displayName = translate(texts, kit.tag());
				finishes.add(new Finish(kit.name(), displayName != null ? displayName : kit.name(),
						Float.isNaN(kit.wearMin()) ? defaultMin : kit.wearMin(),
						Float.isNaN(kit.wearMax()) ? defaultMax : kit.wearMax(), composite));
			}
			if (!finishes.isEmpty()) {
				finishesByKnife.put(knife.getKey(), List.copyOf(distinctNames(finishes)));
			}
		}
		return new FinishCatalog(finishesByKnife, knifeNames);
	}

	/**
	 * Several finishes share one display name (every phase of a Doppler is a "Doppler"). Those
	 * get their internal name appended, so each choice can be told apart.
	 */
	private static List<Finish> distinctNames(List<Finish> finishes) {
		Map<String, Integer> uses = new HashMap<>();
		for (Finish finish : finishes) {
			uses.merge(finish.displayName(), 1, Integer::sum);
		}
		List<Finish> result = new ArrayList<>();
		for (Finish finish : finishes) {
			String name = uses.get(finish.displayName()) > 1 && !finish.displayName().equals(finish.name())
					? finish.displayName() + " (" + finish.name() + ")" : finish.displayName();
			result.add(new Finish(finish.name(), name, finish.wearMin(), finish.wearMax(), finish.compositePath()));
		}
		result.sort(Comparator.comparing((Finish finish) -> finish.displayName().toLowerCase(Locale.ROOT))
				.thenComparing(Finish::name));
		return result;
	}

	/** Token to text, with tokens lower-cased because the game matches them case-insensitively. */
	static Map<String, String> readTexts(String file) {
		Map<String, String> texts = new HashMap<>();
		StringBuilder token = new StringBuilder();
		StringBuilder text = new StringBuilder();
		int length = file.length();
		int pos = 0;
		while (pos < length) {
			int lineEnd = file.indexOf('\n', pos);
			if (lineEnd < 0) {
				lineEnd = length;
			}
			// A line of interest is a quoted token followed by its quoted translation.
			int at = skipBlanks(file, pos, lineEnd);
			at = quoted(file, at, lineEnd, token);
			if (at >= 0) {
				at = quoted(file, skipBlanks(file, at, lineEnd), lineEnd, text);
				if (at >= 0 && !token.isEmpty()) {
					texts.putIfAbsent(token.toString().toLowerCase(Locale.ROOT), text.toString().strip());
				}
			}
			pos = lineEnd + 1;
		}
		return texts;
	}

	private static int skipBlanks(String text, int from, int end) {
		while (from < end && (text.charAt(from) == ' ' || text.charAt(from) == '\t' || text.charAt(from) == '\r'
				|| text.charAt(from) == '﻿')) {
			from++;
		}
		return from;
	}

	/** Reads a quoted string starting at {@code from}; returns the position after it, or -1 if there is none. */
	private static int quoted(String text, int from, int end, StringBuilder out) {
		out.setLength(0);
		if (from >= end || text.charAt(from) != '"') {
			return -1;
		}
		for (int i = from + 1; i < end; i++) {
			char c = text.charAt(i);
			if (c == '"') {
				return i + 1;
			}
			if (c == '\\' && i + 1 < end) {
				char escaped = text.charAt(++i);
				out.append(escaped == 'n' || escaped == 't' ? ' ' : escaped);
			} else {
				out.append(c);
			}
		}
		return -1;
	}

	private static String translate(Map<String, String> texts, String token) {
		if (token == null || !token.startsWith("#")) {
			return null;
		}
		String text = texts.get(token.substring(1).toLowerCase(Locale.ROOT));
		return text == null || text.isBlank() ? null : text;
	}

	private static float number(String text, float fallback) {
		if (text == null) {
			return fallback;
		}
		try {
			float value = Float.parseFloat(text.trim());
			return Float.isFinite(value) ? value : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
