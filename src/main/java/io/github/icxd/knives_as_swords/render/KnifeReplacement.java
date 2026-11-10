package io.github.icxd.knives_as_swords.render;

import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.fx.FxStyle;
import io.github.icxd.knives_as_swords.fx.WeaponFx;
import io.github.icxd.knives_as_swords.mixin.ItemStackRenderStateAccessor;
import io.github.icxd.knives_as_swords.mixin.LayerRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Decides which item stacks are drawn as knives, and swaps a sword's own render layers for the
 * knife. Purely a matter of drawing: the stack itself is never touched.
 */
public final class KnifeReplacement {
	private KnifeReplacement() {
	}

	/** Whether this stack is a sword at all; cheap, because it runs for every item drawn. */
	public static boolean isSword(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		if (stack.is(ItemTags.SWORDS)) {
			return true;
		}
		// Tags come from the server and are missing before a world is joined (e.g. on the title screen).
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		return id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) && id.getPath().endsWith("_sword");
	}

	/** The knife to draw instead of this stack, or {@code null} to leave it alone. */
	public static @Nullable KnifeVisual visualFor(ItemStack stack) {
		KnifeRuntime runtime = KnivesAsSwordsClient.runtime();
		if (runtime == null || !isSword(stack)) {
			return null;
		}
		return KnivesAsSwordsClient.visuals().forItem(stack.getItem());
	}

	/**
	 * Replaces the layers that vanilla just appended for a sword (those from {@code firstLayer}
	 * on) with a single knife layer. The sword's own item transform is kept, so the knife ends up
	 * wherever this resource pack puts a sword in the given context.
	 *
	 * @param owner who the item belongs to, if anybody; only read
	 */
	public static void replaceLayers(ItemStackRenderState output, int firstLayer, ItemStack stack,
			ItemDisplayContext context, @Nullable ItemOwner owner) {
		ItemStackRenderStateAccessor state = (ItemStackRenderStateAccessor) output;
		int layerCount = state.knives$getActiveLayerCount();
		if (layerCount <= firstLayer) {
			return;
		}
		KnifeVisual visual = visualFor(stack);
		if (visual == null) {
			return;
		}

		ItemStackRenderState.LayerRenderState[] layers = state.knives$getLayers();
		ItemTransform swordTransform = ((LayerRenderStateAccessor) layers[firstLayer]).knives$getItemTransform();
		for (int i = firstLayer; i < layerCount; i++) {
			layers[i].clear();
		}
		state.knives$setActiveLayerCount(firstLayer);

		// Inventory slots are drawn once and kept as pictures; effects are for knives that are really there.
		FxStyle effects = context == ItemDisplayContext.GUI ? null : WeaponFx.styleFor(stack.getItem());
		int holder = 0;
		Vec3 holderAt = Vec3.ZERO;
		if (effects != null && owner != null && (context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
				|| context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)) {
			LivingEntity entity = owner.asLivingEntity();
			if (entity != null) {
				// Where the holder is drawn this frame: a swung blade is followed relative to that.
				holder = entity.getId();
				holderAt = entity.getPosition(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
			}
		}

		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		layer.setupSpecialModel(KnifeItemRenderer.INSTANCE,
				new KnifeItemRenderer.Draw(visual, effects, context, holder, holderAt.x, holderAt.y, holderAt.z));
		layer.setItemTransform(swordTransform);
		layer.setLocalTransform(visual.cubeTransform(context));
		layer.setExtents(visual::extents);
		// A solid object rather than a flat sprite: light it from the side in inventories.
		layer.setUsesBlockLight(true);
		if (stack.hasFoil()) {
			layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
			output.setAnimated();
		}
		// Cached item pictures (inventory slots) must be redrawn when the knife or its finish changes.
		output.appendModelIdentityElement(visual);
	}
}
