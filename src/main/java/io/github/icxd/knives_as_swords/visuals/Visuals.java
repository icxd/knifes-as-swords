package io.github.icxd.knives_as_swords.visuals;

import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.fx.WorldFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * All the mod's visuals: the list the menu shows, and the place the game's moments are handed on
 * from to the visuals that are switched on.
 *
 * <p>Client thread only.
 */
public final class Visuals {
	/** Ticks a change has to rest before it is saved, so that dragging a slider does not write the file over and over. */
	private static final int SAVE_AFTER_TICKS = 10;

	private static final List<Visual> ALL = new ArrayList<>();
	private static @Nullable KnifeRuntime runtime;
	private static boolean unsaved;
	private static int restedTicks;

	private Visuals() {
	}

	/** Adds a visual to the list. Its id has to be new. */
	public static void add(Visual visual) {
		if (ALL.stream().anyMatch(other -> other.id().equals(visual.id()))) {
			throw new IllegalArgumentException("two visuals called " + visual.id());
		}
		ALL.add(visual);
	}

	/** Every visual, in the order they were added. */
	public static List<Visual> all() {
		return List.copyOf(ALL);
	}

	public static List<Visual> in(Visual.Category category) {
		return ALL.stream().filter(visual -> visual.category() == category).toList();
	}

	/** The visual of the given kind; there is one of each. */
	public static <T extends Visual> T get(Class<T> kind) {
		for (Visual visual : ALL) {
			if (kind.isInstance(visual)) {
				return kind.cast(visual);
			}
		}
		throw new IllegalArgumentException("no visual of kind " + kind.getSimpleName());
	}

	/** Brings every visual in line with the config file, and remembers where to save to. */
	public static void load(KnifeRuntime from) {
		runtime = from;
		Map<String, Map<String, String>> saved = from.config().visuals;
		for (Visual visual : ALL) {
			visual.read(saved.getOrDefault(visual.id(), Map.of()));
		}
		unsaved = false;
	}

	/** Something about a visual was changed by the player: save it once things have settled. */
	public static void changed() {
		unsaved = true;
		restedTicks = 0;
	}

	/** Saves right away whatever is waiting to be saved. */
	public static void save() {
		if (!unsaved || runtime == null) {
			return;
		}
		unsaved = false;
		runtime.updateConfig(config -> {
			for (Visual visual : ALL) {
				config.visuals.put(visual.id(), visual.write());
			}
		});
	}

	// ---------------------------------------------------------------------------------------
	// The game's moments, handed on

	/** Once per client tick. */
	public static void tick(Minecraft client) {
		if (unsaved && ++restedTicks >= SAVE_AFTER_TICKS) {
			save();
		}
		if (client.level == null || client.player == null || client.isPaused()) {
			return;
		}
		for (Visual visual : ALL) {
			if (visual.on()) {
				visual.tick(client);
			}
		}
	}

	/** The player is about to hit an entity. */
	public static void attack(Player player, Entity target, boolean charged) {
		for (Visual visual : ALL) {
			if (visual.on()) {
				visual.attack(player, target, charged);
			}
		}
	}

	/** The world is being drawn. */
	public static void world(WorldFrame frame) {
		for (Visual visual : ALL) {
			if (visual.on()) {
				visual.world(frame);
			}
		}
	}

	/** The heads-up display is being drawn. */
	public static void hud(GuiGraphicsExtractor graphics, boolean editing) {
		for (Visual visual : ALL) {
			if (visual.on()) {
				visual.hud(graphics, editing);
			}
		}
	}
}
