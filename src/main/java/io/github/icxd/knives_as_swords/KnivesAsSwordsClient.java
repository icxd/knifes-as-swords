package io.github.icxd.knives_as_swords;

import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class KnivesAsSwordsClient implements ClientModInitializer {
	public static final String MOD_ID = "knives_as_swords";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static KnifeRuntime runtime;

	/** The mod's runtime state; available once the client has initialized. */
	public static KnifeRuntime runtime() {
		return runtime;
	}

	@Override
	public void onInitializeClient() {
		FabricLoader loader = FabricLoader.getInstance();
		Path configFile = loader.getConfigDir().resolve(MOD_ID + ".json");
		// Holds the downloaded extractor and everything converted from the local game files.
		Path dataDir = loader.getGameDir().resolve(MOD_ID);

		KnifeRuntime runtime = new KnifeRuntime(configFile, dataDir);
		KnivesAsSwordsClient.runtime = runtime;
		runtime.scan();

		ClientCommandRegistrationCallback.EVENT.register(
				(dispatcher, buildContext) -> KnivesCommand.register(dispatcher, runtime));
	}
}
