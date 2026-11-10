package io.github.icxd.knives_as_swords.cs2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A knife model found in the player's CS2 install.
 *
 * @param id short stable name taken from the model's folder, e.g. {@code karambit}
 * @param modelPath path of the compiled model inside the main package
 */
public record KnifeModel(String id, String modelPath) {
	/** Knives are laid out as {@code weapons/models/knife/knife_<id>/weapon_knife_<id>.vmdl_c}. */
	private static final String KNIFE_FOLDER = "weapons/models/knife/";
	private static final String MODEL_EXTENSION = ".vmdl_c";
	private static final String FOLDER_PREFIX = "knife_";

	/** Lists the knife models in the install's main package, sorted by id. */
	public static List<KnifeModel> list(Cs2Install install) throws IOException {
		List<VpkDirectory.Entry> entries = VpkDirectory.list(install.mainPackage(),
				path -> path.startsWith(KNIFE_FOLDER) && path.endsWith(MODEL_EXTENSION));

		List<KnifeModel> knives = new ArrayList<>();
		for (VpkDirectory.Entry entry : entries) {
			// Only models sitting directly in a knife's own folder; anything nested deeper is not a knife.
			String relative = entry.path().substring(KNIFE_FOLDER.length());
			int slash = relative.indexOf('/');
			if (slash <= 0 || relative.indexOf('/', slash + 1) >= 0) {
				continue;
			}

			String folder = relative.substring(0, slash);
			String id = folder.startsWith(FOLDER_PREFIX) ? folder.substring(FOLDER_PREFIX.length()) : folder;
			if (!id.matches("[a-z0-9_]+")) {
				continue;
			}
			knives.add(new KnifeModel(id, entry.path()));
		}

		knives.sort(Comparator.comparing(KnifeModel::id).thenComparing(KnifeModel::modelPath));
		return knives;
	}
}
