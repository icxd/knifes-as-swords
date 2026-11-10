package io.github.icxd.knives_as_swords;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.icxd.knives_as_swords.fx.WeaponFx;
import io.github.icxd.knives_as_swords.fx.WorldFx;
import io.github.icxd.knives_as_swords.render.FirstPersonKnife;
import io.github.icxd.knives_as_swords.render.KnifePipelines;
import io.github.icxd.knives_as_swords.render.KnifeVisuals;
import io.github.icxd.knives_as_swords.screen.ClickMenuScreen;
import io.github.icxd.knives_as_swords.screen.KnifePreviewRenderer;
import io.github.icxd.knives_as_swords.screen.KnivesScreen;
import io.github.icxd.knives_as_swords.ui.UiPipelines;
import io.github.icxd.knives_as_swords.visuals.AllVisuals;
import io.github.icxd.knives_as_swords.visuals.Visuals;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KnivesAsSwordsClient implements ClientModInitializer {
	public static final String MOD_ID = "knives_as_swords";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static KnifeRuntime runtime;
	private static KnifeVisuals visuals;
	/** The setup hint is shown once per game session, not on every world joined. */
	private static boolean hinted;

	/** The mod's runtime state; available once the client has initialized. */
	public static KnifeRuntime runtime() {
		return runtime;
	}

	/** The knives loaded for drawing; available once the client has initialized. */
	public static KnifeVisuals visuals() {
		return visuals;
	}

	@Override
	public void onInitializeClient() {
		FabricLoader loader = FabricLoader.getInstance();
		Path configFile = loader.getConfigDir().resolve(MOD_ID + ".json");
		// Holds the downloaded extractor and everything converted from the local game files.
		Path dataDir = loader.getGameDir().resolve(MOD_ID);

		KnifeRuntime runtime = new KnifeRuntime(configFile, dataDir);
		KnivesAsSwordsClient.visuals = new KnifeVisuals(runtime);
		KnivesAsSwordsClient.runtime = runtime;
		runtime.scan();

		ClientCommandRegistrationCallback.EVENT.register(
				(dispatcher, buildContext) -> KnivesCommand.register(dispatcher, runtime));

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "knives"));
		KeyMapping inspectKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key." + MOD_ID + ".inspect", InputConstants.KEY_G, category));
		KeyMapping settingsKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key." + MOD_ID + ".settings", InputConstants.KEY_K, category));
		KeyMapping menuKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key." + MOD_ID + ".menu", InputConstants.KEY_RSHIFT, category));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Visuals.tick(client);
			while (inspectKey.consumeClick()) {
				FirstPersonKnife.inspect();
			}
			while (settingsKey.consumeClick()) {
				client.gui.setScreen(new KnivesScreen(null));
			}
			while (menuKey.consumeClick()) {
				client.gui.setScreen(new ClickMenuScreen(null));
			}
		});

		// The game was found but nothing can be shown yet: say once where to go, since nothing is
		// ever downloaded without being asked for.
		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> client.execute(() -> {
			if (hinted || client.player == null) {
				return;
			}
			boolean gameFound = runtime.state().map(state -> state.install().isPresent()).orElse(false);
			if (gameFound && runtime.config().enabled
					&& runtime.extractorStatus() == KnifeRuntime.ExtractorStatus.NEEDS_CONSENT) {
				hinted = true;
				client.player.sendSystemMessage(Component.translatable(MOD_ID + ".hint.setup",
						settingsKey.getTranslatedKeyMessage()));
			}
		}));

		// The shader knives are drawn with has to be known before the game compiles its shaders.
		KnifePipelines.init();
		WeaponFx.init();
		UiPipelines.init();

		// Everything else the mod draws: each a visual of its own, switched on and off in the menu.
		AllVisuals.register();
		Visuals.load(runtime);
		WorldFx.paint(Visuals::world);

		// The turnable knife in the settings screen.
		PictureInPictureRendererRegistry.register(context -> new KnifePreviewRenderer());
	}
}
