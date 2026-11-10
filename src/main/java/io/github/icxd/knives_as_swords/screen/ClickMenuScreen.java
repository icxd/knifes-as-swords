package io.github.icxd.knives_as_swords.screen;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.fx.Gradient;
import io.github.icxd.knives_as_swords.ui.Backdrop;
import io.github.icxd.knives_as_swords.ui.Ease;
import io.github.icxd.knives_as_swords.ui.Glow;
import io.github.icxd.knives_as_swords.ui.Theme;
import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import io.github.icxd.knives_as_swords.visuals.Visuals;
import io.github.icxd.knives_as_swords.visuals.hud.HudWidget;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The menu for all the mod's visuals: one panel of dark glass per category, each glowing in its
 * own colours and listing its visuals.
 *
 * <p>Left-clicking a visual switches it on or off; right-clicking it unfolds its settings.
 * Panels are moved by dragging their title and folded by right-clicking it. While the menu is
 * open, the panels on the heads-up display can be dragged to where they should sit. The world
 * stays visible behind the menu, so a change shows at once.
 *
 * <p>Everything is drawn by hand with {@link Glow}: a pass over the panels lays them out as
 * lines, and the same lines are what is drawn and what the mouse acts on. Things that change
 * (switches, folding, the light under the mouse) glide rather than jump; see {@link Ease}.
 */
public final class ClickMenuScreen extends Screen {
	private static final String PREFIX = KnivesAsSwordsClient.MOD_ID + ".";
	private static final int PANEL_WIDTH = 142;
	private static final int TITLE_HEIGHT = 21;
	private static final int ROW_HEIGHT = 15;
	private static final int SETTING_HEIGHT = 14;
	private static final int PAD = 8;
	/** Room under a panel's last line, so that it does not sit on the rim. */
	private static final int FOOT = 4;

	/** Which sizes the saved places of the panels were made for; places made for others are laid out afresh. */
	private static final String LAYOUT = "2";

	/** What a line of a panel is. */
	private enum Kind {
		TITLE, VISUAL, TOGGLE, SLIDER, CHOICE, COLOR, COLOR_PART, COLORS, ACTION
	}

	/** One panel: a category, where it is, and whether it is unfolded. */
	private static final class Panel {
		final Visual.Category category;
		int x;
		int y;
		int height;
		boolean open = true;

		Panel(Visual.Category category) {
			this.category = category;
		}

		boolean holds(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + PANEL_WIDTH && mouseY >= y && mouseY < y + height;
		}
	}

	/** One line of a panel as it is laid out right now. */
	private static final class Row {
		final Kind kind;
		final Panel panel;
		final @Nullable Visual visual;
		final @Nullable Setting setting;
		/** For a colour's own sliders: 0 hue, 1 saturation, 2 brightness. */
		final int part;
		final int x;
		final int y;
		final int width;
		final int height;
		/** While something folds or unfolds, only what lies between these two heights is there yet. */
		int clipTop = Integer.MIN_VALUE;
		int clipBottom = Integer.MAX_VALUE;

		Row(Kind kind, Panel panel, @Nullable Visual visual, @Nullable Setting setting, int part, int x, int y,
				int width, int height) {
			this.kind = kind;
			this.panel = panel;
			this.visual = visual;
			this.setting = setting;
			this.part = part;
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
		}

		boolean holds(double mouseX, double mouseY) {
			return mouseX >= x && mouseX < x + width && mouseY >= Math.max(y, clipTop)
					&& mouseY < Math.min(y + height, clipBottom);
		}

		/** A name for this line that stays the same from frame to frame, for what glides on it. */
		String key(String what) {
			return what + ':' + (visual != null ? visual.id() : panel.category.key()) + '/'
					+ (setting != null ? setting.key() : "") + '/' + part;
		}
	}

	private final @Nullable Screen parent;
	private final KnifeRuntime runtime;
	private final List<Panel> panels = new ArrayList<>();
	private final List<Row> rows = new ArrayList<>();
	/** The visuals whose settings are unfolded, and the colours whose sliders are. */
	private final Set<String> unfolded = new HashSet<>();
	private final Set<Setting> unfoldedColors = new HashSet<>();

