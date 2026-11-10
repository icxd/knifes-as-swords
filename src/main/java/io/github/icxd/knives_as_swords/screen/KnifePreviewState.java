package io.github.icxd.knives_as_swords.screen;

import io.github.icxd.knives_as_swords.fx.FxStyle;
import io.github.icxd.knives_as_swords.render.KnifeVisual;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

/**
 * A knife to draw into a rectangle of a screen, turned by the given angles.
 *
 * @param effects the look of the knife's effects; {@code null} for none
 * @param swinging whether the knife slashes by itself, over and over, to show its effects in motion
 * @param yaw turn about the vertical axis, in degrees
 * @param pitch tilt towards the viewer, in degrees
 */
public record KnifePreviewState(KnifeVisual visual, @Nullable FxStyle effects, boolean swinging, float yaw, float pitch,
		int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds)
		implements PictureInPictureRenderState {
	public KnifePreviewState(KnifeVisual visual, @Nullable FxStyle effects, boolean swinging, float yaw, float pitch,
			int x0, int y0, int x1, int y1, @Nullable ScreenRectangle scissorArea) {
		this(visual, effects, swinging, yaw, pitch, x0, y0, x1, y1, scissorArea,
				PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea));
	}

	/** One unit while drawing is one GUI pixel; the renderer sizes the knife itself. */
	@Override
	public float scale() {
		return 1f;
	}
}
