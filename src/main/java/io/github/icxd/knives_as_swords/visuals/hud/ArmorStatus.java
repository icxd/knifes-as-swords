package io.github.icxd.knives_as_swords.visuals.hud;

import io.github.icxd.knives_as_swords.visuals.Setting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What the player is wearing and holding, each with how much use it has left: what the inventory
 * screen says, without opening it.
 */
public final class ArmorStatus extends HudWidget {
	private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET};
	private static final int ROW = 17;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int LOW = 0xFFFF5555;

	private final Setting.Toggle held = toggle("held", true);
	/** How what is left is written: as a count of uses, or as a share. */
	private final Setting.Choice shown = choice("shown", "uses", "percent");

	private final List<ItemStack> stacks = new ArrayList<>();
	private final List<String> texts = new ArrayList<>();

	public ArmorStatus() {
		super("armor_status", false, 0.99f, 0.6f);
	}

	private void gather(Minecraft client, boolean editing) {
		stacks.clear();
		texts.clear();
		for (EquipmentSlot slot : WORN) {
			add(client.player.getItemBySlot(slot));
		}
		if (held.on()) {
			add(client.player.getMainHandItem());
		}
		if (stacks.isEmpty() && editing) {
			// Something to get hold of while it is being placed.
			add(new ItemStack(Items.IRON_CHESTPLATE));
		}
	}

	private void add(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		stacks.add(stack);
		if (!stack.isDamageableItem()) {
			texts.add(stack.getCount() > 1 ? Integer.toString(stack.getCount()) : "");
		} else {
			int left = stack.getMaxDamage() - stack.getDamageValue();
			texts.add(shown.is("percent") ? Math.round(100f * left / Math.max(stack.getMaxDamage(), 1)) + "%"
					: Integer.toString(left));
		}
	}

	@Override
	protected int width(Minecraft client, boolean editing) {
		gather(client, editing);
		int widest = 0;
		for (String text : texts) {
			widest = Math.max(widest, client.font.width(text));
		}
		return stacks.isEmpty() ? 0 : 16 + (widest > 0 ? 3 + widest : 0);
	}

	@Override
	protected int height(Minecraft client, boolean editing) {
		return stacks.size() * ROW - 1;
	}

	@Override
	protected void draw(GuiGraphicsExtractor graphics, Minecraft client, int width, int height, boolean editing) {
		for (int i = 0; i < stacks.size(); i++) {
			ItemStack stack = stacks.get(i);
			int y = i * ROW;
			graphics.item(stack, 0, y);
			boolean low = stack.isDamageableItem()
					&& stack.getMaxDamage() - stack.getDamageValue() <= stack.getMaxDamage() / 10;
			graphics.text(client.font, texts.get(i), 19, y + 4, low ? LOW : TEXT);
		}
	}
}
