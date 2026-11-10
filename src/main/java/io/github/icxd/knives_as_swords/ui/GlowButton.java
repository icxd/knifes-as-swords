package io.github.icxd.knives_as_swords.ui;

import io.github.icxd.knives_as_swords.fx.FxMath;
import java.util.function.IntSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * A button in the look of the mod's menus: dark glass that lights up in its colour when the mouse
 * is on it. It behaves like any button of the game's (keyboard, narration, tooltips).
 */
public class GlowButton extends AbstractButton {
	private final Runnable action;
	private IntSupplier accent = () -> Theme.now(Theme.ACCENT, 0f);
	/** How much the mouse is on it, gliding between 0 and 1. */
	private float hover;

	public GlowButton(int x, int y, int width, int height, Component message, Runnable action) {
		super(x, y, width, height, message);
		this.action = action;
	}

	/** Gives the button a colour of its own; asked for every frame, so it may change. */
	public GlowButton accent(IntSupplier accent) {
		this.accent = accent;
		return this;
	}

	@Override
	public void onPress(InputWithModifiers input) {
		action.run();
	}

	/** The button's colour right now, fully opaque. */
	protected final int accent() {
		return accent.getAsInt() | 0xFF000000;
	}

	/** How much the mouse is on the button right now, 0 to 1. */
	protected final float hover() {
		return hover;
	}

	@Override
	protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		hover = Ease.step(hover, active && isHoveredOrFocused() ? 1f : 0f, 20f, Ease.seconds());
		Glow.button(graphics, getX(), getY(), width, height, accent(), hover, active);
		extractLabel(graphics, Minecraft.getInstance().font);
	}

	/** Draws what is written on the button. */
	protected void extractLabel(GuiGraphicsExtractor graphics, Font font) {
		String text = Glow.fit(font, getMessage().getString(), width - 4);
		Glow.text(graphics, font, text, getX() + (width - font.width(text)) / 2f, textY(font), ink(),
				FxMath.fade(accent(), 0.35f * hover));
	}

	/** The colour of the button's writing: brighter under the mouse, faint when it cannot be pressed. */
	protected final int ink() {
		return active ? FxMath.mix(Theme.TEXT, 0xFFFFFFFF, hover) : Theme.FAINT;
	}

	/** Where a line of text sits so that it is in the button's middle, top to bottom. */
	protected final float textY(Font font) {
		return getY() + (height - font.lineHeight + 2) / 2f;
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
