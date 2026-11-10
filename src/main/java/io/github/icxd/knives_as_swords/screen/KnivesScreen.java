package io.github.icxd.knives_as_swords.screen;

import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.KnifeRuntime.ExtractorStatus;
import io.github.icxd.knives_as_swords.KnifeRuntime.State;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.ModConfig;
import io.github.icxd.knives_as_swords.cs2.FinishCatalog;
import io.github.icxd.knives_as_swords.cs2.KnifeModel;
import io.github.icxd.knives_as_swords.extract.ExtractorRelease;
import io.github.icxd.knives_as_swords.render.Appearance;
import io.github.icxd.knives_as_swords.render.KnifeReplacement;
import io.github.icxd.knives_as_swords.render.KnifeVisual;
import io.github.icxd.knives_as_swords.render.KnifeVisuals;
import io.github.icxd.knives_as_swords.render.SwordDefaults;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
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
 * pattern and wear. Every change applies at once and is saved, so the preview is simply the
 * knife as it will look in the world. The one exception is a slider while it is being dragged:
 * its value applies when it is let go, because every new look has to be painted first.
 */
public final class KnivesScreen extends Screen {
	private static final String PREFIX = KnivesAsSwordsClient.MOD_ID + ".screen.";
	private static final int MARGIN = 8;
	private static final int GAP = 4;
	private static final int ROW_HEIGHT = 14;
	private static final int BUTTON_HEIGHT = 20;
	private static final int SWORD_BUTTON = 22;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GRAY = 0xFFA0A0A0;
	private static final int PANEL = 0x60000000;
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
				addRenderableWidget(Button.builder(text("rescan"), button -> {
					runtime.scan();
					mode = Mode.LOOKING;
					rebuildWidgets();
				}).bounds(centerX - 100, height / 2 + 20, 200, BUTTON_HEIGHT).build());
			} else if (mode == Mode.NEEDS_CONVERTER) {
				addRenderableWidget(Button.builder(text("download"), button -> askForDownload())
						.bounds(centerX - 100, height / 2 + 20, 200, BUTTON_HEIGHT).build());
			}
			addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
					.bounds(centerX - 100, bottomRow, 200, BUTTON_HEIGHT).build());
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
		int half = (contentWidth - GAP) / 2;
		patternSlider = addRenderableWidget(new PatternSlider(MARGIN, sliderRow, half, BUTTON_HEIGHT));
		wearSlider = addRenderableWidget(new WearSlider(MARGIN + half + GAP, sliderRow, contentWidth - half - GAP,
				BUTTON_HEIGHT));
		addRenderableWidget(CycleButton.onOffBuilder(setting.enabled).create(MARGIN, bottomRow, quarter,
				BUTTON_HEIGHT, text("show_as_knife"), (button, value) -> {
					setting.enabled = value;
					apply();
				}));
		addRenderableWidget(CycleButton.onOffBuilder(runtime.config().firstPersonAnimations).create(
				MARGIN + quarter + GAP, bottomRow, quarter, BUTTON_HEIGHT, text("animations"),
				(button, value) -> runtime.updateConfig(config -> config.firstPersonAnimations = value)));
		addRenderableWidget(CycleButton.onOffBuilder(runtime.config().shine).create(
				MARGIN + (quarter + GAP) * 2, bottomRow, quarter, BUTTON_HEIGHT, text("shine"),
				(button, value) -> runtime.updateConfig(config -> config.shine = value)));
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
				.bounds(MARGIN + (quarter + GAP) * 3, bottomRow, contentWidth - (quarter + GAP) * 3, BUTTON_HEIGHT)
				.build());

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
	private abstract class LookSlider extends AbstractSliderButton {
		private boolean held;
		private boolean changedWhileHeld;

		LookSlider(int x, int y, int width, int height, double value) {
			super(x, y, width, height, CommonComponents.EMPTY, value);
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
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		graphics.centeredText(font, title, width / 2, MARGIN, WHITE);

		if (mode != Mode.READY) {
			Component message = text(switch (mode) {
				case LOOKING -> "looking";
				case NO_GAME -> "no_game";
				case NEEDS_CONVERTER -> "needs_converter";
				default -> "no_converter";
			});
			int textWidth = Math.min(width - MARGIN * 4, 320);
			graphics.textWithWordWrap(font, message, (width - textWidth) / 2, height / 2 - 30, textWidth, WHITE);
			return;
		}

		int labelY = previewY0 - font.lineHeight - 3;
		graphics.text(font, text("knife"), MARGIN, labelY, GRAY);
		if (finishList != null) {
			graphics.text(font, text("finish"), finishList.getX(), labelY, GRAY);
		}
		if (sword != null) {
			graphics.text(font, new ItemStack(sword).getHoverName(), previewX0, labelY, GRAY);
		}
		graphics.fill(previewX0, previewY0, previewX1, previewY1, PANEL);
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
			graphics.guiRenderState.addPicturesInPictureState(new KnifePreviewState(shown, yaw, pitch, previewX0,
					previewY0, previewX1, previewY1, null));
		}
		if (visual == null) {
			Component status = text(visuals.failed(appearance) ? "failed" : "loading");
			graphics.centeredText(font, status, centerX, previewY1 - font.lineHeight - 3, WHITE);
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
			extractDefaultSprite(graphics);
			graphics.fakeItem(stack, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
			if (item == sword) {
				graphics.outline(getX(), getY(), width, height, WHITE);
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
			return width - 12;
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
				graphics.enableScissor(getContentX(), getContentY(), getContentX() + getContentWidth(),
						getContentY() + ROW_HEIGHT);
				graphics.text(font, choice.label(), x, y, WHITE);
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
