package io.github.icxd.knives_as_swords.screen;

import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.KnifeRuntime.ExtractorStatus;
import io.github.icxd.knives_as_swords.KnifeRuntime.State;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.ModConfig;
import io.github.icxd.knives_as_swords.cs2.FinishCatalog;
import io.github.icxd.knives_as_swords.cs2.KnifeModel;
import io.github.icxd.knives_as_swords.extract.ExtractorRelease;
import io.github.icxd.knives_as_swords.fx.CustomEffect;
import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.fx.FxStyle;
import io.github.icxd.knives_as_swords.fx.Gradient;
import io.github.icxd.knives_as_swords.fx.WeaponFx;
import io.github.icxd.knives_as_swords.render.Appearance;
import io.github.icxd.knives_as_swords.render.KnifeReplacement;
import io.github.icxd.knives_as_swords.render.KnifeVisual;
import io.github.icxd.knives_as_swords.render.KnifeVisuals;
import io.github.icxd.knives_as_swords.render.SwordDefaults;
import io.github.icxd.knives_as_swords.ui.Backdrop;
import io.github.icxd.knives_as_swords.ui.Ease;
import io.github.icxd.knives_as_swords.ui.Glow;
import io.github.icxd.knives_as_swords.ui.GlowButton;
import io.github.icxd.knives_as_swords.ui.GlowCycle;
import io.github.icxd.knives_as_swords.ui.GlowSlider;
import io.github.icxd.knives_as_swords.ui.GlowSwitch;
import io.github.icxd.knives_as_swords.ui.Theme;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The settings screen: for each kind of sword, which knife it is shown as, with which finish,
 * pattern, wear and effects. Every change applies at once and is saved, so the preview is simply the
 * knife as it will look in the world. The one exception is a slider while it is being dragged:
 * its value applies when it is let go, because every new look has to be painted first.
 *
 * <p>The screen is drawn in the look of the mod's menus (see {@link Glow}) and glows in the
 * colours of the chosen sword's effects.
 */
public final class KnivesScreen extends Screen {
	private static final String PREFIX = KnivesAsSwordsClient.MOD_ID + ".screen.";
	private static final int MARGIN = 8;
	private static final int GAP = 4;
	private static final int ROW_HEIGHT = 14;
	private static final int BUTTON_HEIGHT = 20;
	private static final int SWORD_BUTTON = 22;
	/**
	 * Ticks a slider moved with the keyboard has to rest before its value is applied. (Moved with
	 * the mouse, it applies on release instead.)
	 */
	private static final int SLIDER_DELAY_TICKS = 10;
	/** Wear above which the game calls a finish by the next worse name. */
	private static final float[] WEAR_LIMITS = {0.07f, 0.15f, 0.38f, 0.45f};
	private static final String[] WEAR_NAMES = {"factory_new", "minimal_wear", "field_tested", "well_worn",
			"battle_scarred"};

	/** What the screen can show, depending on how far the mod got with the game files. */
	private enum Mode {
		LOOKING, NO_GAME, NEEDS_CONVERTER, NO_CONVERTER, READY
	}

	private final @Nullable Screen parent;
	private final KnifeRuntime runtime;
	private final KnifeVisuals visuals;

	private Mode mode = Mode.LOOKING;
	private int seenScanVersion = -1;
	private List<Item> swords = List.of();
	private @Nullable Item sword;
	private ModConfig.SwordSetting setting = new ModConfig.SwordSetting();
	private @Nullable ChoiceList finishList;
	private @Nullable PatternSlider patternSlider;
	private @Nullable WearSlider wearSlider;
	private int applyCountdown = -1;
	private int ticksSinceModeCheck;

	private int previewX0, previewY0, previewX1, previewY1;
	private @Nullable KnifeVisual shown;
	private float yaw = 25f;
	private float pitch = 10f;
	private boolean turning;
	private long lastFrameNanos;
	private long lastTurnedNanos;

	public KnivesScreen(@Nullable Screen parent) {
		super(Component.translatable(PREFIX + "title"));
		this.parent = parent;
		this.runtime = KnivesAsSwordsClient.runtime();
		this.visuals = KnivesAsSwordsClient.visuals();
	}

	// ---------------------------------------------------------------------------------------
	// Building the screen

