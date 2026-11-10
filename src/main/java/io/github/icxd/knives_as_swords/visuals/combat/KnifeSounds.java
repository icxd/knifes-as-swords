package io.github.icxd.knives_as_swords.visuals.combat;

import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import io.github.icxd.knives_as_swords.sound.KnifeAudio;
import io.github.icxd.knives_as_swords.sound.KnifeSound;
import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import net.minecraft.client.Minecraft;

/**
 * The knife's own sounds, taken from the player's game files like its looks: drawing it, slashing,
 * hitting, turning it over to look at it.
 *
 * <p>Not a visual in the strict sense, but switched on and off like one. Only the player hears
 * them, they go along with the knife's own first-person animations, and they are played on top of
 * Minecraft's sounds, not instead of them.
 */
public final class KnifeSounds extends Visual {
	/** A hit is heard this many ticks after its swing starts: about when the blade lands. */
	private static final int HIT_DELAY_TICKS = 2;

	private final Setting.Slider volume = slider("volume", 0.8f, 0.05f, 1f, 0.05f);
	private final Setting.Toggle draw = toggle("draw", true);
	private final Setting.Toggle swings = toggle("swings", true);
	private final Setting.Toggle hits = toggle("hits", true);
	private final Setting.Toggle inspect = toggle("inspect", true);

	public KnifeSounds() {
		super("knife_sounds", Category.COMBAT, true);
	}

	@Override
	public void tick(Minecraft client) {
		// Converts and decodes them the first time; after that this is a quick look.
		KnifeAudio.prepare(KnivesAsSwordsClient.runtime());
	}

	/** The knife is being taken out. */
	public void drawn() {
		if (on() && draw.on()) {
			KnifeAudio.play(KnifeSound.DRAW, volume.get(), 0);
		}
	}

	/**
	 * A swing has started.
	 *
	 * @param lands whether it hits something
	 * @param heavy whether it is the heavy stab rather than a quick slash
	 */
	public void swung(boolean lands, boolean heavy) {
		if (!on()) {
			return;
		}
		if (swings.on()) {
			KnifeAudio.play(heavy ? KnifeSound.SWING_HEAVY : KnifeSound.SWING, volume.get(), 0);
		}
		if (lands && hits.on()) {
			KnifeAudio.play(heavy ? KnifeSound.HIT_HEAVY : KnifeSound.HIT, volume.get(), HIT_DELAY_TICKS);
		}
	}

	/** The player started looking the knife over. */
	public void inspected() {
		if (on() && inspect.on()) {
			KnifeAudio.play(KnifeSound.INSPECT, volume.get(), 0);
		}
	}
}
