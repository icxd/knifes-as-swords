package io.github.icxd.knives_as_swords.screen;

import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.ModConfig;
import io.github.icxd.knives_as_swords.fx.CustomEffect;
import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.fx.FxStyle;
import io.github.icxd.knives_as_swords.fx.WeaponFx;
import io.github.icxd.knives_as_swords.render.Appearance;
import io.github.icxd.knives_as_swords.render.KnifeVisual;
import io.github.icxd.knives_as_swords.render.KnifeVisuals;
import io.github.icxd.knives_as_swords.ui.Backdrop;
import io.github.icxd.knives_as_swords.ui.Ease;
import io.github.icxd.knives_as_swords.ui.Glow;
import io.github.icxd.knives_as_swords.ui.GlowButton;
import io.github.icxd.knives_as_swords.ui.GlowCycle;
import io.github.icxd.knives_as_swords.ui.GlowSlider;
import io.github.icxd.knives_as_swords.ui.Theme;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Where a player puts together a look of their own for one sword's effects: how much of each
 * effect, how sparks behave, and the colours, as a gradient with as many colours as they like.
 *
 * <p>The knife in the preview slashes by itself, so every change can be seen for what it does.
 * Changes are saved a moment after they are made, and make the sword use this look.
 *
 * <p>The screen itself glows in the colours being put together: every slider has its place along
 * the gradient.
 */
public final class EffectScreen extends Screen {
	private static final String PREFIX = KnivesAsSwordsClient.MOD_ID + ".effect_screen.";
	private static final int MARGIN = 8;
	private static final int GAP = 4;
	private static final int BUTTON_HEIGHT = 20;
	/** Ticks a change has to rest before it is saved, so that dragging does not write the file over and over. */
	private static final int SAVE_AFTER_TICKS = 4;

	private final @Nullable Screen parent;
	private final KnifeRuntime runtime;
	private final KnifeVisuals visuals;
	private final Item sword;
	private final String swordId;

	/** The look being edited. */
	private CustomEffect effect;
	/** The controls for its colours. */
	private final GradientEditor colors;
	private FxStyle style;
	private boolean unsaved;
	private int restedTicks;
	private String copiedLook = "";

	private int previewX0, previewY0, previewX1, previewY1;
	private @Nullable KnifeVisual shown;
	private float yaw = 12f;
	private float pitch = 8f;
	private boolean turning;

	public EffectScreen(@Nullable Screen parent, Item sword) {
		super(Component.translatable(PREFIX + "title", new ItemStack(sword).getHoverName()));
		this.parent = parent;
		this.runtime = KnivesAsSwordsClient.runtime();
		this.visuals = KnivesAsSwordsClient.visuals();
		this.sword = sword;
		this.swordId = BuiltInRegistries.ITEM.getKey(sword).toString();

		ModConfig.SwordSetting setting = runtime.config().sword(swordId);
		if (setting.customEffect != null) {
			effect = setting.customEffect.copy();
		} else {
			// Nothing put together yet: start from the look the sword has now.
			effect = CustomEffect.from(FxStyle.byId(WeaponFx.effectId(swordId, setting.effect))
					.orElse(FxStyle.all().getFirst()));
		}
		style = effect.toStyle();
		colors = new GradientEditor(effect.gradient(), this::changed);
	}

	// ---------------------------------------------------------------------------------------
	// Building the screen

