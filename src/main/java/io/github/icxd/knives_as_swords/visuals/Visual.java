package io.github.icxd.knives_as_swords.visuals;

import io.github.icxd.knives_as_swords.fx.Gradient;
import io.github.icxd.knives_as_swords.fx.WorldFrame;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * One visual of the mod: something the player can switch on and off in the menu, with settings of
 * its own.
 *
 * <p>A visual only ever draws. It may look at the game to know what to draw, but it changes
 * nothing about it; see the fair-play rules in CLAUDE.md, which every visual has to keep.
 *
 * <p>Subclasses declare their settings as fields with {@link #toggle}, {@link #slider} and so on,
 * and override the moments they care about. Those are only called while the visual is on.
 * Client thread only.
 */
public abstract class Visual {
	/** The name the on/off state is saved under, next to the visual's settings. */
	private static final String ENABLED = "enabled";

	/** Where a visual is listed in the menu. */
	public enum Category {
		COMBAT, PLAYER, WORLD, HUD;

		/** The category's name as used in translation keys. */
		public String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private final String id;
	private final Category category;
	private final boolean onAtFirst;
	private final List<Setting> settings = new ArrayList<>();
	private boolean on;

	/**
	 * @param id the name the visual is saved under and looked up by; lower case with underscores
	 * @param onAtFirst whether the visual is on for a player who has never touched it
	 */
	protected Visual(String id, Category category, boolean onAtFirst) {
		this.id = id;
		this.category = category;
		this.onAtFirst = onAtFirst;
		this.on = onAtFirst;
	}

	public String id() {
		return id;
	}

	public Category category() {
		return category;
	}

	public boolean on() {
		return on;
	}

	public void set(boolean on) {
		if (this.on != on) {
			this.on = on;
			switched();
		}
	}

	/** The settings, in the order they are offered. */
	public List<Setting> settings() {
		return settings;
	}

	/**
	 * Whether this visual puts information on screen that the game itself does not show, such as
	 * the health of what the player is fighting. Such visuals have to be off at first, and the menu
	 * marks them: some servers do not allow them.
	 */
	public boolean showsInformation() {
		return false;
	}

	// ---------------------------------------------------------------------------------------
	// Declaring settings

	protected Setting.Toggle toggle(String key, boolean initial) {
		return add(new Setting.Toggle(key, initial));
	}

	protected Setting.Slider slider(String key, float initial, float min, float max, float step) {
		return add(new Setting.Slider(key, initial, min, max, step));
	}

	protected Setting.Choice choice(String key, String... options) {
		return add(new Setting.Choice(key, options));
	}

	protected Setting.Color color(String key, int rgb) {
		return add(new Setting.Color(key, rgb));
	}

	protected Setting.Colors colors(String key, int... rgb) {
		return add(new Setting.Colors(key, Gradient.evenly(rgb)));
	}

	protected Setting.Action action(String key, Runnable action) {
		return add(new Setting.Action(key, action));
	}

	private <T extends Setting> T add(T setting) {
		if (setting.key().equals(ENABLED) || settings.stream().anyMatch(other -> other.key().equals(setting.key()))) {
			throw new IllegalArgumentException("setting '" + setting.key() + "' of " + id + " is taken");
		}
		settings.add(setting);
		return setting;
	}

	// ---------------------------------------------------------------------------------------
	// Saving

	/** Everything about this visual as it is written in the config file. */
	public Map<String, String> write() {
		Map<String, String> values = new LinkedHashMap<>();
		values.put(ENABLED, Boolean.toString(on));
		for (Setting setting : settings) {
			String text = setting.write();
			if (text != null) {
				values.put(setting.key(), text);
			}
		}
		return values;
	}

	/** Takes over what the config file says. What it does not mention goes back to how it is at first. */
	public void read(Map<String, String> values) {
		boolean wanted = onAtFirst;
		String enabled = values.get(ENABLED);
		if ("true".equals(enabled) || "false".equals(enabled)) {
			wanted = enabled.equals("true");
		}
		for (Setting setting : settings) {
			setting.reset();
			String text = values.get(setting.key());
			if (text != null) {
				setting.read(text);
			}
		}
		set(wanted);
	}

	// ---------------------------------------------------------------------------------------
	// The moments a visual can care about; called only while it is on

	/** The visual was switched on or off. */
	protected void switched() {
	}

	/** Once per client tick, while a world is loaded. */
	public void tick(Minecraft client) {
	}

	/**
	 * The player is about to hit an entity. Only for looking.
	 *
	 * @param charged whether the attack was fully wound up
	 */
	public void attack(Player player, Entity target, boolean charged) {
	}

	/** The world is being drawn: add light to it. */
	public void world(WorldFrame frame) {
	}

	/**
	 * The heads-up display is being drawn.
	 *
	 * @param editing whether the menu is open, in which case things that can be moved about should
	 *     show themselves even if they would be empty right now
	 */
	public void hud(GuiGraphicsExtractor graphics, boolean editing) {
	}
}
