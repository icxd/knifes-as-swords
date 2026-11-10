package io.github.icxd.knives_as_swords.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/**
 * A slider in the look of the mod's menus: dark glass filled with light as far as its value goes.
 * It behaves like any slider of the game's; only how it is drawn is its own.
 */
public abstract class GlowSlider extends AbstractSliderButton {
	/** How much the mouse is on it, gliding between 0 and 1. */
	private float hover;

	protected GlowSlider(int x, int y, int width, int height, Component message, double value) {
		super(x, y, width, height, message, value);
	}

	/** The slider's colour right now; asked for every frame, so it may change. */
	protected int accent() {
		return Theme.now(Theme.ACCENT, 0f);
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		hover = Ease.step(hover, active && (isHovered() || isFocused()) ? 1f : 0f, 20f, Ease.seconds());
		Glow.slider(graphics, getX(), getY(), width, height, (float) value, accent(), hover, active);
		Font font = Minecraft.getInstance().font;
		String text = Glow.fit(font, getMessage().getString(), width - 10);
		// With a shadow: the writing lies on the slider's light.
		graphics.text(font, text, getX() + (width - font.width(text)) / 2, getY() + (height - font.lineHeight + 2) / 2,
				active ? 0xFFFFFFFF : Theme.FAINT, active);
		handleCursor(graphics);
	}
}
