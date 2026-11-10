package io.github.icxd.knives_as_swords.visuals.player;

import io.github.icxd.knives_as_swords.fx.FxClock;
import io.github.icxd.knives_as_swords.fx.WorldFrame;
import io.github.icxd.knives_as_swords.fx.WorldPulses;
import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * A ring of light spreading over the ground where the player jumps off and where they land.
 *
 * <p>Decoration on the player's own jumps, seen by nobody else.
 */
public final class JumpRings extends Visual {
	/** Ticks in the air before coming down counts as a landing rather than a step. */
	private static final int LANDING_AIR_TICKS = 5;

	private final Setting.Colors colors = colors("colors", 0xFFFFFF, 0x66ADFF);
	private final Setting.Slider cycle = slider("cycle", 0f, 0f, 1f, 0.05f);
	private final Setting.Slider size = slider("size", 1f, 0.5f, 2.5f, 0.1f);
	private final Setting.Toggle jumping = toggle("jumping", true);
	private final Setting.Toggle landing = toggle("landing", true);

	private final WorldPulses pulses = new WorldPulses();
	private boolean wasOnGround = true;
	private int airTicks;

	public JumpRings() {
		super("jump_rings", Category.PLAYER, true);
	}

	@Override
	public void tick(Minecraft client) {
		LocalPlayer player = client.player;
		boolean onGround = player.onGround();
		float time = FxClock.seconds();
		if (wasOnGround && !onGround && player.getDeltaMovement().y > 0.1 && jumping.on()) {
			ring(player, time, 1f);
		} else if (!wasOnGround && onGround && airTicks >= LANDING_AIR_TICKS && landing.on()) {
			// The longer the fall, the wider the ring.
			ring(player, time, Math.min(1f + airTicks / 20f, 2.2f));
		}
		airTicks = onGround ? 0 : airTicks + 1;
		wasOnGround = onGround;
	}

	private void ring(LocalPlayer player, float time, float reach) {
		float scale = size.get() * reach;
		int color = colors.get().at(0f, time, cycle.get());
		// Just above the ground, so that it is not hidden by it.
		double y = player.getY() + 0.03;
		pulses.add(WorldPulses.Shape.GROUND_RING, player.getX(), y, player.getZ(), time, 0.5f, 0.15f * scale, 1.2f * scale,
				color, 0.5f);
		pulses.add(WorldPulses.Shape.GROUND_RING, player.getX(), y, player.getZ(), time, 0.8f, 0.1f * scale, 0.8f * scale,
				colors.get().at(1f, time, cycle.get()), 0.2f);
	}

	@Override
	public void world(WorldFrame frame) {
		pulses.draw(frame);
	}
}
