package io.github.icxd.knives_as_swords.visuals.player;

import io.github.icxd.knives_as_swords.fx.FxBatch;
import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.fx.Gradient;
import io.github.icxd.knives_as_swords.fx.WorldFrame;
import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Something to wear that is made of light: a halo over the head, a wide pointed hat, or wisps
 * circling the body.
 *
 * <p>Worn by the player's own figure only, and so only seen from outside (in third person).
 */
public final class Halo extends Visual {
	private static final int HAT_SIDES = 28;
	private static final int WISPS = 3;
	/** How many fading lights each wisp drags behind it. */
	private static final int WISP_TAIL = 7;

	private final Setting.Choice style = choice("style", "halo", "hat", "wisps");
	private final Setting.Colors colors = colors("colors", 0xFFE27A, 0xFFA63A);
	private final Setting.Slider cycle = slider("cycle", 0f, 0f, 1f, 0.05f);
	private final Setting.Slider size = slider("size", 1f, 0.5f, 2f, 0.1f);
	private final Setting.Slider strength = slider("strength", 0.8f, 0.1f, 1f, 0.05f);

	private final float[] corners = new float[12];
	private final int[] cornerColors = new int[4];
	private final float[] rim = new float[(HAT_SIDES + 1) * 3];

	public Halo() {
		super("halo", Category.PLAYER, false);
	}

	@Override
	public void world(WorldFrame frame) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		// Seen through the player's own eyes it would sit on the camera.
		if (player == null || client.options.getCameraType().isFirstPerson() || player.isInvisible()) {
			return;
		}
		Vec3 at = player.getPosition(client.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		float x = frame.x(at.x), feet = frame.y(at.y), z = frame.z(at.z);
		float top = feet + player.getBbHeight();
		float time = frame.time();
		Gradient gradient = colors.get();
		float cycling = cycle.get();
		float scale = size.get();
		float glow = strength.get();
		FxBatch batch = frame.batch();

		switch (style.get()) {
			case "hat" -> hat(batch, x, top, z, scale, glow, gradient, time, cycling);
			case "wisps" -> wisps(batch, x, feet, z, player.getBbHeight(), scale, glow, gradient, time, cycling);
			default -> {
				float radius = 0.32f * scale;
				float height = top + 0.28f + 0.03f * (float) Math.sin(time * 2f);
				int color = FxMath.fade(gradient.at(0f, time, cycling), glow);
				batch.flat(x, height, z, radius, 0f, 0f, 0f, 0f, radius, color, 0.6f, FxBatch.RING);
				// Seen edge-on a flat ring is nothing; a soft light keeps it there from every side.
				batch.dot(x, height, z, radius * 1.3f, FxMath.fade(gradient.at(1f, time, cycling), glow * 0.25f), 0.2f);
			}
		}
	}

	/** A wide, shallow cone, coloured round its rim. */
	private void hat(FxBatch batch, float x, float top, float z, float scale, float glow, Gradient gradient, float time,
			float cycling) {
		float radius = 0.62f * scale;
		float brim = top + 0.04f;
		float peak = top + 0.04f + 0.32f * scale;
		for (int i = 0; i <= HAT_SIDES; i++) {
			double angle = Math.PI * 2.0 * i / HAT_SIDES;
			rim[i * 3] = x + radius * (float) Math.cos(angle);
			rim[i * 3 + 1] = brim;
			rim[i * 3 + 2] = z + radius * (float) Math.sin(angle);
		}
		for (int i = 0; i < HAT_SIDES; i++) {
			// Round the rim once through the colours and back, so that the ends meet.
			float around = 1f - Math.abs(2f * i / HAT_SIDES - 1f);
			float next = 1f - Math.abs(2f * (i + 1) / HAT_SIDES - 1f);
			corners[0] = x;
			corners[1] = peak;
			corners[2] = z;
			System.arraycopy(rim, i * 3, corners, 3, 3);
			System.arraycopy(rim, (i + 1) * 3, corners, 6, 3);
			corners[9] = x;
			corners[10] = peak;
			corners[11] = z;
			int here = gradient.at(around, time, cycling);
			int there = gradient.at(next, time, cycling);
			cornerColors[0] = FxMath.fade(here, glow * 0.5f);
			cornerColors[1] = FxMath.fade(here, glow * 0.22f);
			cornerColors[2] = FxMath.fade(there, glow * 0.22f);
			cornerColors[3] = FxMath.fade(there, glow * 0.5f);
			batch.plain(corners, cornerColors);
		}
		batch.polyline(rim, HAT_SIDES + 1, 0.035f * scale, FxMath.fade(gradient.at(0f, time, cycling), glow), 0.5f, 0f,
				false);
	}

	/** Small lights going round the body, each trailing a few fainter ones. */
	private void wisps(FxBatch batch, float x, float feet, float z, float tall, float scale, float glow, Gradient gradient,
			float time, float cycling) {
		float radius = 0.6f * scale;
		for (int wisp = 0; wisp < WISPS; wisp++) {
			for (int k = 0; k < WISP_TAIL; k++) {
				// Further down the tail is where the wisp was a moment ago.
				float then = time - k * 0.035f;
				double angle = then * 2.4 + wisp * Math.PI * 2.0 / WISPS;
				float height = feet + tall * (0.5f + 0.35f * (float) Math.sin(then * 1.3f + wisp * 2.1f));
				float fade = 1f - k / (float) WISP_TAIL;
				batch.dot(x + radius * (float) Math.cos(angle), height, z + radius * (float) Math.sin(angle),
						0.09f * scale * (0.5f + 0.5f * fade),
						FxMath.fade(gradient.at(k / (float) WISP_TAIL, time, cycling), glow * fade * fade), 0.7f);
			}
		}
	}
}
