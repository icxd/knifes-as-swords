package io.github.icxd.knives_as_swords.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.fx.FxStyle;
import io.github.icxd.knives_as_swords.fx.ItemFx;
import io.github.icxd.knives_as_swords.model.KnifeMesh;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Draws a knife wherever Minecraft draws an item: the special-model renderer that takes the
 * place of a sword's own layers.
 */
public final class KnifeItemRenderer implements SpecialModelRenderer<KnifeItemRenderer.Draw> {
	public static final KnifeItemRenderer INSTANCE = new KnifeItemRenderer();

	/**
	 * One knife to draw, and what its effects need to know about where.
	 *
	 * @param effects the look of its effects; {@code null} for none
	 * @param context how the item is shown
	 * @param holder which entity's hand it is in, or 0 when that is unknown or does not matter
	 * @param holderX where that entity is in the world this frame; likewise {@code holderY}, {@code holderZ}
	 */
	public record Draw(KnifeVisual visual, @Nullable FxStyle effects, ItemDisplayContext context, int holder,
			double holderX, double holderY, double holderZ) {
	}

	private KnifeItemRenderer() {
	}

	@Override
	public void submit(@Nullable Draw draw, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
			int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
		if (draw == null) {
			return;
		}
		KnifeVisual visual = draw.visual();
		submit(visual, visual.mesh().positions(), visual.mesh().normals(), poseStack, submitNodeCollector, lightCoords,
				overlayCoords, hasFoil, false);
		if (draw.effects() != null) {
			ItemFx.submit(visual, draw.effects(), draw.context(), draw.holder(), draw.holderX(), draw.holderY(),
					draw.holderZ(), poseStack, submitNodeCollector);
		}
	}

	/**
	 * Draws the knife with the given (possibly animated) vertex positions and normals.
	 *
	 * @param mirrored whether the pose stack holds a mirroring transform of the caller's own
	 */
	public static void submit(KnifeVisual visual, float[] positions, float[] normals, PoseStack poseStack,
			SubmitNodeCollector submitNodeCollector, int lightCoords, int overlayCoords, boolean hasFoil,
			boolean mirrored) {
		KnifeMesh mesh = visual.mesh();
		boolean shiny = KnivesAsSwordsClient.runtime().config().shine;
		submitNodeCollector.submitCustomGeometry(poseStack, visual.renderType(hasFoil, shiny),
				(pose, buffer) -> KnifeGeometry.emit(mesh, positions, normals, pose, buffer, lightCoords,
						overlayCoords, mirrored));
	}

	@Override
	public void getExtents(Consumer<Vector3fc> output) {
		// Extents depend on the knife and are set per layer instead.
	}

	@Override
	public @Nullable Draw extractArgument(ItemStack stack) {
		// Not how knives get here (see KnifeReplacement), but the interface asks for an answer.
		KnifeVisual visual = KnifeReplacement.visualFor(stack);
		return visual != null ? new Draw(visual, null, ItemDisplayContext.NONE, 0, 0.0, 0.0, 0.0) : null;
	}
}
