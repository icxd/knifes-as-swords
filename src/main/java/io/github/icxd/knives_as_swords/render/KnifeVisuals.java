package io.github.icxd.knives_as_swords.render;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.KnifeRuntime.ExtractorStatus;
import io.github.icxd.knives_as_swords.KnifeRuntime.State;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.ModConfig;
import io.github.icxd.knives_as_swords.cs2.Cs2Install;
import io.github.icxd.knives_as_swords.cs2.FinishCatalog;
import io.github.icxd.knives_as_swords.cs2.KnifeModel;
import io.github.icxd.knives_as_swords.finish.FinishPainter;
import io.github.icxd.knives_as_swords.model.KnifeAnimations;
import io.github.icxd.knives_as_swords.model.KnifeMesh;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * The knives that are loaded, loading, or failed to load, keyed by how they look.
 *
 * <p>Asking for a knife that is not there yet starts converting and loading it in the background
 * and answers {@code null} until it is ready, so swords simply keep their vanilla look meanwhile.
 * Everything here runs on the client thread unless noted.
 */
public final class KnifeVisuals {
	/** How often a knife that cannot be loaded right now (no consent yet, scan running) is retried. */
	private static final long RETRY_NANOS = 1_000_000_000L;
	/**
	 * Unused knives are dropped after this long, once more than {@link #KEEP_LOADED} are loaded.
	 * Short, because trying out patterns and wear in the settings makes a new texture each time.
	 */
	private static final long UNUSED_NANOS = 5_000_000_000L;
	private static final int KEEP_LOADED = 12;

	private static final class Slot {
		@Nullable KnifeVisual visual;
		boolean failed;
		boolean discarded;
		long lastUsed;
	}

	private final KnifeRuntime runtime;
	private final Map<Appearance, Slot> slots = new HashMap<>();
	/** The appearance configured for each sword item; {@link Optional#empty()} for "leave vanilla". */
	private final Map<Item, Optional<Appearance>> choices = new IdentityHashMap<>();
	/** Meshes are shared by every finish of a knife. Filled on the worker thread. */
	private final Map<String, KnifeMesh> meshes = new ConcurrentHashMap<>();
	/** Animations per knife; an empty entry means "tried, has none". Filled on the worker thread. */
	private final Map<String, Optional<KnifeAnimations>> animations = new ConcurrentHashMap<>();
	private int seenScanVersion = -1;
	private int seenConfigVersion = -1;
	private long retryAt;
	private long pruneAt;
	private int textureCounter;

	public KnifeVisuals(KnifeRuntime runtime) {
		this.runtime = runtime;
	}

	/** The knife to draw for this kind of sword, or {@code null} for the vanilla sword. */
	public @Nullable KnifeVisual forItem(Item item) {
		refresh();
		Optional<Appearance> choice = choices.get(item);
		if (choice == null) {
			choice = choose(item);
			if (choice == null) {
				// Not known yet (the game files are still being looked for); ask again next time.
				return null;
			}
			choices.put(item, choice);
		}
		return choice.map(this::get).orElse(null);
	}

	/** The knife for an explicit appearance (used by the settings preview), or {@code null} while it loads. */
	public @Nullable KnifeVisual get(Appearance appearance) {
		refresh();
		long now = System.nanoTime();
		Slot slot = slots.get(appearance);
		if (slot == null) {
			if (now - retryAt < 0) {
				return null;
			}
			slot = start(appearance);
			if (slot == null) {
				retryAt = now + RETRY_NANOS;
				return null;
			}
			slots.put(appearance, slot);
		}
		slot.lastUsed = now;
		if (now - pruneAt >= 0) {
			pruneAt = now + UNUSED_NANOS / 5;
			prune(now);
		}
		return slot.visual;
	}

	/** Whether this appearance could not be loaded (as opposed to still loading). */
	public boolean failed(Appearance appearance) {
		Slot slot = slots.get(appearance);
		return slot != null && slot.failed;
	}

	/** The appearance a sword would get with the current settings; empty when it stays vanilla or is unknown. */
	public Optional<Appearance> appearanceFor(Item item) {
		Optional<Appearance> choice = choose(item);
		return choice == null ? Optional.empty() : choice;
	}

	private void refresh() {
		int scanVersion = runtime.scanVersion();
		if (scanVersion != seenScanVersion) {
			// The game files may have changed; nothing made from the old ones can be trusted.
			seenScanVersion = scanVersion;
			discardAll();
		}
		int configVersion = runtime.configVersion();
		if (configVersion != seenConfigVersion) {
			seenConfigVersion = configVersion;
			choices.clear();
			retryAt = System.nanoTime();
			// Failures get another chance: the cause may have been a setting.
			slots.values().removeIf(slot -> slot.failed);
		}
	}

