package io.github.icxd.knives_as_swords.visuals.hud;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;

/**
 * The effects the player is under, each with its name and the time it has left: what the
 * inventory screen says, without opening it.
 */
public final class PotionStatus extends HudWidget {
	private static final int ROW = 20;
	private static final int ICON = 18;
	private static final int TEXT = 0xFFFFFFFF;
	private static final int DIM = 0xFFA8A8B4;
	private static final int HARMFUL = 0xFFFF7A7A;

	private final List<MobEffectInstance> effects = new ArrayList<>();
	private final List<Component> names = new ArrayList<>();
	private final List<Component> times = new ArrayList<>();

	public PotionStatus() {
		super("potion_status", false, 0.99f, 0.25f);
	}

	private void gather(Minecraft client) {
		effects.clear();
		names.clear();
		times.clear();
		for (MobEffectInstance effect : client.player.getActiveEffects()) {
			if (!effect.showIcon()) {
				continue;
			}
			effects.add(effect);
		}
		effects.sort(null);
		float tickRate = client.level != null ? client.level.tickRateManager().tickrate() : 20f;
		for (MobEffectInstance effect : effects) {
			MutableComponent name = effect.getEffect().value().getDisplayName().copy();
			if (effect.getAmplifier() >= 1 && effect.getAmplifier() <= 9) {
				name.append(CommonComponents.SPACE)
						.append(Component.translatable("enchantment.level." + (effect.getAmplifier() + 1)));
			}
			names.add(name);
			times.add(MobEffectUtil.formatDuration(effect, 1f, tickRate));
		}
	}

	@Override
	protected int width(Minecraft client, boolean editing) {
		gather(client);
		if (effects.isEmpty()) {
			// Something to get hold of while it is being placed.
			return editing ? 80 : 0;
		}
		int widest = 0;
		for (int i = 0; i < effects.size(); i++) {
			widest = Math.max(widest, Math.max(client.font.width(names.get(i)), client.font.width(times.get(i))));
		}
		return ICON + 4 + widest;
	}

	@Override
	protected int height(Minecraft client, boolean editing) {
		return Math.max(effects.size(), 1) * ROW - 2;
	}

	@Override
	protected void draw(GuiGraphicsExtractor graphics, Minecraft client, int width, int height, boolean editing) {
		if (effects.isEmpty()) {
			graphics.text(client.font, Component.translatable("knives_as_swords.visual.potion_status"), 0, 5, DIM);
			return;
		}
		for (int i = 0; i < effects.size(); i++) {
			MobEffectInstance effect = effects.get(i);
			int y = i * ROW;
			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Hud.getMobEffectSprite(effect.getEffect()), 0, y, ICON, ICON);
			graphics.text(client.font, names.get(i), ICON + 4, y,
					effect.getEffect().value().isBeneficial() ? TEXT : HARMFUL);
			graphics.text(client.font, times.get(i), ICON + 4, y + client.font.lineHeight, DIM);
		}
	}
}
