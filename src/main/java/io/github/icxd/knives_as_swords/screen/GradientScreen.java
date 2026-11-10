package io.github.icxd.knives_as_swords.screen;

import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.fx.FxStyle;
import io.github.icxd.knives_as_swords.ui.Backdrop;
import io.github.icxd.knives_as_swords.ui.Ease;
import io.github.icxd.knives_as_swords.ui.Glow;
import io.github.icxd.knives_as_swords.ui.GlowButton;
import io.github.icxd.knives_as_swords.ui.GlowCycle;
import io.github.icxd.knives_as_swords.ui.Theme;
import io.github.icxd.knives_as_swords.visuals.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Where the colours of one visual are put together: the same gradient controls as in the effect
 * editor, on a screen of their own. Changes apply at once.
 */
public final class GradientScreen extends Screen {
	private static final String PREFIX = KnivesAsSwordsClient.MOD_ID + ".effect_screen.";
	private static final int MARGIN = 8;
	private static final int GAP = 4;
	private static final int BUTTON_HEIGHT = 20;

	private final @Nullable Screen parent;
	private final Setting.Colors setting;
	private final Runnable onChange;
	private final GradientEditor editor;
	private String copiedLook = "";
	private int top;
	/** The pane everything sits on. */
	private int paneX0, paneY0, paneX1, paneY1;

	/**
	 * @param title what the colours are for
	 * @param onChange called whenever the colours have changed, after the setting has taken them over
	 */
	public GradientScreen(@Nullable Screen parent, Component title, Setting.Colors setting, Runnable onChange) {
		super(title);
		this.parent = parent;
		this.setting = setting;
		this.onChange = onChange;
		this.editor = new GradientEditor(setting.get(), this::changed);
	}

	private void changed() {
		setting.set(editor.gradient());
		onChange.run();
	}

	@Override
	protected void init() {
		int editorWidth = Math.min(width - MARGIN * 4, 320);
		int x = (width - editorWidth) / 2;
		int block = font.lineHeight + GAP * 2 + GradientEditor.HEIGHT + GAP * 2 + BUTTON_HEIGHT;
		top = Math.max((height - block) / 2, MARGIN);
		int y = top + font.lineHeight + GAP * 2;
		editor.build(font, this::addRenderableWidget, x, y, editorWidth);
		paneX0 = x - MARGIN;
		paneY0 = top - MARGIN;
		paneX1 = x + editorWidth + MARGIN;
		paneY1 = y + GradientEditor.HEIGHT + GAP * 2 + BUTTON_HEIGHT + MARGIN;

		// The colours of any ready-made look can be the starting point.
		List<String> looks = new ArrayList<>(List.of(""));
		FxStyle.all().forEach(look -> looks.add(look.id()));
		addRenderableWidget(new GlowCycle<>(editor.spareX(), editor.spareY(), editor.spareWidth(), BUTTON_HEIGHT, null,
				looks, copiedLook, look -> look.isEmpty() ? Component.translatable(PREFIX + "copy")
						: Component.translatable(PREFIX + "copy.from",
								Component.translatable(KnivesAsSwordsClient.MOD_ID + ".effect." + look)),
				look -> {
					copiedLook = look;
					FxStyle.byId(look).ifPresent(source -> {
						editor.set(source.colors());
						changed();
					});
				}).accent(() -> accent(0.5f)));

		addRenderableWidget(new GlowButton(x, y + GradientEditor.HEIGHT + GAP * 2, editorWidth, BUTTON_HEIGHT,
				CommonComponents.GUI_DONE, this::onClose).accent(() -> accent(0.5f)));
	}

	/** One of the colours being put together, for this moment; the screen glows in them. */
	private int accent(float offset) {
		return Theme.now(editor.gradient(), offset);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		if (minecraft.level == null) {
			extractPanorama(graphics, partialTick);
		}
		extractBlurredBackground(graphics);
		Backdrop.draw(graphics, width, height, editor.gradient(), 1f);
		Glow.pane(graphics, paneX0, paneY0, paneX1, paneY1, 7f, Theme.GLASS, FxMath.fade(accent(0f), 0.5f), 0.28f);
		minecraft.gui.hud.extractDeferredSubtitles();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		Ease.frame();
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
		Glow.text(graphics, font, title.getString(), (width - font.width(title)) / 2f, top, 0xFFFFFFFF,
				FxMath.fade(accent(0f), 0.4f));
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
