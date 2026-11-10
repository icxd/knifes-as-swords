package io.github.icxd.knives_as_swords.gametest;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.ModConfig;
import io.github.icxd.knives_as_swords.fx.CustomEffect;
import io.github.icxd.knives_as_swords.screen.ClickMenuScreen;
import io.github.icxd.knives_as_swords.screen.EffectScreen;
import io.github.icxd.knives_as_swords.screen.GradientScreen;
import io.github.icxd.knives_as_swords.sound.KnifeAudio;
import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import io.github.icxd.knives_as_swords.visuals.Visuals;
import io.github.icxd.knives_as_swords.visuals.combat.KillEffects;
import io.github.icxd.knives_as_swords.visuals.player.Halo;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Screenshots of real knives, for looking at while developing. Unlike the smoke test this needs
 * the actual game files and an installed extractor, so it only runs when asked to:
 *
 * <pre>KNIVES_VISUAL_EXTRACTOR=/path/to/Source2Viewer-CLI ./gradlew runClientGameTest</pre>
 *
 * <p>With {@code KNIVES_VISUAL_ONLY=effects} set as well, only the part about the knives' effects
 * runs; with {@code KNIVES_VISUAL_ONLY=visuals}, only the part about the other visuals and their
 * menu; with {@code KNIVES_VISUAL_ONLY=sounds}, only the part about the knife sounds.
 *
 * <p>The screenshots show converted game content. They land in the (ignored) build folder and
 * must stay there.
 */