	@Override
	protected void init() {
		int top = MARGIN + font.lineHeight + GAP + 2;
		int contentWidth = width - MARGIN * 2;
		int leftWidth = Math.min((contentWidth - GAP) * 4 / 10, 180);

		// Left: how much of everything.
		int rows = 9;
		int rowPitch = Math.clamp((height - MARGIN - top + 2) / rows, 14, BUTTON_HEIGHT + 4);
		int rowHeight = Math.min(BUTTON_HEIGHT, rowPitch - 2);
		int row = 0;
		amount(MARGIN, top + rowPitch * row, leftWidth, rowHeight, row++, "trail", effect.trail,
				value -> effect.trail = value);
		amount(MARGIN, top + rowPitch * row, leftWidth, rowHeight, row++, "afterimages", effect.afterimages,
				value -> effect.afterimages = value);
		amount(MARGIN, top + rowPitch * row, leftWidth, rowHeight, row++, "bolts", effect.bolts,
				value -> effect.bolts = value);
		amount(MARGIN, top + rowPitch * row, leftWidth, rowHeight, row++, "sparks", effect.sparks,
				value -> effect.sparks = value);
		amount(MARGIN, top + rowPitch * row, leftWidth, rowHeight, row++, "glow", effect.glow, value -> effect.glow = value);
		amount(MARGIN, top + rowPitch * row, leftWidth, rowHeight, row++, "heat", effect.heat, value -> effect.heat = value);
		amount(MARGIN, top + rowPitch * row, leftWidth, rowHeight, row++, "raggedness", effect.raggedness,
				value -> effect.raggedness = value);
		addRenderableWidget(new ValueSlider(MARGIN, top + rowPitch * row, leftWidth, rowHeight, row++ / (rows - 1f), -1f,
				1f, effect.sparkDrift, EffectScreen::driftLabel, value -> {
					// A little play around the middle, so that "float" is easy to hit.
					effect.sparkDrift = Math.abs(value) < 0.05f ? 0f : value;
					changed();
				}));
		addRenderableWidget(new ValueSlider(MARGIN, top + rowPitch * row, leftWidth, rowHeight, row / (rows - 1f), 0f,
				CustomEffect.MAX_COLOR_CYCLE, effect.colorCycle, EffectScreen::cycleLabel, value -> {
					effect.colorCycle = value < 0.02f ? 0f : value;
					changed();
				}));

		// Right, from the top: the gradient, the colour picked in it, what to do with colours.
		int rightX = MARGIN + leftWidth + GAP;
		int rightWidth = width - MARGIN - rightX;
		colors.build(font, this::addRenderableWidget, rightX, top, rightWidth);
		// Any ready-made look can be the starting point.
		List<String> looks = new ArrayList<>(List.of(""));
		FxStyle.all().forEach(look -> looks.add(look.id()));
		addRenderableWidget(new GlowCycle<>(colors.spareX(), colors.spareY(), colors.spareWidth(), BUTTON_HEIGHT, null,
				looks, copiedLook, look -> look.isEmpty() ? text("copy")
						: text("copy.from", Component.translatable(KnivesAsSwordsClient.MOD_ID + ".effect." + look)),
				this::copyLook).accent(() -> accent(0.5f)));
		int y = top + GradientEditor.HEIGHT + GAP;

		// Below that: the knife, and the way out.
		int bottomRow = height - MARGIN - BUTTON_HEIGHT;
		previewX0 = rightX;
		previewY0 = y;
		previewX1 = width - MARGIN;
		previewY1 = bottomRow - GAP;
		addRenderableWidget(new GlowButton(rightX, bottomRow, rightWidth, BUTTON_HEIGHT, CommonComponents.GUI_DONE,
				this::onClose).accent(() -> accent(0.5f)));
	}

	/**
	 * A slider for how much of one effect there is.
	 *
	 * @param row which of the sliders it is, counted from the top
	 */
	private void amount(int x, int y, int width, int height, int row, String key, float value, Consumer<Float> set) {
		addRenderableWidget(new ValueSlider(x, y, width, height, row / 8f, 0f, 1f, value,
				shown -> text(key, Math.round(shown * 100f)), changedValue -> {
					set.accept(changedValue);
					changed();
				}));
	}

	/**
	 * The look's colour for something of this screen.
	 *
	 * @param along where along the gradient, 0 to 1
	 * @return the colour, fully opaque
	 */
	private int accent(float along) {
		return 0xFF000000 | style.color(Theme.seconds(), along);
	}

	private static Component driftLabel(float drift) {
		if (Math.abs(drift) < 0.05f) {
			return text("drift.float");
		}
		return text(drift < 0f ? "drift.fall" : "drift.rise", Math.round(Math.abs(drift) * 100f));
	}

	private static Component cycleLabel(float cycle) {
		if (cycle < 0.02f) {
			return text("cycle.off");
		}
		return text("cycle", String.format(java.util.Locale.ROOT, "%.2f", cycle));
	}

	private static Component text(String key, Object... arguments) {
		return Component.translatable(PREFIX + key, arguments);
	}

	// ---------------------------------------------------------------------------------------
	// Changing the look

	/** Something about the look has changed: show it, and save it soon. */
	private void changed() {
		effect.setGradient(colors.gradient());
		style = effect.toStyle();
		unsaved = true;
		restedTicks = 0;
	}