	@Override
	protected void init() {
		mode = currentMode();
		seenScanVersion = runtime.scanVersion();
		finishList = null;
		patternSlider = null;
		wearSlider = null;

		int bottomRow = height - MARGIN - BUTTON_HEIGHT;
		if (mode != Mode.READY) {
			int centerX = width / 2;
			if (mode == Mode.NO_GAME) {
				addRenderableWidget(new GlowButton(centerX - 100, height / 2 + 20, 200, BUTTON_HEIGHT, text("rescan"), () -> {
					runtime.scan();
					mode = Mode.LOOKING;
					rebuildWidgets();
				}));
			} else if (mode == Mode.NEEDS_CONVERTER) {
				addRenderableWidget(new GlowButton(centerX - 100, height / 2 + 20, 200, BUTTON_HEIGHT, text("download"),
						this::askForDownload));
			}
			addRenderableWidget(new GlowButton(centerX - 100, bottomRow, 200, BUTTON_HEIGHT, CommonComponents.GUI_DONE,
					this::onClose));
			return;
		}

		swords = BuiltInRegistries.ITEM.stream().filter(item -> KnifeReplacement.isSword(new ItemStack(item))).toList();
		if (swords.isEmpty()) {
			mode = Mode.NO_GAME;
			return;
		}
		if (sword == null || !swords.contains(sword)) {
			// Start with the sword in hand, if there is one.
			Item held = minecraft.player != null ? minecraft.player.getMainHandItem().getItem() : null;
			sword = swords.contains(held) ? held : swords.getFirst();
		}
		setting = runtime.config().sword(id(sword)).copy();
		State state = runtime.state().orElseThrow();

		// Top: one button per kind of sword.
		int swordsWidth = swords.size() * (SWORD_BUTTON + 2) - 2;
		int swordsX = (width - swordsWidth) / 2;
		int swordsY = MARGIN + font.lineHeight + GAP;
		for (int i = 0; i < swords.size(); i++) {
			addRenderableWidget(new SwordButton(swordsX + i * (SWORD_BUTTON + 2), swordsY, swords.get(i)));
		}

		// Bottom: two rows of controls.
		int sliderRow = bottomRow - BUTTON_HEIGHT - GAP;
		int contentWidth = width - MARGIN * 2;
		int quarter = (contentWidth - GAP * 3) / 4;
		int third = (contentWidth - GAP * 2) / 3;
		patternSlider = addRenderableWidget(new PatternSlider(MARGIN, sliderRow, third, BUTTON_HEIGHT));
		wearSlider = addRenderableWidget(new WearSlider(MARGIN + third + GAP, sliderRow, third, BUTTON_HEIGHT));
		// The look of the effects: whatever suits this sword, none at all, a ready-made one, or the
		// player's own, which the small button next to it opens for editing.
		List<String> effects = new ArrayList<>(List.of("", FxStyle.NONE));
		FxStyle.all().forEach(style -> effects.add(style.id()));
		effects.add(FxStyle.CUSTOM);
		String swordId = id(sword);
		int effectX = MARGIN + (third + GAP) * 2;
		int effectWidth = contentWidth - (third + GAP) * 2 - BUTTON_HEIGHT - 2;
		addRenderableWidget(new GlowCycle<>(effectX, sliderRow, effectWidth, BUTTON_HEIGHT, text("effect"), effects,
				effects.contains(setting.effect) ? setting.effect : "", effect -> effectLabel(swordId, effect), value -> {
					if (value.equals(FxStyle.CUSTOM) && setting.customEffect == null) {
						// Nothing put together yet: start from the look the sword had until now.
						setting.customEffect = CustomEffect.from(FxStyle.byId(WeaponFx.effectId(swordId, setting.effect))
								.orElse(FxStyle.all().getFirst()));
					}
					setting.effect = value;
					apply();
				}).accent(this::accent));
		GlowButton edit = addRenderableWidget(new GlowButton(effectX + effectWidth + 2, sliderRow, BUTTON_HEIGHT,
				BUTTON_HEIGHT, Component.literal("..."), () -> {
					if (applyCountdown >= 0) {
						apply();
					}
					minecraft.gui.setScreen(new EffectScreen(this, sword));
				}).accent(this::accent));
		edit.setTooltip(Tooltip.create(text("effect.edit")));
		addRenderableWidget(new GlowSwitch(MARGIN, bottomRow, quarter, BUTTON_HEIGHT, text("show_as_knife"),
				setting.enabled, value -> {
					setting.enabled = value;
					apply();
				}).accent(this::accent));
		addRenderableWidget(new GlowSwitch(MARGIN + quarter + GAP, bottomRow, quarter, BUTTON_HEIGHT, text("animations"),
				runtime.config().firstPersonAnimations,
				value -> runtime.updateConfig(config -> config.firstPersonAnimations = value)).accent(this::accent));
		addRenderableWidget(new GlowSwitch(MARGIN + (quarter + GAP) * 2, bottomRow, quarter, BUTTON_HEIGHT, text("shine"),
				runtime.config().shine, value -> runtime.updateConfig(config -> config.shine = value))
				.accent(this::accent));
		addRenderableWidget(new GlowButton(MARGIN + (quarter + GAP) * 3, bottomRow, contentWidth - (quarter + GAP) * 3,
				BUTTON_HEIGHT, CommonComponents.GUI_DONE, this::onClose).accent(this::accent));

		// Middle: knives, finishes, preview.
		int listTop = swordsY + SWORD_BUTTON + GAP + font.lineHeight + 4;
		int listHeight = sliderRow - GAP - listTop;
		int listWidth = Math.min((contentWidth - GAP * 2) * 3 / 10, 150);
		ChoiceList knifeList = addRenderableWidget(new ChoiceList(MARGIN, listTop, listWidth, listHeight, knife -> {
			setting.knife = knife;
			// A finish made for one knife does not exist for another.
			if (state.finishes().finish(effectiveKnife(state), setting.finish).isEmpty()) {
				setting.finish = "";
			}
			fillFinishes(state);
			apply();
		}));
		finishList = addRenderableWidget(new ChoiceList(MARGIN + listWidth + GAP, listTop, listWidth, listHeight,
				finish -> {
					setting.finish = finish;
					setting.wear = finish(state).map(chosen -> chosen.clampWear(setting.wear)).orElse(setting.wear);
					syncSliders(state);
					apply();
				}));
		previewX0 = MARGIN + (listWidth + GAP) * 2;
		previewY0 = listTop;
		previewX1 = width - MARGIN;
		previewY1 = listTop + listHeight;

		List<Choice> knives = new ArrayList<>();
		String defaultKnife = SwordDefaults.knifeFor(BuiltInRegistries.ITEM.getKey(sword), state);
		knives.add(new Choice("", Component.translatable(PREFIX + "default_knife",
				state.finishes().knifeName(defaultKnife))));
		state.knives().stream()
				.sorted(Comparator.comparing(knife -> state.finishes().knifeName(knife.id()).toLowerCase(Locale.ROOT)))
				.forEach(knife -> knives.add(new Choice(knife.id(), knifeLabel(state, knife))));
		knifeList.setChoices(knives, state.knife(setting.knife).isPresent() ? setting.knife : "");
		fillFinishes(state);
	}

