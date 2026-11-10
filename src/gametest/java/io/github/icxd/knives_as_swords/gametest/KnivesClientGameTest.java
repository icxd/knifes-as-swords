package io.github.icxd.knives_as_swords.gametest;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.KnifeRuntime.ExtractorStatus;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.cs2.KnifeModel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;

/**
 * In-game smoke test, run with {@code ./gradlew runClientGameTest}.
 *
 * <p>Uses a made-up game folder whose package index names two knives and holds no data at all,
 * so it needs neither the real game nor the network: it covers finding the knives, the
 * {@code /knives} command, and that nothing is downloaded without the player agreeing.
 */
@SuppressWarnings("UnstableApiUsage")
public class KnivesClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path fakeGame = gameDir.resolve("fake-game");
		try {
			writeFakeGame(fakeGame);
			JsonObject config = new JsonObject();
			config.addProperty("cs2Dir", fakeGame.toString());
			Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("knives_as_swords.json"),
					config.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}

		KnifeRuntime runtime = KnivesAsSwordsClient.runtime();
		KnifeRuntime.State state = runtime.scan().join();
		check(state.install().isPresent(), "the configured game folder was not picked up");
		check(state.knives().stream().map(KnifeModel::id).toList().equals(List.of("alpha", "beta")),
				"unexpected knives: " + state.knives());

		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();

			runChatCommand(context, "/knives");
			context.waitTicks(10);
			context.takeScreenshot("knives-status");

			if (runtime.extractorStatus() == ExtractorStatus.NEEDS_CONSENT) {
				runChatCommand(context, "/knives export alpha");
				context.waitForScreen(ConfirmScreen.class);
				context.takeScreenshot("knives-consent");
				context.clickScreenButton("knives_as_swords.consent.decline");
				context.waitForScreen(null);
				context.waitTicks(10);
				context.takeScreenshot("knives-consent-declined");

				check(runtime.extractorStatus() == ExtractorStatus.NEEDS_CONSENT, "declining must not grant consent");
				check(!Files.exists(runtime.dataDir().resolve("tools")), "something was downloaded without consent");
			}
		}
	}

	private static void runChatCommand(ClientGameTestContext context, String command) {
		context.getInput().pressKey(options -> options.keyChat);
		context.waitForScreen(ChatScreen.class);
		context.getInput().typeChars(command);
		context.getInput().pressKey(InputConstants.KEY_RETURN);
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	/** Writes a package index that lists two knife models. The entries point at no data. */
	private static void writeFakeGame(Path root) throws IOException {
		ByteArrayOutputStream tree = new ByteArrayOutputStream();
		writeString(tree, "vmdl_c");
		for (String knife : new String[] {"alpha", "beta"}) {
			writeString(tree, "weapons/models/knife/knife_" + knife);
			writeString(tree, "weapon_knife_" + knife);
			ByteBuffer entry = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN);
			entry.putInt(0).putShort((short) 0).putShort((short) 0).putInt(0).putInt(0).putShort((short) 0xFFFF);
			tree.writeBytes(entry.array());
			tree.write(0); // end of this folder
		}
		tree.write(0); // end of this extension
		tree.write(0); // end of the index

		ByteBuffer header = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
		header.putInt(0x55AA1234).putInt(1).putInt(tree.size());

		Path vpk = root.resolve("game/csgo/pak01_dir.vpk");
		Files.createDirectories(vpk.getParent());
		ByteArrayOutputStream file = new ByteArrayOutputStream();
		file.writeBytes(header.array());
		file.writeBytes(tree.toByteArray());
		Files.write(vpk, file.toByteArray());
	}

	private static void writeString(ByteArrayOutputStream out, String value) {
		out.writeBytes(value.getBytes(StandardCharsets.UTF_8));
		out.write(0);
	}
}
