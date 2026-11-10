package io.github.icxd.knives_as_swords;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.icxd.knives_as_swords.KnifeRuntime.ExtractorStatus;
import io.github.icxd.knives_as_swords.KnifeRuntime.State;
import io.github.icxd.knives_as_swords.cs2.Cs2Install;
import io.github.icxd.knives_as_swords.cs2.KnifeModel;
import io.github.icxd.knives_as_swords.extract.ExtractorRelease;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * The client-side {@code /knives} command: a development and troubleshooting aid next to the
 * settings screen. It never talks to the server.
 */
final class KnivesCommand {
	private static final String PREFIX = KnivesAsSwordsClient.MOD_ID + ".";

	private KnivesCommand() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher, KnifeRuntime runtime) {
		dispatcher.register(ClientCommands.literal("knives")
				.executes(context -> status(context.getSource(), runtime))
				.then(ClientCommands.literal("list")
						.executes(context -> list(context.getSource(), runtime)))
				.then(ClientCommands.literal("rescan")
						.executes(context -> rescan(context.getSource(), runtime)))
				.then(ClientCommands.literal("export")
						.then(ClientCommands.argument("knife", StringArgumentType.word())
								.suggests((context, builder) -> SharedSuggestionProvider.suggest(
										runtime.state().stream()
												.flatMap(state -> state.knives().stream())
												.map(KnifeModel::id),
										builder))
								.executes(context -> export(context.getSource(), runtime,
										StringArgumentType.getString(context, "knife"))))));
	}

	private static int status(FabricClientCommandSource source, KnifeRuntime runtime) {
		Optional<State> state = runtime.state();
		if (state.isEmpty()) {
			source.sendFeedback(text("status.scanning"));
			return 0;
		}

		Optional<Cs2Install> install = state.get().install();
		if (install.isEmpty()) {
			source.sendError(text("status.game_missing"));
			return 0;
		}
		source.sendFeedback(text("status.game_found", install.get().root().toString(), install.get().buildId()));
		source.sendFeedback(text("status.knives", state.get().knives().size()));
		source.sendFeedback(text("status.extractor." + runtime.extractorStatus().name().toLowerCase(Locale.ROOT)));
		source.sendFeedback(text("status.data_dir", runtime.dataDir().toString()));
		return Command.SINGLE_SUCCESS;
	}

	private static int list(FabricClientCommandSource source, KnifeRuntime runtime) {
		Optional<State> state = runtime.state();
		if (state.isEmpty()) {
			source.sendFeedback(text("status.scanning"));
			return 0;
		}
		if (state.get().install().isEmpty()) {
			source.sendError(text("status.game_missing"));
			return 0;
		}

		source.sendFeedback(text("list.header", state.get().knives().size()));
		source.sendFeedback(Component.literal(
				state.get().knives().stream().map(KnifeModel::id).collect(Collectors.joining(", "))));
		return state.get().knives().size();
	}

	private static int rescan(FabricClientCommandSource source, KnifeRuntime runtime) {
		Minecraft client = source.getClient();
		source.sendFeedback(text("rescan.started"));
		runtime.scan().thenRunAsync(() -> status(source, runtime), client);
		return Command.SINGLE_SUCCESS;
	}

	private static int export(FabricClientCommandSource source, KnifeRuntime runtime, String knifeId) {
		Optional<State> state = runtime.state();
		if (state.isEmpty()) {
			source.sendFeedback(text("status.scanning"));
			return 0;
		}
		if (state.get().install().isEmpty()) {
			source.sendError(text("status.game_missing"));
			return 0;
		}
		Optional<KnifeModel> knife = state.get().knife(knifeId);
		if (knife.isEmpty()) {
			source.sendError(text("export.unknown_knife", knifeId));
			return 0;
		}

		Cs2Install install = state.get().install().get();
		ExtractorStatus extractor = runtime.extractorStatus();
		if (extractor == ExtractorStatus.UNSUPPORTED_PLATFORM) {
			source.sendError(text("status.extractor.unsupported_platform"));
			return 0;
		}
		if (extractor == ExtractorStatus.NEEDS_CONSENT) {
			askForDownload(source, runtime, () -> startExport(source, runtime, install, knife.get()));
			return Command.SINGLE_SUCCESS;
		}

		startExport(source, runtime, install, knife.get());
		return Command.SINGLE_SUCCESS;
	}

	private static void startExport(FabricClientCommandSource source, KnifeRuntime runtime, Cs2Install install,
			KnifeModel knife) {
		Minecraft client = source.getClient();
		if (runtime.extractorStatus() == ExtractorStatus.NEEDS_DOWNLOAD) {
			source.sendFeedback(text("export.downloading"));
		}
		source.sendFeedback(text("export.started", knife.id()));

		runtime.export(install, knife).whenCompleteAsync((model, error) -> {
			if (error != null) {
				Throwable cause = error.getCause() != null ? error.getCause() : error;
				KnivesAsSwordsClient.LOGGER.error("Could not convert {}", knife.modelPath(), cause);
				source.sendError(text("export.failed", knife.id(), firstLine(cause.getMessage())));
			} else {
				source.sendFeedback(text("export.done", knife.id(), model.toString()));
			}
		}, client);
	}

	/**
	 * Asks before anything is downloaded. Opened on the next tick, because the chat screen
	 * closes itself right after running a command and would take this screen with it.
	 */
	private static void askForDownload(FabricClientCommandSource source, KnifeRuntime runtime, Runnable onAccepted) {
		Minecraft client = source.getClient();
		client.schedule(() -> client.gui.setScreen(new ConfirmScreen(accepted -> {
			client.gui.setScreen(null);
			if (accepted) {
				runtime.allowExtractorDownload();
				onAccepted.run();
			} else {
				source.sendFeedback(text("consent.declined"));
			}
		}, text("consent.title"), text("consent.message", ExtractorRelease.VERSION),
				text("consent.accept"), text("consent.decline"))));
	}

	private static String firstLine(String message) {
		if (message == null) {
			return "unknown error (see the log)";
		}
		int newline = message.indexOf('\n');
		return newline < 0 ? message : message.substring(0, newline);
	}

	private static Component text(String key, Object... arguments) {
		return Component.translatable(PREFIX + key, arguments);
	}
}
