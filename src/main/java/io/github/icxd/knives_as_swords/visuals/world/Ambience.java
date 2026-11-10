package io.github.icxd.knives_as_swords.visuals.world;

import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FogType;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * Tints the sky and the fog in colours of the player's choice.
 *
 * <p>A tint changes the colour and nothing else: the sky and fog stay exactly as bright or dark
 * as the game made them, and the fog as thick. Night stays night. Fog that is there to hinder
 * (under water, in lava or snow, from blindness or darkness) is left alone entirely.
 */
public final class Ambience extends Visual {
	private final Setting.Color sky = color("sky", 0x8B5CFF);
	private final Setting.Slider skyAmount = slider("sky_amount", 0.6f, 0f, 1f, 0.05f);
	private final Setting.Color fog = color("fog", 0xFF7AD9);
	private final Setting.Slider fogAmount = slider("fog_amount", 0.5f, 0f, 1f, 0.05f);

	public Ambience() {
		super("ambience", Category.WORLD, false);
	}

	/** The game has worked out this frame's sky and fog: recolour them. */
	public void tint(LevelRenderState level) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || level.cameraRenderState.fogType != FogType.NONE
				|| client.player.hasEffect(MobEffects.BLINDNESS) || client.player.hasEffect(MobEffects.DARKNESS)) {
			return;
		}
		Vector4f fogColor = level.cameraRenderState.fogData.color;
		float[] tinted = tinted(fogColor.x, fogColor.y, fogColor.z, fog.rgb(), fogAmount.get());
		fogColor.set(tinted[0], tinted[1], tinted[2], fogColor.w);

		if (level.skyRenderState != null && level.skyRenderState.skyColor != null) {
			Vector3fc skyColor = level.skyRenderState.skyColor;
			float[] skyTinted = tinted(skyColor.x(), skyColor.y(), skyColor.z(), sky.rgb(), skyAmount.get());
			level.skyRenderState.skyColor = new Vector3f(skyTinted[0], skyTinted[1], skyTinted[2]);
		}
	}

	/**
	 * A colour moved towards a tint without getting any brighter or darker: the tint only brings its
	 * hue, at the brightness the colour already has.
	 *
	 * @param tint the tint as {@code 0xRRGGBB}
	 * @param amount 0 leaves the colour alone, 1 replaces its hue entirely
	 * @return red, green and blue from 0 to 1
	 */
	public static float[] tinted(float red, float green, float blue, int tint, float amount) {
		float tintRed = ((tint >>> 16) & 0xFF) / 255f;
		float tintGreen = ((tint >>> 8) & 0xFF) / 255f;
		float tintBlue = (tint & 0xFF) / 255f;
		float brightness = brightness(red, green, blue);
		float tintBrightness = brightness(tintRed, tintGreen, tintBlue);
		// A black tint has no hue to bring; then there is nothing to do.
		float scale = tintBrightness > 1e-4f ? brightness / tintBrightness : 0f;
		if (scale == 0f && brightness > 0f) {
			return new float[] {red, green, blue};
		}
		float mix = Math.clamp(amount, 0f, 1f);
		float[] result = {red + (tintRed * scale - red) * mix, green + (tintGreen * scale - green) * mix,
				blue + (tintBlue * scale - blue) * mix};
		// A strong hue at the same brightness can ask for more than one channel can give.
		float most = Math.max(result[0], Math.max(result[1], result[2]));
		if (most > 1f) {
			for (int i = 0; i < 3; i++) {
				result[i] /= most;
			}
		}
		return result;
	}

	private static float brightness(float red, float green, float blue) {
		return 0.299f * red + 0.587f * green + 0.114f * blue;
	}
}
