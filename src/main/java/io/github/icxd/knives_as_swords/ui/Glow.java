package io.github.icxd.knives_as_swords.ui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.icxd.knives_as_swords.fx.FxMath;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * Draws the shapes the mod's menus are made of: dark glass with round corners, and light that
 * glows round its edges, in the colours of the effects it sets up.
 *
 * <p>Everything takes fractions of a pixel, so that things can move smoothly. Shapes named
 * "light" are added to what is behind them, the way the effects in the world are; the others
 * cover it.
 */
public final class Glow {
	/** See {@code ui_glow.fsh}: where in a quad's coordinates each kind of shape has its middle. */
	private static final float PLAIN = 0f;
	private static final float SOFT = 11f;
	private static final float DISC = 21f;

	private Glow() {
	}

	// ---------------------------------------------------------------------------------------
	// Covering shapes

	public static void fill(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int argb) {
		quad(graphics, UiPipelines.SOLID, x0, y0, x1, y1, PLAIN, PLAIN, PLAIN, PLAIN, argb, argb, argb, argb);
	}

	/** A rectangle running from one colour at its left edge to another at its right. */
	public static void across(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int left,
			int right) {
		quad(graphics, UiPipelines.SOLID, x0, y0, x1, y1, PLAIN, PLAIN, PLAIN, PLAIN, left, right, left, right);
	}

	/** A rectangle running from one colour at its top edge to another at its bottom. */
	public static void down(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int top, int bottom) {
		quad(graphics, UiPipelines.SOLID, x0, y0, x1, y1, PLAIN, PLAIN, PLAIN, PLAIN, top, top, bottom, bottom);
	}

	/** A rectangle with round corners. */
	public static void rounded(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, float radius,
			int argb) {
		rounded(graphics, UiPipelines.SOLID, x0, y0, x1, y1, radius, argb, argb);
	}

	/** A rectangle with round corners, running from one colour at its left to another at its right. */
	public static void rounded(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, float radius,
			int left, int right) {
		rounded(graphics, UiPipelines.SOLID, x0, y0, x1, y1, radius, left, right);
	}

	/** A disc. */
	public static void disc(GuiGraphicsExtractor graphics, float x, float y, float radius, int argb) {
		quad(graphics, UiPipelines.SOLID, x - radius, y - radius, x + radius, y + radius, DISC - 1f, DISC - 1f, DISC + 1f,
				DISC + 1f, argb, argb, argb, argb);
	}

	// ---------------------------------------------------------------------------------------
	// Light

	/** A rectangle of light. */
	public static void light(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int argb) {
		quad(graphics, UiPipelines.LIGHT, x0, y0, x1, y1, PLAIN, PLAIN, PLAIN, PLAIN, argb, argb, argb, argb);
	}

	/** A rectangle of light with a colour of its own at each corner. */
	public static void light(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int topLeft,
			int topRight, int bottomLeft, int bottomRight) {
		quad(graphics, UiPipelines.LIGHT, x0, y0, x1, y1, PLAIN, PLAIN, PLAIN, PLAIN, topLeft, topRight, bottomLeft,
				bottomRight);
	}

	/** A rectangle of light running from one colour at its left edge to another at its right. */
	public static void lightAcross(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, int left,
			int right) {
		quad(graphics, UiPipelines.LIGHT, x0, y0, x1, y1, PLAIN, PLAIN, PLAIN, PLAIN, left, right, left, right);
	}

	/** A rectangle of light with round corners. */
	public static void lightRounded(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, float radius,
			int left, int right) {
		rounded(graphics, UiPipelines.LIGHT, x0, y0, x1, y1, radius, left, right);
	}

	/** A round light, brightest in its middle and gone at {@code radius}. */
	public static void dot(GuiGraphicsExtractor graphics, float x, float y, float radius, int argb) {
		quad(graphics, UiPipelines.LIGHT, x - radius, y - radius, x + radius, y + radius, SOFT - 1f, SOFT - 1f, SOFT + 1f,
				SOFT + 1f, argb, argb, argb, argb);
	}

