package io.github.icxd.knives_as_swords.cs2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * A knife model found in the player's CS2 install.
 *
 * @param id short stable name taken from the model's folder, e.g. {@code karambit}
 * @param modelPath path of the compiled model inside the main package
 * @param animationFolder folder inside the main package holding this knife's first-person
 *     animation clips, ending in a slash; empty when the game has none for it
 */
public record KnifeModel(String id, String modelPath, String animationFolder) {
	/** Knives are laid out as {@code weapons/models/knife/knife_<id>/weapon_knife_<id>.vmdl_c}. */
	private static final String KNIFE_FOLDER = "weapons/models/knife/";
	private static final String MODEL_EXTENSION = ".vmdl_c";
	private static final String FOLDER_PREFIX = "knife_";
	/** First-person clips are laid out as {@code <this>/knife_<id>/<clip>_<id>.vnmclip_c}. */
	private static final String ANIMATION_FOLDER = "animation/anims/viewmodel/knife/";
	private static final String CLIP_EXTENSION = ".vnmclip_c";
	/** Clips shared by knives that have no folder of their own; their names end in {@code _knife}. */
	private static final String SHARED_ANIMATIONS = "_default_knife";

	public KnifeModel(String id, String modelPath) {
		this(id, modelPath, "");
	}

	public boolean hasAnimations() {
		return !animationFolder.isEmpty();
	}

	/**
	 * What follows a clip's name in its file name: {@code _karambit} in
	 * {@code draw_karambit.vnmclip_c}, or {@code _knife} for the shared clips.
	 */
	public String clipSuffix() {
		return animationFolder.equals(ANIMATION_FOLDER + SHARED_ANIMATIONS + "/") ? "_knife" : "_" + id;
	}

	/** Lists the knife models in the install's main package, sorted by id. */
	public static List<KnifeModel> list(Cs2Install install) throws IOException {
		List<VpkDirectory.Entry> entries = VpkDirectory.list(install.mainPackage(),
				path -> (path.startsWith(KNIFE_FOLDER) && path.endsWith(MODEL_EXTENSION))
						|| (path.startsWith(ANIMATION_FOLDER) && path.endsWith(CLIP_EXTENSION)));

		// Folders directly under the animation folder that hold at least one clip.
		Set<String> animated = new TreeSet<>();
		for (VpkDirectory.Entry entry : entries) {
			if (entry.path().startsWith(ANIMATION_FOLDER)) {
				String relative = entry.path().substring(ANIMATION_FOLDER.length());
				int slash = relative.indexOf('/');
				if (slash > 0 && relative.indexOf('/', slash + 1) < 0) {
					animated.add(relative.substring(0, slash));
				}
			}
		}

		List<KnifeModel> knives = new ArrayList<>();
		for (VpkDirectory.Entry entry : entries) {
			if (!entry.path().startsWith(KNIFE_FOLDER)) {
				continue;
			}
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

			String animations = "";
			if (animated.contains(FOLDER_PREFIX + id)) {
				animations = ANIMATION_FOLDER + FOLDER_PREFIX + id + "/";
			} else if (animated.contains(SHARED_ANIMATIONS)) {
				animations = ANIMATION_FOLDER + SHARED_ANIMATIONS + "/";
			}
			knives.add(new KnifeModel(id, entry.path(), animations));
		}

		knives.sort(Comparator.comparing(KnifeModel::id).thenComparing(KnifeModel::modelPath));
		return knives;
	}
}