	/** {@code null} while unknown, empty for "keep vanilla". */
	private @Nullable Optional<Appearance> choose(Item item) {
		ModConfig config = runtime.config();
		if (!config.enabled) {
			return Optional.empty();
		}
		Optional<State> state = runtime.state();
		if (state.isEmpty()) {
			return null;
		}
		if (state.get().install().isEmpty() || state.get().knives().isEmpty()) {
			return Optional.empty();
		}

		Identifier id = BuiltInRegistries.ITEM.getKey(item);
		ModConfig.SwordSetting setting = config.sword(id.toString());
		if (!setting.enabled) {
			return Optional.empty();
		}
		String knife = setting.knife;
		if (knife.isEmpty() || state.get().knife(knife).isEmpty()) {
			knife = SwordDefaults.knifeFor(id, state.get());
		}
		return Optional.of(Appearance.of(knife, setting.finish, setting.pattern, setting.wear));
	}

	/** Starts loading; {@code null} when that is not possible right now and should be retried later. */
	private @Nullable Slot start(Appearance appearance) {
		Optional<State> state = runtime.state();
		if (state.isEmpty()) {
			return null;
		}
		Slot slot = new Slot();
		Optional<KnifeModel> knife = state.get().knife(appearance.knife());
		if (state.get().install().isEmpty() || knife.isEmpty()) {
			slot.failed = true;
			return slot;
		}
		ExtractorStatus extractor = runtime.extractorStatus();
		if (extractor == ExtractorStatus.NEEDS_CONSENT || extractor == ExtractorStatus.UNSUPPORTED_PLATFORM) {
			// Nothing is downloaded without the player having agreed; until then swords stay vanilla.
			return null;
		}

		int textureSize = runtime.config().textureSize;
		Cs2Install install = state.get().install().get();
		// Also converted when they are switched off: the switch then works at once in both directions.
		boolean wantAnimations = knife.get().hasAnimations() && !animations.containsKey(appearance.knife());
		runtime.export(install, knife.get())
				.thenCompose(glb -> {
					if (!wantAnimations) {
						return CompletableFuture.completedFuture(new Exported(glb, null));
					}
					// A knife without working animations is still a knife; it just stays still.
					return runtime.exportAnimations(install, knife.get()).handle((source, error) -> {
						if (error != null) {
							KnivesAsSwordsClient.LOGGER.warn("Could not convert the animations of {}",
									appearance.knife(), error.getCause() != null ? error.getCause() : error);
						}
						return new Exported(glb, source);
					});
				})
				.thenCompose(exported -> runtime.onWorker(() -> prepare(appearance, install, knife.get(),
						state.get().finishes().finish(appearance.knife(), appearance.finish()).orElse(null),
						exported, textureSize)))
				.whenCompleteAsync((prepared, error) -> finish(appearance, slot, prepared, error),
						Minecraft.getInstance());
		return slot;
	}

	private record Exported(Path glb, KnifeRuntime.@Nullable AnimationSource animations) {
	}

	/** What the worker thread hands over: everything except the upload to the GPU. */
	private record Prepared(KnifeMesh mesh, @Nullable KnifeAnimations animations, NativeImage image,
			NativeImage surface) {
		void close() {
			image.close();
			surface.close();
		}
	}

