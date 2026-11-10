package io.github.icxd.knives_as_swords.visuals.player;

import io.github.icxd.knives_as_swords.fx.BladeTrail;
import io.github.icxd.knives_as_swords.fx.Gradient;
import io.github.icxd.knives_as_swords.fx.WorldFrame;
import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Light the player leaves behind as they move, fading after a moment: a path on the ground, or
 * a band standing upright. Standing still leaves nothing.
 *
 * <p>Decoration on the player's own way, seen by nobody else.
 */
public final class PlayerTrail extends Visual {
	/** The world is too big for the trail's small numbers; it is kept relative to a spot that moves along. */
	private static final double RANGE = 256.0;
	/** Speed, in blocks per second, from which the trail shows in full: a walk. */
	private static final float FULL_SPEED = 3.5f;

	private final Setting.Choice style = choice("style", "path", "band");
	private final Setting.Colors colors = colors("colors", 0x4DF2FF, 0x8C66FF);
	private final Setting.Slider cycle = slider("cycle", 0f, 0f, 1f, 0.05f);
	/** Seconds until a stretch of the trail has faded. */
	private final Setting.Slider length = slider("length", 0.8f, 0.2f, 3f, 0.1f);
	/** How wide the path is, or how tall the band from the feet up, in blocks. */
	private final Setting.Slider size = slider("size", 0.8f, 0.1f, 1.8f, 0.1f);
	private final Setting.Slider strength = slider("strength", 0.8f, 0.1f, 1f, 0.05f);
	/** Whether the trail also shows through the player's own eyes, where it is mostly underfoot. */
	private final Setting.Toggle firstPerson = toggle("first_person", false);

	private final BladeTrail trail = new BladeTrail();
	/** The path is two halves, each running from the middle to one edge, so that both edges shine. */
	private final BladeTrail otherHalf = new BladeTrail();
	private boolean wasPath;
	/** Sideways to the way the player last went, for the path to be laid across. */
	private float sideX = 1f;
	private float sideZ;
	private final Vector3f low = new Vector3f();
	private final Vector3f high = new Vector3f();
	private boolean started;
	private double originX;
	private double originY;
	private double originZ;
	private float lastTime;

	public PlayerTrail() {
		super("player_trail", Category.PLAYER, false);
	}

	@Override
	protected void switched() {
		trail.clear();
		otherHalf.clear();
		started = false;
	}

	@Override
	public void world(WorldFrame frame) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		Vec3 at = player.getPosition(client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		float time = frame.time();
		if (!started || time < lastTime || Math.abs(at.x - originX) + Math.abs(at.y - originY)
				+ Math.abs(at.z - originZ) > RANGE) {
			// A fresh start: the first time, after the clock began anew, or far from where the trail is kept.
			trail.clear();
			otherHalf.clear();
			started = true;
			originX = at.x;
			originY = at.y;
			originZ = at.z;
		}
		lastTime = time;
		boolean path = style.is("path");
		if (path != wasPath) {
			trail.clear();
			otherHalf.clear();
			wasPath = path;
		}
		float span = size.get();
		low.set((float) (at.x - originX), (float) (at.y - originY) + 0.05f, (float) (at.z - originZ));
		if (path) {
			// Laid across the way the player is going; standing still, across the way they last went.
			Vec3 moving = player.getDeltaMovement();
			double speed = Math.sqrt(moving.x * moving.x + moving.z * moving.z);
			if (speed > 0.02) {
				sideX = (float) (-moving.z / speed);
				sideZ = (float) (moving.x / speed);
			}
			high.set(low.x + sideX * span * 0.5f, low.y, low.z + sideZ * span * 0.5f);
			trail.add(time, low, high);
			high.set(low.x - sideX * span * 0.5f, low.y, low.z - sideZ * span * 0.5f);
			otherHalf.add(time, low, high);
		} else {
			high.set(low.x, low.y + span, low.z);
			trail.add(time, low, high);
		}

		if (client.options.getCameraType().isFirstPerson() && !firstPerson.on()) {
			return;
		}
		Gradient gradient = colors.get();
		float cycling = cycle.get();
		BladeTrail.Colors ramp = life -> gradient.at(life, time, cycling);
		trail.build(frame.batch(), time, length.get(), FULL_SPEED, Math.max(span, 0.5f), ramp, strength.get(), 0.3f, 0.45f,
				frame.x(originX), frame.y(originY), frame.z(originZ));
		if (path) {
			otherHalf.build(frame.batch(), time, length.get(), FULL_SPEED, Math.max(span, 0.5f), ramp, strength.get(), 0.3f,
					0.45f, frame.x(originX), frame.y(originY), frame.z(originZ));
		}
	}
}