@SuppressWarnings("UnstableApiUsage")
public class KnivesVisualGameTest implements FabricClientGameTest {
	private static final Item[] SWORDS = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.COPPER_SWORD, Items.IRON_SWORD,
			Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};

	private static JsonObject finish(String name, int pattern, float wear) {
		JsonObject sword = new JsonObject();
		sword.addProperty("finish", name);
		sword.addProperty("pattern", pattern);
		sword.addProperty("wear", wear);
		return sword;
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		String extractor = System.getenv("KNIVES_VISUAL_EXTRACTOR");
		if (extractor == null || extractor.isBlank()) {
			return;
		}

		JsonObject config = new JsonObject();
		config.addProperty("extractorPath", extractor);
		// The iron sword stays a sword, as a reference for where and how big things should be.
		JsonObject ironSword = new JsonObject();
		ironSword.addProperty("enabled", false);
		JsonObject swords = new JsonObject();
		swords.add("minecraft:iron_sword", ironSword);
		// One finish of each kind of paint job the game has, at different amounts of wear.
		swords.add("minecraft:golden_sword", finish("aq_oiled", 387, 0.03f));
		swords.add("minecraft:diamond_sword", finish("aa_fade", 0, 0.01f));
		swords.add("minecraft:netherite_sword", finish("an_tiger_orange", 0, 0.05f));
		swords.add("minecraft:copper_sword", finish("am_doppler_phase2", 0, 0.01f));
		config.add("swords", swords);
		try {
			Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("knives_as_swords.json"),
					config.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		KnifeRuntime runtime = KnivesAsSwordsClient.runtime();
		KnifeRuntime.State state = runtime.scan().join();
		if (state.install().isEmpty()) {
			throw new AssertionError("the visual test needs the real game installed");
		}

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("time set noon");
			for (Item sword : SWORDS) {
				singleplayer.getServer().runCommand("give @p " + net.minecraft.core.registries.BuiltInRegistries.ITEM
						.getKey(sword));
			}
			context.waitTicks(10);
			// Asking for a knife starts loading it; wait until every one is there.
			for (Item sword : SWORDS) {
				if (sword != Items.IRON_SWORD) {
					context.waitFor(client -> KnivesAsSwordsClient.visuals().forItem(sword) != null, 20 * 120);
				}
			}
			context.waitTicks(5);

			String only = System.getenv("KNIVES_VISUAL_ONLY");
			if (only == null || only.equals("effects")) {
				effects(context, singleplayer, runtime);
			}
			if (only == null || only.equals("visuals")) {
				visuals(context, singleplayer);
			}
			if (only == null || only.equals("sounds")) {
				sounds(context);
			}
			if (only != null) {
				return;
			}

			for (int slot = 0; slot < SWORDS.length; slot++) {
				int selected = slot;
				context.runOnClient(client -> client.player.getInventory().setSelectedSlot(selected));
				context.waitTicks(4);
				context.takeScreenshot("visual-first-person-" + slot + "-draw");
				context.waitTicks(30);
				context.takeScreenshot("visual-first-person-" + slot + "-idle");
			}

			// The same knife without a finish and back: a wooden grip on bare steel, which has to look
			// like wood, not like more steel.
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(2));
			for (String finish : new String[] {"", "am_doppler_phase2"}) {
				context.runOnClient(client -> runtime.updateConfig(
						settings -> settings.swords.get("minecraft:copper_sword").finish = finish));
				context.waitTicks(2);
				context.waitFor(client -> KnivesAsSwordsClient.visuals().forItem(Items.COPPER_SWORD) != null, 20 * 120);
				if (finish.isEmpty()) {
					context.waitTicks(40);
					context.takeScreenshot("visual-first-person-2-unpainted");
				}
			}

			// The butterfly knife: its parts move against each other, so it shows whether bones are right.
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(5));
			context.waitTicks(40);
			context.getInput().pressKey(options -> options.keyAttack);
			// A slash is over in a few ticks, most of them spent out of view.
			context.waitTicks(2);
			context.takeScreenshot("visual-attack-a");
			context.waitTicks(1);
			context.takeScreenshot("visual-attack-b");
			context.waitTicks(40);
			for (int i = 0; i < 3; i++) {
				if (i == 0) {
					context.getInput().pressKey(InputConstants.KEY_G);
				}
				context.waitTicks(15);
				context.takeScreenshot("visual-inspect-" + i);
			}
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(6));
			context.waitTicks(40);

			// Without the knife's own animations it is held like a sword.
			context.runOnClient(client -> runtime.updateConfig(settings -> settings.firstPersonAnimations = false));
			for (int slot : new int[] {4, 6, 3}) {
				context.runOnClient(client -> client.player.getInventory().setSelectedSlot(slot));
				context.waitTicks(15);
				context.takeScreenshot("visual-static-" + slot);
			}
			context.runOnClient(client -> runtime.updateConfig(settings -> settings.firstPersonAnimations = true));
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(6));
			context.waitTicks(30);

			// The settings screen, opened with its key; it starts on the sword in hand.
			context.getInput().pressKey(InputConstants.KEY_K);
			context.waitForScreen(io.github.icxd.knives_as_swords.screen.KnivesScreen.class);
			context.waitTicks(30);
			context.takeScreenshot("visual-settings");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitForScreen(null);
			context.waitTicks(5);

			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
			context.waitTicks(5);
			context.takeScreenshot("visual-third-person-front");
			context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			context.waitTicks(5);
			context.takeScreenshot("visual-third-person-back");
			context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

			context.getInput().pressKey(options -> options.keyInventory);
			context.waitTicks(10);
			context.takeScreenshot("visual-inventory");
			context.getInput().pressKey(InputConstants.KEY_ESCAPE);
			context.waitTicks(5);

			// Dropped items, looked at from above.
			singleplayer.getServer().runCommand(
					"execute at @p run summon item ~ ~ ~2 {Item:{id:\"minecraft:golden_sword\"},PickupDelay:32767}");
			singleplayer.getServer().runCommand(
					"execute at @p run summon item ~1 ~ ~2 {Item:{id:\"minecraft:netherite_sword\"},PickupDelay:32767}");
			context.getInput().lookAt(0f, 40f);
			context.waitTicks(40);
			context.takeScreenshot("visual-dropped");
			singleplayer.getServer().runCommand("kill @e[type=item]");

			// Holders seen from their right side: a vanilla sword for reference next to knives.
			String[] held = {"iron_sword", "netherite_sword", "golden_sword", "diamond_sword"};
			for (int i = 0; i < held.length; i++) {
				singleplayer.getServer().runCommand("execute at @p run summon armor_stand ~" + (i * 1.5 - 2.25)
						+ " ~ ~3 {ShowArms:1b,NoGravity:1b,Rotation:[90f,0f],equipment:{mainhand:{id:\"minecraft:"
						+ held[i] + "\",count:1}}}");
			}
			singleplayer.getServer().runCommand("execute at @p run summon husk ~-1 ~ ~5 {NoAI:1b,Rotation:[90f,0f],"
					+ "equipment:{mainhand:{id:\"minecraft:iron_sword\",count:1}}}");
			singleplayer.getServer().runCommand("execute at @p run summon husk ~1 ~ ~5 {NoAI:1b,Rotation:[90f,0f],"
					+ "equipment:{mainhand:{id:\"minecraft:netherite_sword\",count:1}}}");
			context.getInput().lookAt(0f, 10f);
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(8));
			context.waitTicks(30);
			context.takeScreenshot("visual-holders");
		}
	}

	/**
	 * The knife's sounds. A picture cannot show them, so this goes by what the mod itself counts:
	 * they have to be there, and taking the knife out, swinging it and looking at it each have to
	 * hand one to the game's sound engine. What the engine made of the first is in the log.
	 */
	private static void sounds(ClientGameTestContext context) {
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(5));
		context.waitFor(client -> KnifeAudio.ready(), 20 * 120);
		context.waitTicks(20);
		int[] played = new int[2];
		context.runOnClient(client -> played[0] = KnifeAudio.played());

		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(6));
		context.waitTicks(30);
		context.getInput().pressKey(options -> options.keyAttack);
		context.waitTicks(30);
		context.getInput().pressKey(InputConstants.KEY_G);
		context.waitTicks(20);
		context.runOnClient(client -> played[1] = KnifeAudio.played());
		if (played[1] - played[0] < 3) {
			throw new AssertionError("taking the knife out, a swing and a look at it should each make a sound, but only "
					+ (played[1] - played[0]) + " were played");
		}
		context.waitTicks(60);
	}

	/**
	 * The visuals that have nothing to do with knives, and the menu they are switched on in. For the
	 * pictures every one of them is on, also those that are off unless a player asks for them.
	 */
	private static void visuals(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("time set midnight");
		context.runOnClient(client -> client.player.getInventory().setSelectedSlot(6));
		context.runOnClient(client -> Visuals.all().forEach(visual -> visual.set(true)));
		context.getInput().lookAt(0f, 50f);
		context.waitTicks(20);
		// Looking at the ground: the crosshair, the outline round a block, and everything on the display.
		context.takeScreenshot("visuals-first-person");

		// The menu, and one visual's settings unfolded by a right-click on it.
		context.getInput().pressKey(InputConstants.KEY_RSHIFT);
		context.waitForScreen(ClickMenuScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("visuals-menu");
		int[] scale = new int[1];
		context.runOnClient(client -> scale[0] = client.getWindow().getGuiScale());
		// The second line of the first panel: below its title and the knives' entry.
		context.getInput().setCursorPos(50 * scale[0], 73 * scale[0]);
		context.waitTicks(2);
		context.getInput().pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		context.waitTicks(5);
		context.takeScreenshot("visuals-menu-settings");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		// Where a visual's colours are put together.
		context.runOnClient(client -> {
			for (Setting setting : Visuals.get(KillEffects.class).settings()) {
				if (setting instanceof Setting.Colors colors) {
					client.gui.setScreen(new GradientScreen(null, Component.literal("Kill Effects: Colours"), colors, () -> { }));
					break;
				}
			}
		});
		context.waitForScreen(GradientScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("visuals-colours");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		// A hit: sparks, a ring, a number, and the panel about what is being fought.
		context.getInput().lookAt(0f, 5f);
		singleplayer.getServer().runCommand("execute at @p run summon husk ~ ~ ~1.6 {NoAI:1b,Silent:1b,Rotation:[180f,0f]}");
		context.waitTicks(15);
		context.getInput().pressKey(options -> options.keyAttack);
		for (int i = 0; i < 6; i++) {
			context.waitTicks(i < 3 ? 1 : 3);
			context.takeScreenshot("visuals-hit-" + i);
		}
		singleplayer.getServer().runCommand("kill @e[type=husk]");
		context.waitTicks(30);

		// A kill, in each of its styles.
		for (String style : new String[] {"burst", "pillar", "bolts", "souls"}) {
			context.runOnClient(client -> Visuals.get(KillEffects.class).read(Map.of("enabled", "true", "style", style)));
			singleplayer.getServer().runCommand(
					"execute at @p run summon husk ~ ~ ~2.4 {NoAI:1b,Silent:1b,Health:1f,Rotation:[180f,0f]}");
			context.waitTicks(15);
			context.getInput().pressKey(options -> options.keyAttack);
			int[] waits = {2, 3, 4, 6};
			for (int i = 0; i < waits.length; i++) {
				context.waitTicks(waits[i]);
				context.takeScreenshot("visuals-kill-" + style + "-" + i);
			}
			context.waitTicks(30);
		}
		singleplayer.getServer().runCommand("kill @e[type=item]");
		singleplayer.getServer().runCommand("kill @e[type=experience_orb]");

		// Seen from outside: the trail behind the player, the rings of a jump, and what there is to wear.
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.getInput().holdKey(options -> options.keyUp);
		context.waitTicks(12);
		context.takeScreenshot("visuals-trail");
		context.getInput().holdKeyFor(options -> options.keyJump, 3);
		context.waitTicks(3);
		context.takeScreenshot("visuals-jump-0");
		context.waitTicks(10);
		context.takeScreenshot("visuals-jump-1");
		context.getInput().releaseKey(options -> options.keyUp);
		context.waitTicks(15);
		for (String style : new String[] {"halo", "hat", "wisps"}) {
			context.runOnClient(client -> Visuals.get(Halo.class).read(Map.of("enabled", "true", "style", style)));
			context.waitTicks(10);
			context.takeScreenshot("visuals-halo-" + style);
		}
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

		// By day: the tinted sky and fog.
		singleplayer.getServer().runCommand("time set noon");
		context.getInput().lookAt(0f, -25f);
		context.waitTicks(30);
		context.takeScreenshot("visuals-sky");

		// Back to how a player finds things.
		context.runOnClient(client -> {
			for (Visual visual : Visuals.all()) {
				visual.read(Map.of());
			}
		});
		context.getInput().lookAt(0f, 5f);
		context.waitTicks(10);
	}

	/**
	 * Trails, afterimages, bolts, sparks and glow. They only show in motion, and a slash is over in
	 * a blink, so every move is photographed several times in a row.
	 */
	private static void effects(ClientGameTestContext context, TestSingleplayerContext singleplayer,
			KnifeRuntime runtime) {
		// Light on dark shows best; and something to hit.
		singleplayer.getServer().runCommand("time set midnight");
		context.getInput().lookAt(0f, 5f);
		context.waitTicks(20);

		// Each sword has a look of its own by default: copper, golden, diamond, netherite.
		for (int slot : new int[] {2, 4, 5, 6}) {
			context.runOnClient(client -> client.player.getInventory().setSelectedSlot(slot));
			context.waitTicks(3);
			context.takeScreenshot("effects-" + slot + "-draw");
			context.waitTicks(30);
			context.takeScreenshot("effects-" + slot + "-rest");
			context.getInput().pressKey(options -> options.keyAttack);
			for (int i = 0; i < 6; i++) {
				context.waitTicks(1);
				context.takeScreenshot("effects-" + slot + "-swing-" + i);
			}
			context.waitTicks(20);
		}

		// The other looks, on the same knife.
		for (String effect : new String[] {"storm", "venom", "prism"}) {
			context.runOnClient(client -> runtime.updateConfig(
					settings -> settings.swords.get("minecraft:netherite_sword").effect = effect));
			context.waitTicks(25);
			context.takeScreenshot("effects-" + effect + "-rest");
			context.getInput().pressKey(options -> options.keyAttack);
			for (int i = 0; i < 5; i++) {
				context.waitTicks(1);
				context.takeScreenshot("effects-" + effect + "-swing-" + i);
			}
			context.waitTicks(20);
		}

		// Looking the knife over, and hitting something.
		context.getInput().pressKey(InputConstants.KEY_G);
		for (int i = 0; i < 3; i++) {
			context.waitTicks(12);
			context.takeScreenshot("effects-inspect-" + i);
		}
		context.waitTicks(100);
		singleplayer.getServer().runCommand("execute at @p run summon husk ~ ~ ~1.6 {NoAI:1b,Silent:1b,Rotation:[180f,0f]}");
		context.waitTicks(10);
		context.getInput().pressKey(options -> options.keyAttack);
		for (int i = 0; i < 8; i++) {
			context.waitTicks(1);
			context.takeScreenshot("effects-hit-" + i);
		}
		context.waitTicks(20);

		// Seen from outside, the swing leaves its trail in the world, and a hit throws sparks there.
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		context.waitTicks(15);
		context.takeScreenshot("effects-outside-rest");
		context.getInput().pressKey(options -> options.keyAttack);
		for (int i = 0; i < 7; i++) {
			context.waitTicks(1);
			context.takeScreenshot("effects-outside-hit-" + i);
		}
		context.waitTicks(20);
		singleplayer.getServer().runCommand("kill @e[type=husk]");
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(15);
		context.getInput().pressKey(options -> options.keyAttack);
		for (int i = 0; i < 6; i++) {
			context.waitTicks(1);
			context.takeScreenshot("effects-outside-swing-" + i);
		}
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.waitTicks(10);

		// Knives that are simply there: dropped, and in other hands.
		singleplayer.getServer().runCommand(
				"execute at @p run summon item ~-0.6 ~ ~2 {Item:{id:\"minecraft:diamond_sword\"},PickupDelay:32767}");
		singleplayer.getServer().runCommand("execute at @p run summon armor_stand ~0.8 ~ ~2.5 {ShowArms:1b,"
				+ "NoGravity:1b,Rotation:[90f,0f],equipment:{mainhand:{id:\"minecraft:netherite_sword\",count:1}}}");
		context.getInput().lookAt(0f, 25f);
		context.waitTicks(40);
		context.takeScreenshot("effects-lying-and-held");

		// Minecraft has a second way of drawing see-through things in the world, which takes pipelines
		// of its own; without them the game would stop right here.
		context.runOnClient(client -> client.options.improvedTransparency().set(true));
		context.waitTicks(20);
		context.takeScreenshot("effects-improved-transparency-lying-and-held");
		singleplayer.getServer().runCommand("kill @e[type=armor_stand]");
		context.getInput().lookAt(0f, 5f);
		context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(15);
		context.getInput().pressKey(options -> options.keyAttack);
		for (int i = 0; i < 5; i++) {
			context.waitTicks(1);
			context.takeScreenshot("effects-improved-transparency-swing-" + i);
		}
		context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
		context.runOnClient(client -> client.options.improvedTransparency().set(false));
		context.waitTicks(20);

		// The settings screen shows the look it offers.
		context.getInput().pressKey(InputConstants.KEY_K);
		context.waitForScreen(io.github.icxd.knives_as_swords.screen.KnivesScreen.class);
		context.waitTicks(30);
		context.takeScreenshot("effects-settings");
		context.waitTicks(12);
		context.takeScreenshot("effects-settings-later");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.waitTicks(5);

		// The editor for a look of one's own. Its knife slashes by itself every few seconds.
		context.runOnClient(client -> client.gui.setScreen(new EffectScreen(null, Items.NETHERITE_SWORD)));
		context.waitForScreen(EffectScreen.class);
		context.waitTicks(8);
		for (int i = 0; i < 12; i++) {
			context.waitTicks(2);
			context.takeScreenshot("effects-editor-" + i);
		}
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);

		// A look put together by hand: three colours, bolts all over, sparks that rise.
		context.runOnClient(client -> runtime.updateConfig(settings -> {
			ModConfig.SwordSetting sword = settings.swords.get("minecraft:netherite_sword");
			CustomEffect custom = new CustomEffect();
			custom.bolts = 1f;
			custom.sparkDrift = 0.8f;
			custom.colors.add(new CustomEffect.ColorStop(0f, "#00FF66"));
			custom.colors.add(new CustomEffect.ColorStop(0.5f, "#FFE600"));
			custom.colors.add(new CustomEffect.ColorStop(1f, "#FF2A00"));
			sword.effect = "custom";
			sword.customEffect = custom;
		}));
		context.waitTicks(25);
		context.takeScreenshot("effects-custom-rest");
		context.getInput().pressKey(options -> options.keyAttack);
		for (int i = 0; i < 5; i++) {
			context.waitTicks(1);
			context.takeScreenshot("effects-custom-swing-" + i);
		}
		context.waitTicks(20);
		context.runOnClient(client -> client.gui.setScreen(new EffectScreen(null, Items.NETHERITE_SWORD)));
		context.waitForScreen(EffectScreen.class);
		context.waitTicks(20);
		for (int i = 0; i < 6; i++) {
			context.waitTicks(2);
			context.takeScreenshot("effects-editor-custom-" + i);
		}
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.getInput().pressKey(InputConstants.KEY_K);
		context.waitForScreen(io.github.icxd.knives_as_swords.screen.KnivesScreen.class);
		context.waitTicks(20);
		context.takeScreenshot("effects-settings-custom");
		context.getInput().pressKey(InputConstants.KEY_ESCAPE);
		context.waitForScreen(null);
		context.waitTicks(5);

		singleplayer.getServer().runCommand("kill @e[type=husk]");
		singleplayer.getServer().runCommand("kill @e[type=item]");
		context.runOnClient(client -> runtime.updateConfig(
				settings -> settings.swords.get("minecraft:netherite_sword").effect = ""));
		singleplayer.getServer().runCommand("time set noon");
		context.waitTicks(20);
	}
}