	/**
	 * Light spilling outwards from the edges of a rectangle, as from something glowing: brightest
	 * at the edge, gone {@code spread} pixels away. The rectangle itself is left alone.
	 */
	public static void halo(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, float spread,
			int argb) {
		if (spread <= 0f || (argb >>> 24) == 0) {
			return;
		}
		RenderPipeline light = UiPipelines.LIGHT;
		// The sides: the light's middle runs along the edge.
		quad(graphics, light, x0 - spread, y0, x0, y1, SOFT + 1f, SOFT, SOFT, SOFT, argb, argb, argb, argb);
		quad(graphics, light, x1, y0, x1 + spread, y1, SOFT, SOFT, SOFT + 1f, SOFT, argb, argb, argb, argb);
		quad(graphics, light, x0, y0 - spread, x1, y0, SOFT, SOFT + 1f, SOFT, SOFT, argb, argb, argb, argb);
		quad(graphics, light, x0, y1, x1, y1 + spread, SOFT, SOFT, SOFT, SOFT + 1f, argb, argb, argb, argb);
		// The corners: the light's middle is the corner itself.
		quad(graphics, light, x0 - spread, y0 - spread, x0, y0, SOFT + 1f, SOFT + 1f, SOFT, SOFT, argb, argb, argb, argb);
		quad(graphics, light, x1, y0 - spread, x1 + spread, y0, SOFT, SOFT + 1f, SOFT + 1f, SOFT, argb, argb, argb, argb);
		quad(graphics, light, x0 - spread, y1, x0, y1 + spread, SOFT + 1f, SOFT, SOFT, SOFT + 1f, argb, argb, argb, argb);
		quad(graphics, light, x1, y1, x1 + spread, y1 + spread, SOFT, SOFT, SOFT + 1f, SOFT + 1f, argb, argb, argb, argb);
	}

	// ---------------------------------------------------------------------------------------
	// Things made of the above

	/**
	 * A pane of dark glass: a round-cornered body with a thin rim, and its rim's light spilling
	 * out around it.
	 *
	 * @param rim the rim's colour; its light is as strong as {@code glow} says, 0 to 1
	 */
	public static void pane(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, float radius,
			int body, int rim, float glow) {
		halo(graphics, x0 + radius, y0 + radius, x1 - radius, y1 - radius, radius + 9f, FxMath.fade(rim | 0xFF000000, glow));
		rounded(graphics, x0 - 1f, y0 - 1f, x1 + 1f, y1 + 1f, radius + 1f, rim);
		rounded(graphics, x0, y0, x1, y1, radius, body);
	}

	/**
	 * A plate for the heads-up display: see-through dark glass with round corners and a line of
	 * light along its top, running from one colour to another.
	 *
	 * @param glow how strong the line and its light are, 0 to 1
	 */
	public static void plate(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, float radius,
			int left, int right, float glow) {
		rounded(graphics, x0, y0, x1, y1, radius, Theme.PLATE);
		int from = left | 0xFF000000;
		int to = right | 0xFF000000;
		halo(graphics, x0 + radius, y0 + 0.5f, x1 - radius, y0 + 0.5f, 5f, FxMath.fade(FxMath.mix(from, to, 0.5f), 0.3f * glow));
		lightAcross(graphics, x0 + radius, y0, x1 - radius, y0 + 1f, FxMath.fade(from, 0.9f * glow), FxMath.fade(to, 0.9f * glow));
	}

