package io.github.icxd.knives_as_swords.ui;

import io.github.icxd.knives_as_swords.fx.FxMath;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * A box to type into, in the look of the mod's menus: the game's own, on dark glass that lights
 * up while it is being typed into.
 */
public final class GlowEditBox extends EditBox {
	private final IntSupplier accent;
	private float lit;
	/** Set while the game's own drawing runs, so that it leaves its frame out. */
	private boolean drawing;

	public GlowEditBox(Font font, int x, int y, int width, int height, Component message, IntSupplier accent) {
		super(font, x, y, width, height, message);
		this.accent = accent;
		setTextColor(Theme.TEXT);
	}

	@Override
	public boolean isBordered() {
		// The frame is this class's business; where the writing sits stays as for a framed box.
		return !drawing && super.isBordered();
	}

	@Override
	public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		lit = Ease.step(lit, isFocused() ? 1f : isHovered() ? 0.4f : 0f, 20f, Ease.seconds());
		int color = accent.getAsInt() | 0xFF000000;
		Glow.pane(graphics, getX(), getY(), getX() + width, getY() + height, 4f, Theme.GLASS,
				FxMath.fade(color, 0.3f + 0.6f * lit), 0.06f + 0.3f * lit);
		drawing = true;
		try {
			super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
		} finally {
			drawing = false;
		}
	}
}