	private Component knifeLabel(State state, KnifeModel knife) {
		String name = state.finishes().knifeName(knife.id());
		// The two starter knives share one name in the game.
		long sameName = state.knives().stream().filter(other -> state.finishes().knifeName(other.id()).equals(name))
				.count();
		return Component.literal(sameName > 1 ? name + " (" + knife.id() + ")" : name);
	}

	private static Component effectLabel(String sword, String effect) {
		if (effect.isEmpty()) {
			return text("effect.default", effectLabel(sword, WeaponFx.effectId(sword, "")));
		}
		return Component.translatable(KnivesAsSwordsClient.MOD_ID + ".effect." + effect);
	}

	private void fillFinishes(State state) {
		if (finishList == null) {
			return;
		}
		List<Choice> finishes = new ArrayList<>();
		finishes.add(new Choice("", text("no_finish")));
		for (FinishCatalog.Finish finish : state.finishes().finishes(effectiveKnife(state))) {
			finishes.add(new Choice(finish.name(), Component.literal(finish.displayName())));
		}
		finishList.setChoices(finishes, finish(state).isPresent() ? setting.finish : "");
		syncSliders(state);
	}

	private void syncSliders(State state) {
		if (patternSlider != null && wearSlider != null) {
			Optional<FinishCatalog.Finish> finish = finish(state);
			patternSlider.sync(finish.isPresent());
			wearSlider.sync(finish.orElse(null));
		}
	}

