package io.github.icxd.knives_as_swords.visuals;

import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.screen.ClickMenuScreen;
import io.github.icxd.knives_as_swords.visuals.combat.DamageNumbers;
import io.github.icxd.knives_as_swords.visuals.combat.HitEffects;
import io.github.icxd.knives_as_swords.visuals.combat.KillEffects;
import io.github.icxd.knives_as_swords.visuals.combat.KnifeSounds;
import io.github.icxd.knives_as_swords.visuals.combat.Knives;
import io.github.icxd.knives_as_swords.visuals.hud.ArmorStatus;
import io.github.icxd.knives_as_swords.visuals.hud.Keystrokes;
import io.github.icxd.knives_as_swords.visuals.hud.PotionStatus;
import io.github.icxd.knives_as_swords.visuals.hud.TargetHud;
import io.github.icxd.knives_as_swords.visuals.hud.TextWidget;
import io.github.icxd.knives_as_swords.visuals.player.Halo;
import io.github.icxd.knives_as_swords.visuals.player.JumpRings;
import io.github.icxd.knives_as_swords.visuals.player.PlayerTrail;
import io.github.icxd.knives_as_swords.visuals.world.Ambience;
import io.github.icxd.knives_as_swords.visuals.world.BlockOutline;
import io.github.icxd.knives_as_swords.visuals.world.Crosshair;
import io.github.icxd.knives_as_swords.visuals.world.HurtTint;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Every visual the mod has, in the order the menu lists them, and the few places where one of
 * them needs more from the game than the moments every visual gets.
 */
public final class AllVisuals {
	private AllVisuals() {
	}

	/** Has to be called once, while the mod initializes. */
	public static void register() {
		Visuals.add(new Knives());
		Visuals.add(new KnifeSounds());
		Visuals.add(new HitEffects());
		Visuals.add(new KillEffects());
		Visuals.add(new DamageNumbers());

		Visuals.add(new PlayerTrail());
		Visuals.add(new JumpRings());
		Visuals.add(new Halo());

		Crosshair crosshair = new Crosshair();
		BlockOutline blockOutline = new BlockOutline();
		Ambience ambience = new Ambience();
		Visuals.add(crosshair);
		Visuals.add(blockOutline);
		Visuals.add(ambience);
		Visuals.add(new HurtTint());

		Visuals.add(new Keystrokes());
		Visuals.add(new TextWidget.Cps());
		Visuals.add(new TextWidget.Fps());
		Visuals.add(new ArmorStatus());
		Visuals.add(new PotionStatus());
		Visuals.add(new TargetHud());

		// Everything on the heads-up display, drawn on top of the game's own.
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(KnivesAsSwordsClient.MOD_ID, "visuals"),
				(graphics, deltaTracker) -> Visuals.hud(graphics, ClickMenuScreen.showing()));
		// The crosshair takes the place of the game's own, where the game would draw one at all.
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, deltaTracker) -> {
			Minecraft client = Minecraft.getInstance();
			if (crosshair.replaces(client)) {
				crosshair.draw(graphics, client);
			} else {
				vanilla.extractRenderState(graphics, deltaTracker);
			}
		});
		// The block outline takes over from the game's, around the same block.
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, state) -> !blockOutline.on() || blockOutline.outline(state));
		// Sky and fog are recoloured once the game has worked them out for the frame.
		LevelExtractionEvents.END_EXTRACTION.register(context -> {
			if (ambience.on()) {
				ambience.tint(context.levelState());
			}
		});
	}
}