	private void save() {
		unsaved = false;
		CustomEffect copy = effect.copy();
		runtime.updateConfig(config -> {
			ModConfig.SwordSetting setting = config.sword(swordId).copy();
			// Having put a look together means wanting to see it.
			setting.effect = FxStyle.CUSTOM;
			setting.customEffect = copy;
			config.swords.put(swordId, setting);
		});
	}

	/** Makes the look a copy of a ready-made one, to go on from there. */
	private void copyLook(String look) {
		copiedLook = look;
		Optional<FxStyle> source = FxStyle.byId(look);
		if (source.isEmpty()) {
			return;
		}
		effect = CustomEffect.from(source.get());
		colors.set(effect.gradient());
		changed();
		// Every control shows something else now.
		rebuildWidgets();
	}

	@Override
	public void tick() {
		// Saved once the mouse has let go and a moment has passed, not on every step of a drag.
		if (unsaved && !isDragging() && ++restedTicks >= SAVE_AFTER_TICKS) {
			save();
		}
	}

	@Override
	public void onClose() {
		if (unsaved) {
			save();
		}
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------------------------------
	// Drawing

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			extractPanorama(graphics, partialTick);
		}
		extractBlurredBackground(graphics);
		Backdrop.draw(graphics, width, height, style.colors(), 1f);
		minecraft.gui.hud.extractDeferredSubtitles();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Ease.frame();
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		Glow.text(graphics, font, title.getString(), (width - font.width(title)) / 2f, MARGIN, 0xFFFFFFFF,
				FxMath.fade(accent(0f), 0.4f));
		// The knife stands on dark glass, lit from below in the colours being put together.
		Glow.pane(graphics, previewX0, previewY0, previewX1, previewY1, 6f, Theme.GLASS, FxMath.fade(accent(0f), 0.45f),
				0.2f);
		int low = FxMath.fade(accent(0f), 0.22f);
		int lowEnd = FxMath.fade(accent(1f), 0.22f);
		Glow.light(graphics, previewX0 + 3, (previewY0 + previewY1) / 2f, previewX1 - 3, previewY1 - 2, low & 0xFFFFFF,
				lowEnd & 0xFFFFFF, low, lowEnd);

		// The knife as the sword is shown right now; only its effects are this screen's business.
		Optional<Appearance> applied = visuals.appearanceFor(sword);
		int centerX = (previewX0 + previewX1) / 2;
		if (applied.isEmpty()) {
			graphics.textWithWordWrap(font, text("no_knife"), previewX0 + GAP + 3, previewY0 + GAP + 3,
					previewX1 - previewX0 - GAP * 2 - 6, Theme.DIM, false);
			return;
		}
		KnifeVisual visual = visuals.get(applied.get());
		if (visual != null) {
			shown = visual;
		}
		if (shown != null && previewX1 - previewX0 > 8 && previewY1 - previewY0 > 8) {
			graphics.guiRenderState.addPicturesInPictureState(new KnifePreviewState(shown, style, true, yaw, pitch,
					previewX0, previewY0, previewX1, previewY1, null));
		} else if (shown == null) {
			graphics.text(font, text("loading"), centerX - font.width(text("loading")) / 2, (previewY0 + previewY1) / 2,
					Theme.DIM, false);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.x() >= previewX0 && event.x() < previewX1 && event.y() >= previewY0 && event.y() < previewY1) {
			turning = true;
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (turning) {
			yaw = (yaw + (float) dx * 1.2f) % 360f;
			pitch = Math.clamp(pitch + (float) dy * 1.2f, -85f, 85f);
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (turning) {
			turning = false;
			return true;
		}
		return super.mouseReleased(event);
	}

	// ---------------------------------------------------------------------------------------
	// Widgets

	/** A slider for one number of the look. */
	private final class ValueSlider extends GlowSlider {
		/** Which of the look's colours the slider glows in: where along the gradient, 0 to 1. */
		private final float along;
		private final float min;
		private final float max;
		private final Function<Float, Component> label;
		private final Consumer<Float> onChange;

		ValueSlider(int x, int y, int width, int height, float along, float min, float max, float initial,
				Function<Float, Component> label, Consumer<Float> onChange) {
			super(x, y, width, height, CommonComponents.EMPTY, max > min ? Math.clamp((initial - min) / (max - min), 0f, 1f) : 0.0);
			this.along = along;
			this.min = min;
			this.max = max;
			this.label = label;
			this.onChange = onChange;
			updateMessage();
		}

		float current() {
			return min + (float) value * (max - min);
		}

		@Override
		protected int accent() {
			return EffectScreen.this.accent(along);
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
}