	private @Nullable Panel movingPanel;
	private @Nullable HudWidget movingWidget;
	private double grabX;
	private double grabY;
	private @Nullable Row sliding;
	private final long openedNanos = System.nanoTime();
	/** Whether the panels have a place yet: where the player left them, or laid out afresh. */
	private boolean placed;

	public ClickMenuScreen(@Nullable Screen parent) {
		super(Component.translatable(PREFIX + "menu.title"));
		this.parent = parent;
		this.runtime = KnivesAsSwordsClient.runtime();

		// Where the player left the panels.
		Map<String, String> saved = runtime.config().menu;
		placed = LAYOUT.equals(saved.get("layout"));
		for (Visual.Category category : Visual.Category.values()) {
			Panel panel = new Panel(category);
			placed &= saved.containsKey("panel." + category.key() + ".x");
			panel.x = number(saved.get("panel." + category.key() + ".x"), 10);
			panel.y = number(saved.get("panel." + category.key() + ".y"), 30);
			panel.open = !"false".equals(saved.get("panel." + category.key() + ".open"));
			panels.add(panel);
		}
	}

	@Override
	protected void init() {
		if (placed) {
			return;
		}
		// Side by side along the top, starting a new row where the screen ends.
		placed = true;
		int x = 10;
		int y = 30;
		int rowHeight = 0;
		for (Panel panel : panels) {
			if (x > 10 && x + PANEL_WIDTH > width - 6) {
				x = 10;
				y += rowHeight + 10;
				rowHeight = 0;
			}
			panel.x = x;
			panel.y = y;
			x += PANEL_WIDTH + 10;
			rowHeight = Math.max(rowHeight, TITLE_HEIGHT + Visuals.in(panel.category).size() * ROW_HEIGHT + FOOT);
		}
	}

