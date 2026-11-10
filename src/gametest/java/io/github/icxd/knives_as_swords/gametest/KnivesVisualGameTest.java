package io.github.icxd.knives_as_swords.gametest;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Screenshots of real knives, for looking at while developing. Unlike the smoke test this needs
 * the actual game files and an installed extractor, so it only runs when asked to:
 *
 * <pre>KNIVES_VISUAL_EXTRACTOR=/path/to/Source2Viewer-CLI ./gradlew runClientGameTest</pre>
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
}