	/**
	 * A bar that fills with light from the left, as for health or progress, on a dark track.
	 *
	 * @param share how much of it is filled, 0 to 1
	 * @param left the colour of the light at the bar's left end; {@code right} where the filling ends
	 */
	public static void bar(GuiGraphicsExtractor graphics, float x0, float y0, float x1, float y1, float share, int left,
			int right) {
		float radius = (y1 - y0) / 2f;
		rounded(graphics, x0, y0, x1, y1, radius, Theme.TRACK);
		float end = x0 + (x1 - x0) * Math.clamp(share, 0f, 1f);
		if (end - x0 < 0.5f) {
			return;
		}
		int from = left | 0xFF000000;
		int to = right | 0xFF000000;
		halo(graphics, x0 + radius, y0 + radius, Math.max(end - radius, x0 + radius), y1 - radius, radius + 4f,
				FxMath.fade(to, 0.4f));
		rounded(graphics, x0, y0, Math.max(end, x0 + radius * 2f), y1, radius, FxMath.mix(from, 0xFF000000, 0.25f), to);
	}

	/** A text cut short with two dots if it is wider than the room there is for it. */
	public static String fit(Font font, String text, int room) {
		return font.width(text) <= room ? text
				: font.plainSubstrByWidth(text, Math.max(room - font.width(".."), 0)) + "..";
	}

	/** Text with light behind it, as if it glowed. */
	public static void text(GuiGraphicsExtractor graphics, Font font, String text, float x, float y, int argb, int glow) {
		if ((glow >>> 24) != 0) {
			float width = font.width(text);
			halo(graphics, x + 2f, y + 3f, x + width - 2f, y + font.lineHeight - 4f, 7f, glow);
		}
		graphics.text(font, text, Math.round(x), Math.round(y), argb, false);
	}

	public static void text(GuiGraphicsExtractor graphics, Font font, Component text, float x, float y, int argb,
			int glow) {
		text(graphics, font, text.getString(), x, y, argb, glow);
	}

	/**
	 * A button: dark glass that lights up in its accent colour when the mouse is on it.
	 *
	 * @param hover how much the mouse is on it, 0 to 1; meant to ease in and out
	 * @param active whether it can be pressed; one that cannot is dimmer
	 */
	public static void button(GuiGraphicsExtractor graphics, float x, float y, float width, float height, int accent,
			float hover, boolean active) {
		float lit = active ? hover : 0f;
		pane(graphics, x, y, x + width, y + height, 4f, Theme.GLASS,
				FxMath.fade(accent | 0xFF000000, active ? 0.35f + 0.5f * lit : 0.15f), 0.08f + 0.3f * lit);
		if (lit > 0f) {
			// Light welling up from the bottom edge.
			quad(graphics, UiPipelines.LIGHT, x + 2f, y + height * 0.35f, x + width - 2f, y + height - 1f, PLAIN, PLAIN,
					PLAIN, PLAIN, 0, 0, FxMath.fade(accent | 0xFF000000, 0.28f * lit), FxMath.fade(accent | 0xFF000000, 0.28f * lit));
		}
	}

	/**
	 * A slider: a button whose left part, up to where its value is, is filled with light, ending
	 * in a bright bar to take hold of.
	 *
	 * @param value where the slider stands, 0 to 1
	 */
	public static void slider(GuiGraphicsExtractor graphics, float x, float y, float width, float height, float value,
			int accent, float hover, boolean active) {
		button(graphics, x, y, width, height, accent, hover * 0.6f, active);
		if (!active) {
			return;
		}
		// Where the game's own slider has the middle of its handle, so the mouse meets it.
		float knob = x + 4f + (width - 8f) * Math.clamp(value, 0f, 1f);
		int strong = accent | 0xFF000000;
		lightAcross(graphics, x + 2f, y + 2f, knob, y + height - 2f, FxMath.fade(strong, 0.05f),
				FxMath.fade(strong, 0.30f + 0.15f * hover));
		halo(graphics, knob - 1f, y + 3f, knob + 1f, y + height - 3f, 6f, FxMath.fade(strong, 0.55f + 0.3f * hover));
		rounded(graphics, knob - 1.5f, y + 2f, knob + 1.5f, y + height - 2f, 1.5f, 0xFFFFFFFF);
	}