	private static int number(@Nullable String text, int fallback) {
		try {
			return text != null ? Integer.parseInt(text) : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Whether this menu is what is on screen right now; the heads-up display shows itself for dragging then. */
	public static boolean showing() {
		return Minecraft.getInstance().gui.screen() instanceof ClickMenuScreen;
	}

	/** Seconds since the menu was opened. */
	private float time() {
		return (System.nanoTime() - openedNanos) / 1e9f;
	}

	// ---------------------------------------------------------------------------------------
	// Laying out

	/**
	 * Works out where every line of every panel is.
	 *
	 * @param moving whether what is folding or unfolding moves on by a frame's worth; false when
	 *     this is only to find out what the mouse is on
	 */
	private void layout(boolean moving) {
		rows.clear();
		for (Panel panel : panels) {
			panel.x = Math.clamp(panel.x, 26 - PANEL_WIDTH, Math.max(width - 26, 0));
			panel.y = Math.clamp(panel.y, 4, Math.max(height - TITLE_HEIGHT, 4));
			int y = panel.y;
			rows.add(new Row(Kind.TITLE, panel, null, null, 0, panel.x, y, PANEL_WIDTH, TITLE_HEIGHT));
			y += TITLE_HEIGHT;

			float panelOpen = glide("panel:" + panel.category.key(), panel.open, moving);
			int bodyTop = y;
			int firstBodyRow = rows.size();
			if (panelOpen > 0f) {
				for (Visual visual : Visuals.in(panel.category)) {
					rows.add(new Row(Kind.VISUAL, panel, visual, null, 0, panel.x, y, PANEL_WIDTH, ROW_HEIGHT));
					y += ROW_HEIGHT;
					float unfold = glide("unfold:" + visual.id(), unfolded.contains(visual.id()), moving);
					if (unfold <= 0f) {
						continue;
					}
					// The settings come out from under their visual: all laid out, but only shown so far.
					int blockTop = y;
					int firstSetting = rows.size();
					for (Setting setting : visual.settings()) {
						if (!setting.shown()) {
							continue;
						}
						Kind kind = switch (setting) {
							case Setting.Toggle toggle -> Kind.TOGGLE;
							case Setting.Slider slider -> Kind.SLIDER;
							case Setting.Choice choice -> Kind.CHOICE;
							case Setting.Color color -> Kind.COLOR;
							case Setting.Colors colors -> Kind.COLORS;
							case Setting.Action action -> Kind.ACTION;
						};
						rows.add(new Row(kind, panel, visual, setting, 0, panel.x, y, PANEL_WIDTH, SETTING_HEIGHT));
						y += SETTING_HEIGHT;
						if (kind == Kind.COLOR && unfoldedColors.contains(setting)) {
							for (int part = 0; part < 3; part++) {
								rows.add(new Row(Kind.COLOR_PART, panel, visual, setting, part, panel.x, y, PANEL_WIDTH,
										SETTING_HEIGHT));
								y += SETTING_HEIGHT;
							}
						}
					}
					int shown = Math.round((y - blockTop) * unfold);
					for (int i = firstSetting; i < rows.size(); i++) {
						rows.get(i).clipTop = blockTop;
						rows.get(i).clipBottom = blockTop + shown;
					}
					y = blockTop + shown;
				}
			}
			// A panel folding away takes all its lines with it.
			int shown = Math.round((y - bodyTop) * panelOpen);
			for (int i = firstBodyRow; i < rows.size(); i++) {
				Row row = rows.get(i);
				row.clipTop = Math.max(row.clipTop, bodyTop);
				row.clipBottom = Math.min(row.clipBottom, bodyTop + shown);
			}
			panel.height = TITLE_HEIGHT + shown + Math.round(FOOT * panelOpen);
		}
	}

	/** How far something that opens and closes is open right now, 0 to 1. */
	private static float glide(String key, boolean open, boolean moving) {
		float target = open ? 1f : 0f;
		return moving ? Ease.toward(key, target, 18f) : Ease.at(key, target);
	}

	/** The panel under the mouse; where panels overlap, the one drawn last. */
	private @Nullable Panel panelAt(double mouseX, double mouseY) {
		for (int i = panels.size() - 1; i >= 0; i--) {
			if (panels.get(i).holds(mouseX, mouseY)) {
				return panels.get(i);
			}
		}
		return null;
	}

	/** The line under the mouse, if the panel under the mouse has one there. */
	private @Nullable Row rowAt(double mouseX, double mouseY) {
		Panel panel = panelAt(mouseX, mouseY);
		if (panel != null) {
			for (Row row : rows) {
				if (row.panel == panel && row.holds(mouseX, mouseY)) {
					return row;
				}
			}
		}
		return null;
	}

	// ---------------------------------------------------------------------------------------
	// Drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// No blur, and less shade than the mod's other screens have: what is being set up stays in view.
		Backdrop.draw(graphics, width, height, Theme.ACCENT, 0.8f);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Ease.frame();
		layout(true);
		float time = time();

		int titleColor = Theme.now(Theme.ACCENT, time, 0f);
		String heading = title.getString().toUpperCase(Locale.ROOT);
		Glow.text(graphics, font, heading, 12, 11, 0xFFFFFFFF, FxMath.fade(titleColor, 0.4f));
		graphics.text(font, Component.translatable(PREFIX + "menu.help"), 12 + font.width(heading) + 14, 11, Theme.DIM,
				false);

		boolean busy = movingPanel != null || movingWidget != null;
		Row hovered = sliding != null ? sliding : busy ? null : rowAt(mouseX, mouseY);
		for (Panel panel : List.copyOf(panels)) {
			// Later panels lie on top of earlier ones.
			graphics.nextStratum();
			drawPanel(graphics, panel, hovered, time);
		}

		// What the visual under the mouse is about, along the bottom.
		if (hovered != null && hovered.visual != null) {
			Visual visual = hovered.visual;
			Component about = Component.translatable(PREFIX + "visual." + visual.id() + ".about");
			graphics.nextStratum();
			int textWidth = Math.min(width - 28, 380);
			int lines = font.split(about, textWidth).size() + (visual.showsInformation() ? 1 : 0);
			int top = height - 13 - lines * font.lineHeight;
			int accent = Theme.now(Theme.colors(visual.category()), time, 0f);
			Glow.pane(graphics, 8, top - 6, 20 + textWidth, height - 7, 5f, Theme.GLASS, FxMath.fade(accent, 0.5f), 0.22f);
			graphics.textWithWordWrap(font, about, 14, top, textWidth, Theme.TEXT, false);
			if (visual.showsInformation()) {
				graphics.text(font, Component.translatable(PREFIX + "menu.information"), 14,
						top + (lines - 1) * font.lineHeight, Theme.WARNING, false);
			}
		}

		// A little light goes with the mouse.
		graphics.nextStratum();
		Glow.dot(graphics, mouseX, mouseY, 34f, FxMath.fade(titleColor, 0.09f));
	}

	private void drawPanel(GuiGraphicsExtractor graphics, Panel panel, @Nullable Row hovered, float time) {
		int order = panel.category.ordinal();
		Gradient colors = Theme.colors(panel.category);
		int accent = Theme.now(colors, time, 0f);
		int accentEnd = Theme.now(colors, time, 0.5f);
		// Each panel drops into place a moment after the one before it.
		float arrived = Ease.out((time - order * 0.05f) / 0.3f);
		boolean held = movingPanel == panel;

		graphics.pose().pushMatrix();
		graphics.pose().translate(0f, (1f - arrived) * -12f);
		int x = panel.x;
		int y = panel.y;
		Glow.pane(graphics, x, y, x + PANEL_WIDTH, y + panel.height, 6f, FxMath.fade(Theme.GLASS, arrived),
				FxMath.fade(accent, (held ? 0.9f : 0.5f) * arrived), (held ? 0.45f : 0.24f) * arrived);

		for (Row row : rows) {
			if (row.panel != panel || row.clipBottom <= Math.max(row.y, row.clipTop)) {
				continue;
			}
			boolean cut = row.y + row.height > row.clipBottom || row.y < row.clipTop;
			if (cut) {
				graphics.enableScissor(row.x, Math.max(row.clipTop, row.y), row.x + row.width,
						Math.min(row.clipBottom, row.y + row.height));
			}
			drawRow(graphics, row, row == hovered, order, accent, accentEnd, time);
			if (cut) {
				graphics.disableScissor();
			}
		}
		graphics.pose().popMatrix();
	}

	private void drawRow(GuiGraphicsExtractor graphics, Row row, boolean hovered, int order, int accent, int accentEnd,
			float time) {
		int x = row.x;
		int y = row.y;
		int right = x + row.width;
		int textY = y + (row.height - font.lineHeight) / 2 + 1;
		float middle = y + row.height / 2f;
		float hover = Ease.toward(row.key("hover"), hovered ? 1f : 0f, 24f);
		Visual visual = row.visual;

		switch (row.kind) {
			case TITLE -> {
				String name = Component.translatable(PREFIX + "menu.category." + row.panel.category.key()).getString();
				Glow.text(graphics, font, name, x + PAD, y + 6, 0xFFFFFFFF, FxMath.fade(accent, 0.55f));
				// A light that says whether the panel is folded.
				float open = Ease.at("panel:" + row.panel.category.key(), row.panel.open ? 1f : 0f);
				Glow.dot(graphics, right - PAD - 2, y + 10, 6f, FxMath.fade(accent, 0.25f + 0.55f * open));
				Glow.disc(graphics, right - PAD - 2, y + 10, 1.5f, FxMath.mix(Theme.FAINT, 0xFFFFFFFF, open));
				// A line of light under the title, with a brighter spot running along it.
				float lineY = y + TITLE_HEIGHT - 3.5f;
				float half = (x + right) / 2f;
				Glow.lightAcross(graphics, x + PAD, lineY, half, lineY + 1f, FxMath.fade(accent, 0.12f),
						FxMath.fade(accent, 0.85f));
				Glow.lightAcross(graphics, half, lineY, right - PAD, lineY + 1f, FxMath.fade(accentEnd, 0.85f),
						FxMath.fade(accentEnd, 0.12f));
				float run = (time * 0.22f + order * 0.37f) % 1f;
				graphics.enableScissor(x + PAD, y, right - PAD, y + TITLE_HEIGHT);
				Glow.dot(graphics, x + PAD + (row.width - PAD * 2) * run, lineY + 0.5f, 12f,
						FxMath.fade(FxMath.mix(accent, accentEnd, run), 0.65f));
				graphics.disableScissor();
			}
			case VISUAL -> {
				float on = Ease.toward(row.key("on"), visual.on() ? 1f : 0f, 16f);
				if (hover > 0f) {
					Glow.lightAcross(graphics, x + 1, y, right - 1, y + row.height, FxMath.fade(accent, 0.22f * hover),
							FxMath.fade(accent, 0f));
				}
				// A bar of light in front of what is switched on.
				float bar = 3f + 6f * on;
				if (on > 0f) {
					Glow.halo(graphics, x + PAD - 3f, middle - bar / 2f + 1f, x + PAD - 1f, middle + bar / 2f - 1f, 6f,
							FxMath.fade(accent, 0.75f * on));
				}
				Glow.rounded(graphics, x + PAD - 3.5f, middle - bar / 2f, x + PAD - 0.5f, middle + bar / 2f, 1.5f,
						FxMath.mix(Theme.FAINT, accent, on));

				int marks = right - PAD;
				if (visual.settings().stream().anyMatch(Setting::shown)) {
					// Three small lights: there is more to set here.
					float unfold = Ease.at("unfold:" + visual.id(), 0f);
					int lit = FxMath.mix(Theme.FAINT, accent, Math.max(unfold, hover * 0.6f));
					for (int dot = 0; dot < 3; dot++) {
						Glow.disc(graphics, marks - 1.5f - dot * 4f, middle, 1f, lit);
					}
					marks -= 15;
				}
				if (visual.showsInformation()) {
					Glow.text(graphics, font, "!", marks - font.width("!"), textY, Theme.WARNING,
							FxMath.fade(Theme.WARNING, 0.35f));
					marks -= 7;
				}
				String name = fit(Component.translatable(PREFIX + "visual." + visual.id()).getString(),
						marks - (x + PAD + 4) - 3);
				Glow.text(graphics, font, name, x + PAD + 4 + 2f * hover, textY,
						FxMath.mix(Theme.DIM, 0xFFFFFFFF, Math.max(on, hover * 0.5f)), FxMath.fade(accent, 0.4f * on));
			}
			default -> {
				Glow.fill(graphics, x + 1, y, right - 1, y + row.height, Theme.INSET);
				// The line of the visual's bar goes on down the side of its settings.
				Glow.fill(graphics, x + PAD - 2.5f, y, x + PAD - 1.5f, y + row.height, FxMath.fade(accent, 0.35f));
				if (hover > 0f) {
					Glow.lightAcross(graphics, x + 1, y, right - 1, y + row.height, FxMath.fade(accent, 0.14f * hover),
							FxMath.fade(accent, 0f));
				}
				drawSetting(graphics, row, x + PAD + 4, right - PAD, textY, accent, hover);
			}
		}
	}

	private void drawSetting(GuiGraphicsExtractor graphics, Row row, int left, int right, int textY, int accent,
			float hover) {
		Visual visual = row.visual;
		Setting setting = row.setting;
		float middle = row.y + row.height / 2f;
		int ink = FxMath.mix(0xFFC6C6D4, 0xFFFFFFFF, hover);
		String label = Component.translatable(PREFIX + "visual." + visual.id() + "." + setting.key()).getString();
		switch (setting) {
			case Setting.Toggle toggle -> {
				graphics.text(font, fit(label, right - left - 22), left, textY, ink, false);
				// A switch: a short track with a knob that slides across and lights up.
				float on = Ease.toward(row.key("switch"), toggle.on() ? 1f : 0f, 18f);
				float trackLeft = right - 16f;
				if (on > 0f) {
					Glow.halo(graphics, trackLeft + 3f, middle - 1f, right - 3f, middle + 1f, 6f, FxMath.fade(accent, 0.55f * on));
				}
				Glow.rounded(graphics, trackLeft, middle - 4f, right, middle + 4f, 4f,
						FxMath.mix(Theme.TRACK, FxMath.mix(accent, 0xFF000000, 0.35f), on));
				Glow.disc(graphics, trackLeft + 4f + 8f * on, middle, 3f, FxMath.mix(Theme.DIM, 0xFFFFFFFF, on));
			}
			case Setting.Slider slider -> {
				String value = slider.text();
				graphics.text(font, fit(label, right - left - font.width(value) - 4), left, textY - 2, ink, false);
				graphics.text(font, value, right - font.width(value), textY - 2, FxMath.mix(accent, 0xFFFFFFFF, 0.6f), false);
				track(graphics, row, slider.fraction(), accent, accent, hover);
			}
			case Setting.Choice choice -> {
				String value = Component.translatable(PREFIX + "visual." + visual.id() + "." + setting.key() + "."
						+ choice.get()).getString();
				graphics.text(font, fit(label, right - left - font.width(value) - 4), left, textY, ink, false);
				Glow.text(graphics, font, value, right - font.width(value), textY, FxMath.mix(accent, 0xFFFFFFFF, 0.35f),
						FxMath.fade(accent, 0.25f + 0.3f * hover));
			}
			case Setting.Color color -> {
				if (row.kind == Kind.COLOR_PART) {
					int part = row.part;
					String name = Component.translatable(PREFIX + "menu.color." + part).getString();
					String value = Math.round(color.part(part) * (part == 0 ? 360f : 100f)) + (part == 0 ? "°" : "%");
					graphics.text(font, name, left + 6, textY - 2, Theme.DIM, false);
					graphics.text(font, value, right - font.width(value), textY - 2, ink, false);
					track(graphics, row, color.part(part), color.argb(1f), color.argb(1f), hover);
				} else {
					graphics.text(font, fit(label, right - left - 22), left, textY, ink, false);
					float open = unfoldedColors.contains(setting) ? 1f : 0f;
					Glow.halo(graphics, right - 13f, middle - 2f, right - 3f, middle + 2f, 6f,
							FxMath.fade(color.argb(1f), 0.45f + 0.35f * Math.max(hover, open)));
					Glow.rounded(graphics, right - 16f, middle - 4f, right, middle + 4f, 3f, color.argb(1f));
				}
			}
			case Setting.Colors colors -> {
				graphics.text(font, fit(label, right - left - 52), left, textY, ink, false);
				// The gradient itself, in a strip, with its own light around it.
				float stripLeft = right - 46f;
				Gradient gradient = colors.get();
				Glow.halo(graphics, stripLeft + 3f, middle - 2f, right - 3f, middle + 2f, 6f,
						FxMath.fade(gradient.at(0.5f), 0.35f + 0.3f * hover));
				int pieces = 12;
				for (int piece = 0; piece < pieces; piece++) {
					float from = stripLeft + 46f * piece / pieces;
					float to = stripLeft + 46f * (piece + 1) / pieces;
					int start = gradient.at(piece / (float) pieces) | 0xFF000000;
					int end = gradient.at((piece + 1) / (float) pieces) | 0xFF000000;
					if (piece == 0) {
						// Round at the strip's own ends only.
						Glow.rounded(graphics, from, middle - 4f, to + 3f, middle + 4f, 3f, start, end);
					} else if (piece == pieces - 1) {
						Glow.rounded(graphics, from - 3f, middle - 4f, to, middle + 4f, 3f, start, end);
					} else {
						Glow.across(graphics, from, middle - 4f, to, middle + 4f, start, end);
					}
				}
			}
			case Setting.Action action -> {
				String text = fit(label, right - left);
				Glow.text(graphics, font, text, left + (right - left - font.width(text)) / 2f, textY,
						FxMath.mix(accent, 0xFFFFFFFF, 0.3f + 0.5f * hover), FxMath.fade(accent, 0.3f + 0.4f * hover));
			}
		}
	}

	/**
	 * The track along the bottom of a line that is a slider: filled with light as far as the value
	 * goes, with a knob there.
	 *
	 * @param fill the colour of the filled part
	 * @param knobGlow the colour of the light around the knob
	 */
	private void track(GuiGraphicsExtractor graphics, Row row, float fraction, int fill, int knobGlow, float hover) {
		float from = row.x + PAD + 4;
		float to = row.x + row.width - PAD;
		float y = row.y + row.height - 2.5f;
		float knob = from + (to - from) * Math.clamp(fraction, 0f, 1f);
		Glow.rounded(graphics, from, y - 0.75f, to, y + 0.75f, 0.75f, Theme.TRACK);
		Glow.lightAcross(graphics, from, y - 0.75f, knob, y + 0.75f, FxMath.fade(fill | 0xFF000000, 0.35f),
				fill | 0xFF000000);
		Glow.dot(graphics, knob, y, 6f + 2f * hover, FxMath.fade(knobGlow | 0xFF000000, 0.7f));
		Glow.disc(graphics, knob, y, 2f + 0.5f * hover, 0xFFFFFFFF);
	}

	private String fit(String text, int room) {
		return Glow.fit(font, text, room);
	}

	// ---------------------------------------------------------------------------------------
	// The mouse

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		layout(false);
		boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
		boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		if (!left && !right) {
			return false;
		}
		Row row = rowAt(event.x(), event.y());
		if (row == null) {
			if (panelAt(event.x(), event.y()) != null) {
				// On a panel, between its lines.
				return true;
			}
			// Not on the menu: perhaps on something of the heads-up display, to drag it.
			if (left) {
				for (Visual visual : Visuals.all()) {
					if (visual.on() && visual instanceof HudWidget widget && widget.contains(event.x(), event.y())) {
						movingWidget = widget;
						grabX = event.x() - widget.left();
						grabY = event.y() - widget.top();
						return true;
					}
				}
			}
			return false;
		}
		Visual visual = row.visual;
		Setting setting = row.setting;
		// The panel being handled comes to the front, so that what unfolds in it is not hidden by another.
		panels.remove(row.panel);
		panels.add(row.panel);
		switch (row.kind) {
			case TITLE -> {
				if (left) {
					movingPanel = row.panel;
					grabX = event.x() - row.panel.x;
					grabY = event.y() - row.panel.y;
				} else {
					row.panel.open = !row.panel.open;
					click();
				}
			}
			case VISUAL -> {
				if (left) {
					visual.set(!visual.on());
					Visuals.changed();
				} else if (!unfolded.remove(visual.id()) && visual.settings().stream().anyMatch(Setting::shown)) {
					unfolded.add(visual.id());
				}
				click();
			}
			case TOGGLE -> {
				Setting.Toggle toggle = (Setting.Toggle) setting;
				toggle.set(!toggle.on());
				Visuals.changed();
				click();
			}
			case SLIDER, COLOR_PART -> {
				if (left) {
					sliding = row;
					slide(event.x());
				}
			}
			case CHOICE -> {
				((Setting.Choice) setting).step(left);
				Visuals.changed();
				click();
			}
			case COLOR -> {
				if (!unfoldedColors.remove(setting)) {
					unfoldedColors.add(setting);
				}
				click();
			}
			case COLORS -> {
				click();
				minecraft.gui.setScreen(new GradientScreen(this,
						Component.translatable(PREFIX + "visual." + visual.id()).append(": ")
								.append(Component.translatable(PREFIX + "visual." + visual.id() + "." + setting.key())),
						(Setting.Colors) setting, Visuals::changed));
			}
			case ACTION -> {
				click();
				((Setting.Action) setting).run();
			}
		}
		return true;
	}