	private String effectiveKnife(State state) {
		if (!setting.knife.isEmpty() && state.knife(setting.knife).isPresent()) {
			return setting.knife;
		}
		return SwordDefaults.knifeFor(BuiltInRegistries.ITEM.getKey(sword), state);
	}

	private Optional<FinishCatalog.Finish> finish(State state) {
		return state.finishes().finish(effectiveKnife(state), setting.finish);
	}

	/** The screen glows in the colours of the chosen sword's effects, or in the menus' own if it has none. */
	private Gradient colors() {
		return colors(sword);
	}

	private static Gradient colors(@Nullable Item item) {
		FxStyle style = item != null ? WeaponFx.styleFor(item) : null;
		return style != null ? style.colors() : Theme.ACCENT;
	}

	private int accent() {
		return Theme.now(colors(), 0f);
	}

	private Mode currentMode() {
		Optional<State> state = runtime.state();
		if (state.isEmpty()) {
			return Mode.LOOKING;
		}
		if (state.get().install().isEmpty() || state.get().knives().isEmpty()) {
			return Mode.NO_GAME;
		}
		return switch (runtime.extractorStatus()) {
			case NEEDS_CONSENT -> Mode.NEEDS_CONVERTER;
			case UNSUPPORTED_PLATFORM -> Mode.NO_CONVERTER;
			default -> Mode.READY;
		};
	}

	/** Nothing is downloaded without the player saying so on this prompt. */
	private void askForDownload() {
		String consent = KnivesAsSwordsClient.MOD_ID + ".consent.";
		minecraft.gui.setScreen(new ConfirmScreen(accepted -> {
			if (accepted) {
				runtime.allowExtractorDownload();
			}
			minecraft.gui.setScreen(this);
		}, Component.translatable(consent + "title"),
				Component.translatable(consent + "message", ExtractorRelease.VERSION),
				Component.translatable(consent + "accept"), Component.translatable(consent + "decline")));
	}

	// ---------------------------------------------------------------------------------------
	// Applying changes

	private static String id(Item item) {
		return BuiltInRegistries.ITEM.getKey(item).toString();
	}

	/** Makes the current choices the sword's look, and saves them. */
	private void apply() {
		applyCountdown = -1;
		if (sword != null) {
			String item = id(sword);
			ModConfig.SwordSetting copy = setting.copy();
			runtime.updateConfig(config -> config.swords.put(item, copy));
		}
	}

	private void applySoon() {
		applyCountdown = SLIDER_DELAY_TICKS;
	}

	/**
	 * A slider that changes how the knife looks. While the mouse holds it nothing is applied;
	 * letting go applies where it ended up. Keyboard steps apply after a short rest.
	 */
	private abstract class LookSlider extends GlowSlider {
		private boolean held;
		private boolean changedWhileHeld;

		LookSlider(int x, int y, int width, int height, double value) {
			super(x, y, width, height, CommonComponents.EMPTY, value);
		}

		@Override
		protected int accent() {
			return KnivesScreen.this.accent();
		}

		/** Called when the slider's value has changed what {@link #setting} says. */
		protected final void changed() {
			if (held) {
				changedWhileHeld = true;
			} else {
				applySoon();
			}
		}

		@Override
		public void onClick(MouseButtonEvent event, boolean doubleClick) {
			held = true;
			super.onClick(event, doubleClick);
		}

		@Override
		public void onRelease(MouseButtonEvent event) {
			super.onRelease(event);
			held = false;
			if (changedWhileHeld) {
				changedWhileHeld = false;
				apply();
			}
		}
	}

	@Override
	public void tick() {
		if (applyCountdown >= 0 && --applyCountdown < 0) {
			apply();
		}
		// The game may be found, or the converter become available, while the screen is open.
		// Looked at once a second only, because finding out reads files.
		boolean modeChanged = false;
		if (++ticksSinceModeCheck >= 20) {
			ticksSinceModeCheck = 0;
			modeChanged = currentMode() != mode;
		}
		if (runtime.scanVersion() != seenScanVersion || modeChanged) {
			rebuildWidgets();
		}
	}

