package io.github.icxd.knives_as_swords.screen;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.fx.Gradient;
import io.github.icxd.knives_as_swords.ui.Ease;
import io.github.icxd.knives_as_swords.ui.Glow;
import io.github.icxd.knives_as_swords.ui.GlowButton;
import io.github.icxd.knives_as_swords.ui.GlowEditBox;
import io.github.icxd.knives_as_swords.ui.GlowSlider;
import io.github.icxd.knives_as_swords.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The controls for putting a gradient together, to be placed into any screen: the gradient as a
 * bar with a marker under it for every colour, sliders for the hue, saturation and brightness of
 * the colour picked, a box to type it as {@code #RRGGBB}, and buttons to add and remove colours.
 *
 * <p>Clicking a marker picks its colour, dragging moves it, right-clicking removes it; clicking
 * the bar anywhere else adds a colour there.
 */
final class GradientEditor {
	private static final String PREFIX = KnivesAsSwordsClient.MOD_ID + ".effect_screen.";
	private static final int GAP = 4;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BAR_HEIGHT = 26;

	/** How much room {@link #build} takes, from top to bottom. */
	static final int HEIGHT = BAR_HEIGHT + GAP + BUTTON_HEIGHT + 2 + BUTTON_HEIGHT + GAP + BUTTON_HEIGHT;

	private final Runnable onChange;
	/** The colours in the order they were made, so that one being dragged past another stays the same one. */
	private final List<Gradient.Stop> stops = new ArrayList<>();
	private int selected;
	private Gradient gradient;

	private @Nullable ValueSlider hueSlider;
	private @Nullable ValueSlider saturationSlider;
	private @Nullable ValueSlider brightnessSlider;
	private @Nullable EditBox hexBox;
	private @Nullable GlowButton removeButton;
	private @Nullable GlowButton addButton;
	/** Set while controls are being brought in line with the gradient, as opposed to moved by the player. */
	private boolean syncing;
	private int spareX;
	private int spareY;
	private int spareWidth;

	/**
	 * @param onChange called whenever the player has changed the gradient
	 */
	GradientEditor(Gradient initial, Runnable onChange) {
		this.onChange = onChange;
		this.gradient = initial;
		stops.addAll(initial.stops());
	}

	Gradient gradient() {
		return gradient;
	}

	/** Replaces the whole gradient, as when a ready-made one is copied. Does not count as a change by the player. */
	void set(Gradient replacement) {
		stops.clear();
		stops.addAll(replacement.stops());
		selected = 0;
		gradient = Gradient.of(stops);
		showSelectedColor();
	}

	/**
	 * Puts the controls into a screen.
	 *
	 * @param add how the screen takes a widget
	 */
	void build(Font font, Consumer<AbstractWidget> add, int x, int y, int width) {
		add.accept(new GradientBar(x, y, width, BAR_HEIGHT));
		y += BAR_HEIGHT + GAP;

		int hexWidth = 54;
		// Everything about the picked colour glows in that colour.
		IntSupplier picked = this::pickedColor;
		hueSlider = new ValueSlider(x, y, width - hexWidth - GAP, BUTTON_HEIGHT, 0f,
				value -> text("hue", Math.round(value * 360f)), value -> pickColor(), picked);
		add.accept(hueSlider);
		hexBox = new GlowEditBox(font, x + width - hexWidth, y, hexWidth, BUTTON_HEIGHT, text("hex"), picked);
		hexBox.setMaxLength(7);
		hexBox.setResponder(this::typeColor);
		add.accept(hexBox);
		y += BUTTON_HEIGHT + 2;

		int half = (width - GAP) / 2;
		saturationSlider = new ValueSlider(x, y, half, BUTTON_HEIGHT, 0f,
				value -> text("saturation", Math.round(value * 100f)), value -> pickColor(), picked);
		add.accept(saturationSlider);
		brightnessSlider = new ValueSlider(x + half + GAP, y, width - half - GAP, BUTTON_HEIGHT, 0f,
				value -> text("brightness", Math.round(value * 100f)), value -> pickColor(), picked);
		add.accept(brightnessSlider);
		y += BUTTON_HEIGHT + GAP;

		int third = (width - GAP * 2) / 3;
		addButton = new GlowButton(x, y, third, BUTTON_HEIGHT, text("add"), this::addColor).accent(picked);
		add.accept(addButton);
		removeButton = new GlowButton(x + third + GAP, y, third, BUTTON_HEIGHT, text("remove"),
				() -> removeColor(selected)).accent(picked);
		add.accept(removeButton);
		spareX = x + (third + GAP) * 2;
		spareY = y;
		spareWidth = width - (third + GAP) * 2;

		showSelectedColor();
	}

	/** Next to the add and remove buttons there is room for one more button of the screen's own. */
	int spareX() {
		return spareX;
	}

	int spareY() {
		return spareY;
	}

	int spareWidth() {
		return spareWidth;
	}

	private static Component text(String key, Object... arguments) {
		return Component.translatable(PREFIX + key, arguments);
	}

	/** The colour picked in the gradient, fully opaque; never so dark that its light could not be seen. */
	private int pickedColor() {
		int rgb = stops.get(Math.clamp(selected, 0, stops.size() - 1)).rgb();
		float[] hsv = Gradient.toHsv(rgb);
		return 0xFF000000 | (hsv[2] < 0.35f ? Gradient.fromHsv(hsv[0], hsv[1], 0.35f) : rgb);
	}

	private void changed() {
		gradient = Gradient.of(stops);
		onChange.run();
	}

	private void select(int index) {
		selected = Math.clamp(index, 0, stops.size() - 1);
		showSelectedColor();
	}

	/** Brings the colour controls in line with the selected colour. */
	private void showSelectedColor() {
		if (hueSlider == null || saturationSlider == null || brightnessSlider == null || hexBox == null) {
			return;
		}
		int rgb = stops.get(selected).rgb();
		float[] hsv = Gradient.toHsv(rgb);
		syncing = true;
		// Grey has no hue and black no saturation; keep what the sliders say rather than jumping to 0.
		if (hsv[1] > 0f && hsv[2] > 0f) {
			hueSlider.show(hsv[0]);
		}
		if (hsv[2] > 0f) {
			saturationSlider.show(hsv[1]);
		}
		brightnessSlider.show(hsv[2]);
		if (Gradient.parse(hexBox.getValue()) != rgb) {
			hexBox.setValue(Gradient.hex(rgb));
		}
		syncing = false;
		if (removeButton != null && addButton != null) {
			removeButton.active = stops.size() > 1;
			addButton.active = stops.size() < Gradient.MAX_STOPS;
		}
	}

	/** The colour sliders were moved: the selected colour becomes what they say. */
	private void pickColor() {
		if (syncing || hueSlider == null || saturationSlider == null || brightnessSlider == null || hexBox == null) {
			return;
		}
		int rgb = Gradient.fromHsv(hueSlider.current(), saturationSlider.current(), brightnessSlider.current());
		stops.set(selected, new Gradient.Stop(stops.get(selected).at(), rgb));
		syncing = true;
		hexBox.setValue(Gradient.hex(rgb));
		syncing = false;
		changed();
	}

	/** Something was typed into the colour box: once it is a whole colour, it is the selected one. */
	private void typeColor(String typed) {
		int rgb = Gradient.parse(typed);
		if (syncing || rgb < 0 || rgb == stops.get(selected).rgb()) {
			return;
		}
		stops.set(selected, new Gradient.Stop(stops.get(selected).at(), rgb));
		showSelectedColor();
		changed();
	}

	/** Adds a colour in the middle of the widest stretch without one, in the colour the gradient has there. */
	private void addColor() {
		if (stops.size() >= Gradient.MAX_STOPS) {
			return;
		}
		List<Gradient.Stop> sorted = gradient.stops();
		float best = 0.5f;
		float widest = -1f;
		float before = 0f;
		for (int i = 0; i <= sorted.size(); i++) {
			float after = i < sorted.size() ? sorted.get(i).at() : 1f;
			// The stretches before the first colour and after the last one count from the gradient's ends.
			float middle = i == 0 ? 0f : i == sorted.size() ? 1f : (before + after) / 2f;
			float gap = i == 0 || i == sorted.size() ? (after - before) * 2f : after - before;
			if (gap > widest + 1e-6f) {
				widest = gap;
				best = middle;
			}
			before = after;
		}
		addColorAt(best);
	}

	private void addColorAt(float at) {
		stops.add(new Gradient.Stop(at, gradient.at(at) & 0xFFFFFF));
		changed();
		select(stops.size() - 1);
	}

	private void removeColor(int index) {
		if (stops.size() <= 1 || index < 0 || index >= stops.size()) {
			return;
		}
		stops.remove(index);
		changed();
		select(Math.min(selected > index ? selected - 1 : selected, stops.size() - 1));
	}

	/** A slider for one part of a colour, from 0 to 1. */
	private static final class ValueSlider extends GlowSlider {
		private final Function<Float, Component> label;
		private final Consumer<Float> onChange;
		private final IntSupplier accent;

		ValueSlider(int x, int y, int width, int height, float initial, Function<Float, Component> label,
				Consumer<Float> onChange, IntSupplier accent) {
			super(x, y, width, height, CommonComponents.EMPTY, Math.clamp(initial, 0f, 1f));
			this.label = label;
			this.onChange = onChange;
			this.accent = accent;
			updateMessage();
		}

		@Override
		protected int accent() {
			return accent.getAsInt();
		}

		float current() {
			return (float) value;
		}

		/** Shows a number without that counting as the player having picked it. */
		void show(float shown) {
			value = Math.clamp(shown, 0f, 1f);
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(label.apply(current()));
		}

		@Override
		protected void applyValue() {
			onChange.accept(current());
		}
	}

	/** The gradient as a bar, with a marker under it for every colour. */
	private final class GradientBar extends AbstractWidget {
		/** Height of the row of markers under the bar, and how far the bar stays clear of the widget's sides. */
		private static final int MARKERS = 10;
		private static final int INSET = 4;
		/** How close to a marker's middle a click has to be to mean that marker, in pixels. */
		private static final int GRAB = 5;

		private boolean moving;
		/** How much the mouse is on the bar, gliding between 0 and 1. */
		private float lit;

		GradientBar(int x, int y, int width, int height) {
			super(x, y, width, height, text("colors"));
			setTooltip(Tooltip.create(text("colors.hint")));
		}

		private int span() {
			return Math.max(width - INSET * 2 - 1, 1);
		}

		private int markerX(float at) {
			return getX() + INSET + Math.round(at * span());
		}

		private float placeAt(double mouseX) {
			return Math.clamp((float) (mouseX - getX() - INSET) / span(), 0f, 1f);
		}

		/** The marker a click at this x means, or -1. Where markers overlap, the selected one wins. */
		private int markerAt(double mouseX) {
			int best = -1;
			double nearest = GRAB + 0.5;
			for (int i = 0; i < stops.size(); i++) {
				double distance = Math.abs(mouseX - markerX(stops.get(i).at()));
				if (distance < nearest || (distance == nearest && i == selected)) {
					nearest = distance;
					best = i;
				}
			}
			return best;
		}

		@Override
		protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			float barTop = getY() + 2f;
			float barBottom = getY() + height - MARKERS - 1f;
			float left = getX() + INSET;
			float right = left + span() + 1f;
			lit = Ease.step(lit, isHovered() || isFocused() ? 1f : 0f, 20f, Ease.seconds());

			// The gradient shines: every stretch of the bar throws its own colour around it.
			int pieces = Math.clamp(span() / 6, 8, 48);
			float radius = (barBottom - barTop) * (1.15f + 0.25f * lit);
			for (int piece = 0; piece < pieces; piece++) {
				float at = (piece + 0.5f) / pieces;
				Glow.dot(graphics, left + (right - left) * at, (barTop + barBottom) / 2f, radius,
						FxMath.fade(gradient.at(at) | 0xFF000000, 0.2f + 0.1f * lit));
			}
			Glow.rounded(graphics, left - 1f, barTop - 1f, right + 1f, barBottom + 1f, 4f,
					FxMath.mix(0xFF30303C, 0xFFFFFFFF, 0.25f + 0.5f * lit));
			for (int piece = 0; piece < pieces; piece++) {
				float from = left + (right - left) * piece / pieces;
				float to = left + (right - left) * (piece + 1) / pieces;
				int start = gradient.at(piece / (float) pieces) | 0xFF000000;
				int end = gradient.at((piece + 1) / (float) pieces) | 0xFF000000;
				if (piece == 0) {
					// Round at the bar's own ends only.
					Glow.rounded(graphics, from, barTop, to + 3f, barBottom, 3f, start, end);
				} else if (piece == pieces - 1) {
					Glow.rounded(graphics, from - 3f, barTop, to, barBottom, 3f, start, end);
				} else {
					Glow.across(graphics, from, barTop, to, barBottom, start, end);
				}
			}

			float markerY = getY() + height - MARKERS / 2f;
			for (int pass = 0; pass < 2; pass++) {
				// The selected marker last, so that it lies on top of its neighbours.
				for (int i = 0; i < stops.size(); i++) {
					if ((i == selected) != (pass == 1)) {
						continue;
					}
					float x = markerX(stops.get(i).at()) + 0.5f;
					int color = 0xFF000000 | stops.get(i).rgb();
					if (i == selected) {
						Glow.dot(graphics, x, markerY, 11f, FxMath.fade(color, 0.7f));
						Glow.fill(graphics, x - 0.5f, barBottom, x + 0.5f, markerY, 0xFFFFFFFF);
					}
					Glow.disc(graphics, x, markerY, i == selected ? 4.5f : 3.75f, i == selected ? 0xFFFFFFFF : 0xFF3A3A48);
					Glow.disc(graphics, x, markerY, i == selected ? 3.25f : 2.75f, color);
				}
			}
		}

		@Override
		protected boolean isValidClickButton(MouseButtonInfo buttonInfo) {
			return buttonInfo.button() == InputConstants.MOUSE_BUTTON_LEFT
					|| buttonInfo.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			int marker = markerAt(event.x());
			if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
				removeColor(marker);
				return;
			}
			if (marker >= 0) {
				select(marker);
			} else if (stops.size() < Gradient.MAX_STOPS) {
				addColorAt(placeAt(event.x()));
			} else {
				return;
			}
			moving = true;
		}

		@Override
		protected void onDrag(MouseButtonEvent event, double dx, double dy) {
			if (moving && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
				stops.set(selected, new Gradient.Stop(placeAt(event.x()), stops.get(selected).rgb()));
				changed();
			}
		}

		@Override
		public void onRelease(MouseButtonEvent event) {
			moving = false;
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}
}