	/** Worker thread. */
	private Prepared prepare(Appearance appearance, Cs2Install install, KnifeModel knife,
			FinishCatalog.@Nullable Finish finish, Exported exported, int textureSize)
			throws IOException, InterruptedException {
		Path glb = exported.glb();
		KnifeMesh mesh = meshes.get(appearance.knife());
		if (mesh == null) {
			mesh = KnifeMesh.load(glb);
			meshes.put(appearance.knife(), mesh);
		}
		if (exported.animations() != null) {
			Optional<KnifeAnimations> loaded = Optional.empty();
			try {
				loaded = Optional.of(KnifeAnimations.load(exported.animations().clipFolder(), knife.clipSuffix(),
						exported.animations().boneNames(), mesh.jointCount()));
			} catch (IOException | RuntimeException e) {
				KnivesAsSwordsClient.LOGGER.warn("Could not read the animations of {}", appearance.knife(), e);
			}
			animations.put(appearance.knife(), loaded);
		}
		KnifeAnimations knifeAnimations = animations.getOrDefault(appearance.knife(), Optional.empty()).orElse(null);

		if (finish != null && mesh.material().isPresent()) {
			try {
				FinishPainter.Painted painted = runtime.paintFinish(install, mesh.material().get(), finish,
						appearance.pattern(), appearance.wearFraction(), textureSize);
				return new Prepared(mesh, knifeAnimations, KnifeImages.ofPixels(painted.color(), painted.size()),
						KnifeImages.ofPixels(painted.material(), painted.size()));
			} catch (IOException | RuntimeException e) {
				// A finish that cannot be made should not take the knife with it.
				KnivesAsSwordsClient.LOGGER.warn("Could not apply the finish {} to {}; showing it unpainted",
						finish.name(), appearance.knife(), e);
			}
		} else if (!appearance.finish().isEmpty()) {
			KnivesAsSwordsClient.LOGGER.warn("The game has no finish '{}' for {}; showing it unpainted",
					appearance.finish(), appearance.knife());
		}

		Path imageFile = mesh.baseColorImage()
				.orElseThrow(() -> new IOException("The converted model has no colour texture: " + glb));
		NativeImage image;
		try (InputStream in = Files.newInputStream(imageFile)) {
			image = NativeImage.read(in);
		}
		image = KnifeImages.limitSize(image, textureSize);
		try {
			return new Prepared(mesh, knifeAnimations, image, surface(mesh, textureSize));
		} catch (IOException | RuntimeException e) {
			image.close();
			throw e;
		}
	}

	/** How the unpainted knife reflects, from the texture the extractor wrote for that. Worker thread. */
	private static NativeImage surface(KnifeMesh mesh, int textureSize) throws IOException {
		if (mesh.surfaceImage().isEmpty() || !Files.isRegularFile(mesh.surfaceImage().get())) {
			return KnifeImages.plainSurface();
		}
		NativeImage surface;
		try (InputStream in = Files.newInputStream(mesh.surfaceImage().get())) {
			surface = NativeImage.read(in);
		}
		return KnifeImages.surfaceFromGltf(KnifeImages.limitSize(surface, textureSize));
	}

	private void finish(Appearance appearance, Slot slot, @Nullable Prepared prepared, @Nullable Throwable error) {
		if (error != null || prepared == null) {
			Throwable cause = error != null && error.getCause() != null ? error.getCause() : error;
			KnivesAsSwordsClient.LOGGER.error("Could not load knife {}", appearance, cause);
			slot.failed = true;
			return;
		}
		if (slot.discarded) {
			prepared.close();
			return;
		}
		try {
			int number = textureCounter++;
			Identifier id = Identifier.fromNamespaceAndPath(KnivesAsSwordsClient.MOD_ID,
					"knife/" + appearance.knife() + "/" + number);
			Identifier surfaceId = Identifier.fromNamespaceAndPath(KnivesAsSwordsClient.MOD_ID,
					"knife/" + appearance.knife() + "/" + number + "_surface");
			// Each upload closes its image, also when it fails.
			KnifeTexture color;
			try {
				color = new KnifeTexture(id, prepared.image());
			} catch (RuntimeException e) {
				prepared.surface().close();
				throw e;
			}
			Minecraft.getInstance().getTextureManager().register(id, color);
			Minecraft.getInstance().getTextureManager().register(surfaceId, new KnifeTexture(surfaceId, prepared.surface()));
			slot.visual = new KnifeVisual(appearance, prepared.mesh(), prepared.animations(), id, surfaceId);
			KnivesAsSwordsClient.LOGGER.info("Loaded knife {} ({} triangles, {} animations)", appearance,
					prepared.mesh().triangleCount(),
					prepared.animations() == null ? 0 : prepared.animations().clipNames().size());
		} catch (RuntimeException e) {
			KnivesAsSwordsClient.LOGGER.error("Could not upload the texture of knife {}", appearance, e);
			slot.failed = true;
		}
	}

	private void prune(long now) {
		long loaded = slots.values().stream().filter(slot -> slot.visual != null).count();
		Iterator<Slot> iterator = slots.values().iterator();
		while (loaded > KEEP_LOADED && iterator.hasNext()) {
			Slot slot = iterator.next();
			if (slot.visual != null && now - slot.lastUsed > UNUSED_NANOS) {
				release(slot);
				iterator.remove();
				loaded--;
			}
		}
	}

	private void discardAll() {
		slots.values().forEach(this::release);
		slots.clear();
		choices.clear();
		meshes.clear();
		animations.clear();
		retryAt = System.nanoTime();
	}

	private void release(Slot slot) {
		slot.discarded = true;
		if (slot.visual != null) {
			Minecraft.getInstance().getTextureManager().release(slot.visual.texture());
			Minecraft.getInstance().getTextureManager().release(slot.visual.surfaceTexture());
			slot.visual = null;
		}
	}
}