	@Override
	public void onClose() {
		if (applyCountdown >= 0) {
			apply();
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
		Backdrop.draw(graphics, width, height, colors(), 1f);
		minecraft.gui.hud.extractDeferredSubtitles();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Ease.frame();
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		int accent = accent();
		Glow.text(graphics, font, title.getString(), (width - font.width(title)) / 2f, MARGIN, 0xFFFFFFFF,
				FxMath.fade(accent, 0.4f));

		if (mode != Mode.READY) {
			Component message = text(switch (mode) {
				case LOOKING -> "looking";
				case NO_GAME -> "no_game";
				case NEEDS_CONVERTER -> "needs_converter";
				default -> "no_converter";
			});
			int textWidth = Math.min(width - MARGIN * 4, 320);
			int textHeight = font.split(message, textWidth).size() * font.lineHeight;
			int x = (width - textWidth) / 2;
			int y = height / 2 + 2 - textHeight;
			Glow.pane(graphics, x - 10, y - 8, x + textWidth + 10, y + textHeight + 7, 6f, Theme.GLASS,
					FxMath.fade(accent, 0.5f), 0.25f);
			graphics.textWithWordWrap(font, message, x, y, textWidth, Theme.TEXT, false);
			return;
		}

		int labelY = previewY0 - font.lineHeight - 3;
		graphics.text(font, text("knife"), MARGIN + 2, labelY, Theme.DIM, false);
		if (finishList != null) {
			graphics.text(font, text("finish"), finishList.getX() + 2, labelY, Theme.DIM, false);
		}
		if (sword != null) {
			graphics.text(font, new ItemStack(sword).getHoverName(), previewX0 + 2, labelY, Theme.DIM, false);
		}
		// The knife stands on dark glass, lit from below in its effects' colours.
		Glow.pane(graphics, previewX0, previewY0, previewX1, previewY1, 6f, Theme.GLASS, FxMath.fade(accent, 0.45f), 0.2f);
		int low = FxMath.fade(accent, 0.22f);
		int lowEnd = FxMath.fade(Theme.now(colors(), 0.5f), 0.22f);
		Glow.light(graphics, previewX0 + 3, (previewY0 + previewY1) / 2f, previewX1 - 3, previewY1 - 2, low & 0xFFFFFF,
				lowEnd & 0xFFFFFF, low, lowEnd);
		drawPreview(graphics);
	}

	private void drawPreview(GuiGraphicsExtractor graphics) {
		State state = runtime.state().orElse(null);
		if (state == null || sword == null) {
			return;
		}
		int centerX = (previewX0 + previewX1) / 2;
		int centerY = (previewY0 + previewY1) / 2;
		// What is applied, not what the widgets say: a slider being dragged has not changed anything yet.
		Optional<Appearance> applied = visuals.appearanceFor(sword);
		if (applied.isEmpty()) {
			// The sword stays a sword: show exactly that.
			graphics.pose().pushMatrix();
			graphics.pose().translate(centerX, centerY);
			graphics.pose().scale(3f, 3f);
			graphics.fakeItem(new ItemStack(sword), -8, -8);
			graphics.pose().popMatrix();
			return;
		}

		Appearance appearance = applied.get();
		KnifeVisual visual = visuals.get(appearance);
		if (visual != null) {
			shown = visual;
		}

		long now = System.nanoTime();
		float seconds = Math.clamp((now - lastFrameNanos) / 1e9f, 0f, 0.1f);
		lastFrameNanos = now;
		// Turns slowly by itself until the player takes over, and again a while after they let go.
		if (!turning && now - lastTurnedNanos > 2_000_000_000L) {
			yaw = (yaw + seconds * 25f) % 360f;
		}

		if (shown != null && previewX1 - previewX0 > 8 && previewY1 - previewY0 > 8) {
			graphics.guiRenderState.addPicturesInPictureState(new KnifePreviewState(shown, WeaponFx.styleFor(sword), false,
					yaw, pitch, previewX0, previewY0, previewX1, previewY1, null));
		}
		if (visual == null) {
			Component status = text(visuals.failed(appearance) ? "failed" : "loading");
			graphics.text(font, status, centerX - font.width(status) / 2, previewY1 - font.lineHeight - 4, Theme.DIM, false);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (mode == Mode.READY && event.x() >= previewX0 && event.x() < previewX1 && event.y() >= previewY0
				&& event.y() < previewY1) {
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
			lastTurnedNanos = System.nanoTime();
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (turning) {
			turning = false;
			lastTurnedNanos = System.nanoTime();
			return true;
		}
		return super.mouseReleased(event);
	}

	private static Component text(String key, Object... arguments) {
		return Component.translatable(PREFIX + key, arguments);
	}

	// ---------------------------------------------------------------------------------------
	// Widgets

	/** Picks which kind of sword the rest of the screen is about. Drawn as the sword's current look. */
	private final class SwordButton extends AbstractButton {
		private final Item item;
		private final ItemStack stack;

		/** How much the mouse is on it, gliding between 0 and 1. */
		private float hover;

		SwordButton(int x, int y, Item item) {
			super(x, y, SWORD_BUTTON, SWORD_BUTTON, new ItemStack(item).getHoverName());
			this.item = item;
			this.stack = new ItemStack(item);
			setTooltip(Tooltip.create(getMessage()));
		}

		@Override
		public void onPress(InputWithModifiers input) {
			if (applyCountdown >= 0) {
				apply();
			}
			sword = item;
			rebuildWidgets();
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
			// Each sword's button glows in that sword's own colours; the chosen one stays lit.
			boolean chosen = item == sword;
			hover = Ease.step(hover, isHoveredOrFocused() ? 1f : 0f, 20f, Ease.seconds());
			int color = Theme.now(colors(item), 0f);
			Glow.button(graphics, getX(), getY(), width, height, color, chosen ? 1f : hover * 0.7f, true);
			graphics.fakeItem(stack, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
			if (chosen) {
				Glow.light(graphics, getX() + 4, getY() + height - 2, getX() + width - 4, getY() + height - 1,
						FxMath.mix(color, 0xFFFFFFFF, 0.5f));
			}
		}

		@Override
		protected void updateWidgetNarration(NarrationElementOutput output) {
			defaultButtonNarrationText(output);
		}
	}

	/** One entry of a {@link ChoiceList}: what is stored, and what is shown for it. */
	private record Choice(String id, Component label) {
	}

	/** A scrolling list to pick one of many from; used for knives and for finishes. */
	private final class ChoiceList extends ObjectSelectionList<ChoiceList.Entry> {
		private final Consumer<String> onChoose;

		ChoiceList(int x, int y, int width, int height, Consumer<String> onChoose) {
			super(Minecraft.getInstance(), width, height, y, ROW_HEIGHT);
			this.onChoose = onChoose;
			updateSizeAndPosition(width, height, x, y);
		}

		void setChoices(List<Choice> choices, String selected) {
			List<Entry> entries = choices.stream().map(Entry::new).toList();
			replaceEntries(entries);
			for (Entry entry : entries) {
				if (entry.choice.id().equals(selected)) {
					setSelected(entry);
					centerScrollOn(entry);
				}
			}
		}

		@Override
		public int getRowWidth() {
			return width - 13;
		}

		@Override
		public int getRowLeft() {
			return getX() + 3;
		}

		@Override
		protected int scrollBarX() {
			return getRight() - 8;
		}

		@Override
		protected void extractListBackground(GuiGraphicsExtractor graphics) {
			Glow.pane(graphics, getX(), getY(), getRight(), getBottom(), 5f, Theme.GLASS, FxMath.fade(accent(), 0.3f), 0.06f);
		}

		@Override
		protected void extractListSeparators(GuiGraphicsExtractor graphics) {
			// The pane has a rim of its own.
		}

		@Override
		protected void extractSelection(GuiGraphicsExtractor graphics, Entry entry, int outlineColor) {
			// A bar of light, as in front of what is switched on in the menu, and its light across the line.
			int accent = accent();
			float x0 = entry.getX();
			float y0 = entry.getY();
			float x1 = x0 + entry.getWidth();
			float y1 = y0 + entry.getHeight();
			Glow.lightRounded(graphics, x0, y0, x1, y1, 3f, FxMath.fade(accent, 0.4f), FxMath.fade(accent, 0.06f));
			Glow.halo(graphics, x0 + 1f, y0 + 4f, x0 + 2f, y1 - 4f, 5f, FxMath.fade(accent, 0.6f));
			Glow.rounded(graphics, x0, y0 + 2.5f, x0 + 2.5f, y1 - 2.5f, 1.25f, FxMath.mix(accent, 0xFFFFFFFF, 0.3f));
		}

		@Override
		protected void extractScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
			if (!scrollable()) {
				return;
			}
			float x = scrollBarX() + 2f;
			Glow.rounded(graphics, x, getY() + 4, x + 3f, getBottom() - 4, 1.5f, Theme.TRACK);
			float top = Math.max(scrollBarY(), getY() + 4);
			float bottom = Math.min(scrollBarY() + scrollerHeight(), getBottom() - 4);
			int accent = accent();
			Glow.halo(graphics, x + 1f, top + 2f, x + 2f, bottom - 2f, 5f, FxMath.fade(accent, 0.45f));
			Glow.rounded(graphics, x, top, x + 3f, bottom, 1.5f, FxMath.mix(accent, 0xFFFFFFFF, 0.25f));
		}

		final class Entry extends ObjectSelectionList.Entry<Entry> {
			private final Choice choice;

			Entry(Choice choice) {
				this.choice = choice;
			}

			@Override
			public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered,
					float partialTick) {
				// Long names are cut off at the list's edge rather than running into the next column.
				int x = getContentX() + 2;
				int y = getContentYMiddle() - font.lineHeight / 2;
				boolean chosen = ChoiceList.this.getSelected() == this;
				if (hovered && !chosen) {
					Glow.lightRounded(graphics, getX(), getY(), getX() + getWidth(), getY() + getHeight(), 3f,
							FxMath.fade(accent(), 0.16f), FxMath.fade(accent(), 0.03f));
				}
				graphics.enableScissor(getContentX(), getContentY(), getContentX() + getContentWidth(),
						getContentY() + ROW_HEIGHT);
				graphics.text(font, choice.label(), x + (chosen ? 2 : 0), y, chosen || hovered ? 0xFFFFFFFF : 0xFFC6C6D4,
						false);
				graphics.disableScissor();
			}

			@Override
			public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
				if (ChoiceList.this.getSelected() != this) {
					ChoiceList.this.setSelected(this);
					onChoose.accept(choice.id());
				}
				return super.mouseClicked(event, doubleClick);
			}

			@Override
			public Component getNarration() {
				return Component.translatable("narrator.select", choice.label());
			}
		}
	}

	/** Picks the pattern seed, which decides where the finish's pattern sits on the knife. */
	private final class PatternSlider extends LookSlider {
		PatternSlider(int x, int y, int width, int height) {
			super(x, y, width, height, setting.pattern / (double) ModConfig.MAX_PATTERN);
			updateMessage();
		}

		void sync(boolean hasFinish) {
			active = hasFinish;
			value = setting.pattern / (double) ModConfig.MAX_PATTERN;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			setMessage(active ? text("pattern", (int) Math.round(value * ModConfig.MAX_PATTERN))
					: text("pattern_none"));
		}

		@Override
		protected void applyValue() {
			int pattern = (int) Math.round(value * ModConfig.MAX_PATTERN);
			if (pattern != setting.pattern) {
				setting.pattern = pattern;
				changed();
			}
		}
	}

	/** Picks the wear, within what the chosen finish can have. */
	private final class WearSlider extends LookSlider {
		private float min;
		private float max = 1f;

		WearSlider(int x, int y, int width, int height) {
			super(x, y, width, height, 0.0);
		}

		void sync(FinishCatalog.@Nullable Finish finish) {
			active = finish != null && finish.wearMax() > finish.wearMin();
			if (finish != null) {
				min = finish.wearMin();
				max = Math.max(finish.wearMax(), min);
				value = max > min ? Math.clamp((setting.wear - min) / (max - min), 0f, 1f) : 0.0;
			}
			updateMessage();
		}

		private float wear() {
			// Hundredths are as fine as anyone can see, and keep the number of distinct textures down.
			return Math.round((min + (float) value * (max - min)) * 100f) / 100f;
		}

		@Override
		protected void updateMessage() {
			if (!active) {
				setMessage(text("wear_none"));
				return;
			}
			float wear = wear();
			int grade = 0;
			while (grade < WEAR_LIMITS.length && wear >= WEAR_LIMITS[grade]) {
				grade++;
			}
			setMessage(text("wear", String.format(Locale.ROOT, "%.2f", wear), text("wear." + WEAR_NAMES[grade])));
		}

		@Override
		protected void applyValue() {
			float wear = wear();
			if (wear != setting.wear) {
				setting.wear = wear;
				changed();
			}
		}
	}
}
