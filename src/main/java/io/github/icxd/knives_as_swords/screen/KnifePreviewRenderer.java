package io.github.icxd.knives_as_swords.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.icxd.knives_as_swords.model.KnifeMesh;
import io.github.icxd.knives_as_swords.render.KnifeItemRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/** Draws the large, turnable knife in the settings screen. */
public final class KnifePreviewRenderer extends PictureInPictureRenderer<KnifePreviewState> {
	/** Full brightness, as for everything shown in a menu. */
	private static final int FULL_LIGHT = 15728880;
	/** How much of the rectangle's shorter side the knife's longest dimension takes up. */
	private static final float FILL = 0.92f;

	@Override
	public Class<KnifePreviewState> getRenderStateClass() {
		return KnifePreviewState.class;
	}

	@Override
	protected void renderToTexture(KnifePreviewState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
		Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
		KnifeMesh mesh = state.visual().mesh();
		Vector3f min = mesh.min();
		Vector3f max = mesh.max();
		Vector3f size = new Vector3f(max).sub(min);
		Vector3f center = new Vector3f(min).add(max).mul(0.5f);
		float box = Math.min(state.x1() - state.x0(), state.y1() - state.y0());
		// The knife may be turned any way round, so its longest diagonal has to fit.
		float scale = FILL * box / Math.max(size.length(), 1e-4f);

		// Screens count y downwards; turn the knife so that its up is the screen's up.
		poseStack.rotateDegrees(Axis.XP, 180f);
		poseStack.rotateDegrees(Axis.XP, state.pitch());
		poseStack.rotateDegrees(Axis.YP, state.yaw());
		// Lying diagonally, flat side to the viewer, like an item in a slot.
		poseStack.rotateDegrees(Axis.ZP, -45f);
		poseStack.rotateDegrees(Axis.YP, 90f);
		poseStack.scale(scale, scale, scale);
		poseStack.translate(-center.x, -center.y, -center.z);
		KnifeItemRenderer.submit(state.visual(), mesh.positions(), mesh.normals(), poseStack, submitNodeCollector,
				FULL_LIGHT, OverlayTexture.NO_OVERLAY, false, false);
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2f;
	}

	@Override
	protected String getTextureLabel() {
		return "knife preview";
	}
}
