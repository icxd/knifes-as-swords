package io.github.icxd.knives_as_swords.visuals.combat;

import io.github.icxd.knives_as_swords.KnifeRuntime;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.screen.ClickMenuScreen;
import io.github.icxd.knives_as_swords.screen.KnivesScreen;
import io.github.icxd.knives_as_swords.visuals.Visual;
import java.util.Map;
import net.minecraft.client.Minecraft;

/**
 * The knives themselves, as an entry in the menu: on and off is the mod's master switch for
 * drawing swords as knives, and its one "setting" opens the knife screen, where each sword's
 * knife, finish and effects are picked.
 *
 * <p>Nothing of this is kept with the other visuals; the knives have their own place in the
 * config file.
 */
public final class Knives extends Visual {
	public Knives() {
		super("knives", Category.COMBAT, true);
		action("open", () -> {
			Minecraft client = Minecraft.getInstance();
			client.gui.setScreen(new KnivesScreen(client.gui.screen() instanceof ClickMenuScreen menu ? menu : null));
		});
	}

	@Override
	public boolean on() {
		KnifeRuntime runtime = KnivesAsSwordsClient.runtime();
		return runtime != null && runtime.config().enabled;
	}

	@Override
	public void set(boolean on) {
		KnifeRuntime runtime = KnivesAsSwordsClient.runtime();
		if (runtime != null && runtime.config().enabled != on) {
			runtime.updateConfig(config -> config.enabled = on);
		}
	}

	@Override
	public Map<String, String> write() {
		return Map.of();
	}

	@Override
	public void read(Map<String, String> values) {
	}
}
