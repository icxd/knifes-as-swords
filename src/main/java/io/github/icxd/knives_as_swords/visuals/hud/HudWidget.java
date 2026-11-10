package io.github.icxd.knives_as_swords.visuals.hud;

import io.github.icxd.knives_as_swords.fx.FxClock;
import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.ui.Glow;
import io.github.icxd.knives_as_swords.ui.Theme;
import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A visual that is a small panel on the heads-up display, which the player can put wherever they
 * like by dragging it while the menu is open.
 *
 * <p>Where it sits is kept as a share of the room it has on screen (0 at the left or top edge, 1
 * at the right or bottom), so it stays put when the window changes size.
 *
 * <p>Its background is a plate of dark glass with a line of light along the top, in the widget's
 * own colours: the look of the mod's menus, kept thin enough to play behind.
 */
public abstract class HudWidget extends Visual {
	/** How far the plate behind a widget reaches beyond what the widget shows, before scaling. */
	private static final int FRAME_X = 5;
	private static final int FRAME_Y = 4;

	private final Setting.Slider x;
	private final Setting.Slider y;
	protected final Setting.Slider scale = slider("scale", 1f, 0.5f, 2f, 0.05f);
	protected final Setting.Toggle background = toggle("background", true);
	/** The widget's own colours: the light on its plate, and whatever else of it glows. */
	protected final Setting.Colors glow = colors("glow", 0x9B5CFF, 0x3FC8FF);

	/** Where the widget was last drawn, in screen pixels; for telling whether the mouse is on it. */
	private int left;
	private int top;
	private int right;
	private int bottom;
	private boolean drawn;

	/**
	 * @param atX where the widget starts out across the screen, 0 (left) to 1 (right); {@code atY}
	 *     likewise from top to bottom
	 */
	protected HudWidget(String id, boolean onAtFirst, float atX, float atY) {
		super(id, Category.HUD, onAtFirst);
		// Set by dragging, not from the list of settings.
		x = slider("x", atX, 0f, 1f, 0.001f);
		x.onlyWhen(() -> false);
		y = slider("y", atY, 0f, 1f, 0.001f);
		y.onlyWhen(() -> false);
	}

	/** How wide what there is to show is right now, before scaling; 0 for nothing to show. */
	protected abstract int width(Minecraft client, boolean editing);

	protected abstract int height(Minecraft client, boolean editing);

	/** Whether the background is one dark panel behind everything; a widget may have its own idea of a background. */
	protected boolean framed() {
		return true;
	}

	/**
	 * One of the widget's own colours for this moment; they wander slowly through its gradient.
	 *
	 * @param along which one: lights that are to differ ask for different ones, 0 to 1
	 * @return the colour, fully opaque
	 */
	protected final int glowColor(float along) {
		return Theme.now(glow.get(), FxClock.seconds(), along);
	}

	/** Draws the widget with its top left corner at the origin. */
	protected abstract void draw(GuiGraphicsExtractor graphics, Minecraft client, int width, int height, boolean editing);

	@Override
	public final void hud(GuiGraphicsExtractor graphics, boolean editing) {
		Minecraft client = Minecraft.getInstance();
		drawn = false;
		// The debug screen needs the room, and says most of this itself.
		if (client.player == null || (!editing && client.debugEntries.isOverlayVisible())) {
			return;
		}
		int width = width(client, editing);
		int height = height(client, editing);
		if (width <= 0 || height <= 0) {
			return;
		}
		float size = scale.get();
		int scaledWidth = Math.round(width * size);
		int scaledHeight = Math.round(height * size);
		int marginX = marginX();
		int marginY = marginY();
		left = marginX + Math.round(x.get() * Math.max(graphics.guiWidth() - scaledWidth - marginX * 2, 0));
		top = marginY + Math.round(y.get() * Math.max(graphics.guiHeight() - scaledHeight - marginY * 2, 0));
		right = left + scaledWidth;
		bottom = top + scaledHeight;
		drawn = true;

		graphics.pose().pushMatrix();
		graphics.pose().translate(left, top);
		graphics.pose().scale(size, size);
		if (background.on() && framed()) {
			Glow.plate(graphics, -FRAME_X, -FRAME_Y, width + FRAME_X, height + FRAME_Y, 4f, glowColor(0f), glowColor(0.5f),
					1f);
		}
		draw(graphics, client, width, height, editing);
		graphics.pose().popMatrix();
		if (editing) {
			// Light all round, breathing: this can be taken hold of.
			int light = glowColor(0.25f);
			float breath = 0.5f + 0.5f * (float) Math.sin(FxClock.seconds() * 3.2f);
			float x0 = left - marginX + 1f;
			float y0 = top - marginY + 1f;
			float x1 = right + marginX - 1f;
			float y1 = bottom + marginY - 1f;
			Glow.halo(graphics, x0 + 2f, y0 + 2f, x1 - 2f, y1 - 2f, 11f, FxMath.fade(light, 0.22f + 0.2f * breath));
			int line = FxMath.fade(light, 0.55f + 0.3f * breath);
			Glow.light(graphics, x0 + 3f, y0 - 1f, x1 - 3f, y0, line);
			Glow.light(graphics, x0 + 3f, y1, x1 - 3f, y1 + 1f, line);
			Glow.light(graphics, x0 - 1f, y0 + 3f, x0, y1 - 3f, line);
			Glow.light(graphics, x1, y0 + 3f, x1 + 1f, y1 - 3f, line);
		}
	}

	/** How far the widget's top left corner stays from the edges of the screen, so that its plate fits. */
	private int marginX() {
		return Math.round((FRAME_X + 1) * scale.get());
	}

	private int marginY() {
		return Math.round((FRAME_Y + 1) * scale.get());
	}

	/** Whether the widget is on screen at this point, as it was last drawn. */
	public boolean contains(double mouseX, double mouseY) {
		return drawn && mouseX >= left - 3 && mouseX < right + 3 && mouseY >= top - 3 && mouseY < bottom + 3;
	}

	public int left() {
		return left;
	}

	public int top() {
		return top;
	}

	/** Puts the widget's top left corner at a point of the screen, as far as it fits. */
	public void moveTo(double newLeft, double newTop, int screenWidth, int screenHeight) {
		int roomX = screenWidth - (right - left) - marginX() * 2;
		int roomY = screenHeight - (bottom - top) - marginY() * 2;
		x.set(roomX > 0 ? (float) Math.clamp((newLeft - marginX()) / roomX, 0.0, 1.0) : 0f);
		y.set(roomY > 0 ? (float) Math.clamp((newTop - marginY()) / roomY, 0.0, 1.0) : 0f);
	}
}