	// ---------------------------------------------------------------------------------------
	// The quads everything is made of

	private static void rounded(GuiGraphicsExtractor graphics, RenderPipeline pipeline, float x0, float y0, float x1,
			float y1, float radius, int left, int right) {
		float r = Math.max(Math.min(radius, Math.min(x1 - x0, y1 - y0) / 2f), 0f);
		if (r < 0.25f) {
			quad(graphics, pipeline, x0, y0, x1, y1, PLAIN, PLAIN, PLAIN, PLAIN, left, right, left, right);
			return;
		}
		float span = Math.max(x1 - x0, 1e-4f);
		int nearLeft = FxMath.mix(left, right, r / span);
		int nearRight = FxMath.mix(left, right, 1f - r / span);
		// The middle from top to bottom, then the two sides between the corners.
		quad(graphics, pipeline, x0 + r, y0, x1 - r, y1, PLAIN, PLAIN, PLAIN, PLAIN, nearLeft, nearRight, nearLeft, nearRight);
		quad(graphics, pipeline, x0, y0 + r, x0 + r, y1 - r, PLAIN, PLAIN, PLAIN, PLAIN, left, nearLeft, left, nearLeft);
		quad(graphics, pipeline, x1 - r, y0 + r, x1, y1 - r, PLAIN, PLAIN, PLAIN, PLAIN, nearRight, right, nearRight, right);
		// The corners: quarters of a disc whose middle is the corner's inner point.
		quad(graphics, pipeline, x0, y0, x0 + r, y0 + r, DISC - 1f, DISC - 1f, DISC, DISC, left, nearLeft, left, nearLeft);
		quad(graphics, pipeline, x1 - r, y0, x1, y0 + r, DISC, DISC - 1f, DISC + 1f, DISC, nearRight, right, nearRight, right);
		quad(graphics, pipeline, x0, y1 - r, x0 + r, y1, DISC - 1f, DISC, DISC, DISC + 1f, left, nearLeft, left, nearLeft);
		quad(graphics, pipeline, x1 - r, y1 - r, x1, y1, DISC, DISC, DISC + 1f, DISC + 1f, nearRight, right, nearRight, right);
	}

	private static void quad(GuiGraphicsExtractor graphics, RenderPipeline pipeline, float x0, float y0, float x1,
			float y1, float u0, float v0, float u1, float v1, int topLeft, int topRight, int bottomLeft, int bottomRight) {
		if (!(x1 > x0) || !(y1 > y0) || ((topLeft | topRight | bottomLeft | bottomRight) >>> 24) == 0) {
			return;
		}
		Matrix3x2f pose = new Matrix3x2f(graphics.pose());
		ScreenRectangle scissor = graphics.scissorStack.peek();
		int left = (int) Math.floor(x0);
		int top = (int) Math.floor(y0);
		ScreenRectangle bounds = new ScreenRectangle(left, top, (int) Math.ceil(x1) - left, (int) Math.ceil(y1) - top)
				.transformMaxBounds(pose);
		if (scissor != null) {
			bounds = scissor.intersection(bounds);
		}
		graphics.guiRenderState.addGuiElement(new Quad(pipeline, pose, x0, y0, x1, y1, u0, v0, u1, v1, topLeft, topRight,
				bottomLeft, bottomRight, scissor, bounds));
	}

	/** One quad on its way to the screen. */
	private record Quad(RenderPipeline pipeline, Matrix3x2f pose, float x0, float y0, float x1, float y1, float u0,
			float v0, float u1, float v1, int topLeft, int topRight, int bottomLeft, int bottomRight,
			@Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
		@Override
		public void buildVertices(VertexConsumer vertices) {
			vertices.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(topLeft);
			vertices.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(bottomLeft);
			vertices.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(bottomRight);
			vertices.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(topRight);
		}

		@Override
		public TextureSetup textureSetup() {
			return TextureSetup.noTexture();
		}
	}
}