	/** Sets the slider being dragged to where the mouse is. */
	private void slide(double mouseX) {
		if (sliding == null) {
			return;
		}
		float from = sliding.x + PAD + 4;
		float to = sliding.x + sliding.width - PAD;
		float fraction = Math.clamp((float) (mouseX - from) / (to - from), 0f, 1f);
		if (sliding.setting instanceof Setting.Slider slider) {
			slider.setFraction(fraction);
		} else if (sliding.setting instanceof Setting.Color color) {
			color.setPart(sliding.part, fraction);
		}
		Visuals.changed();
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (movingPanel != null) {
			movingPanel.x = (int) Math.round(event.x() - grabX);
			movingPanel.y = (int) Math.round(event.y() - grabY);
			return true;
		}
		if (movingWidget != null) {
			movingWidget.moveTo(event.x() - grabX, event.y() - grabY, width, height);
			Visuals.changed();
			return true;
		}
		if (sliding != null) {
			slide(event.x());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		boolean was = movingPanel != null || movingWidget != null || sliding != null;
		movingPanel = null;
		movingWidget = null;
		sliding = null;
		return was;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		// A panel too long for the screen is rolled up and down.
		Panel panel = panelAt(mouseX, mouseY);
		if (panel == null) {
			return false;
		}
		panel.y += (int) Math.round(scrollY * ROW_HEIGHT * 2);
		return true;
	}

	private void click() {
		AbstractWidget.playButtonClickSound(minecraft.getSoundManager());
	}

	// ---------------------------------------------------------------------------------------
	// Coming and going

	@Override
	public void removed() {
		// Remember where the panels are, and whatever was changed.
		runtime.updateConfig(config -> {
			config.menu.put("layout", LAYOUT);
			for (Panel panel : panels) {
				config.menu.put("panel." + panel.category.key() + ".x", Integer.toString(panel.x));
				config.menu.put("panel." + panel.category.key() + ".y", Integer.toString(panel.y));
				config.menu.put("panel." + panel.category.key() + ".open", Boolean.toString(panel.open));
			}
		});
		Visuals.changed();
		Visuals.save();
		Ease.forget();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
