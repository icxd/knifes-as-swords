package io.github.icxd.knives_as_swords.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Quaternionfc;
import org.jspecify.annotations.Nullable;

/**
 * What it takes to put light into the world for one frame: a batch to add shapes to, and where
 * the camera is.
 *
 * <p>The batch is drawn in world axes with the camera at the origin; {@link #x}, {@link #y} and
 * {@link #z} take a place in the world there. Everything in it is hidden by whatever stands in
 * front of it, like anything else in the world.
 */
public final class WorldFrame {
	private final FxBatch batch;
	private double cameraX;
	private double cameraY;
	private double cameraZ;
	private float time;
	private float seconds;
	private @Nullable SubmitNodeCollector collector;
	private @Nullable PoseStack poseStack;
	/** Which way something has to be turned to face the camera. */
	private final Quaternionf facing = new Quaternionf();
	/** Full brightness: light and writing of the effects do not dim in the dark. */
	private static final int FULL_LIGHT = 15728880;

	public WorldFrame(FxBatch batch) {
		this.batch = batch;
	}

	/** Called by whoever draws the world, before anybody else gets their turn. */
	void begin(double cameraX, double cameraY, double cameraZ, float time, float seconds, SubmitNodeCollector collector,
			PoseStack poseStack, Quaternionfc facing) {
		this.collector = collector;
		this.poseStack = poseStack;
		this.facing.set(facing);
		this.cameraX = cameraX;
		this.cameraY = cameraY;
		this.cameraZ = cameraZ;
		this.time = time;
		this.seconds = seconds;
	}

	public FxBatch batch() {
		return batch;
	}

	/**
	 * Writes a short text at a place in the world, facing the camera. Like everything else here it
	 * is hidden by whatever stands in front of it.
	 *
	 * @param size 1 is the size of a name tag
	 * @param argb the colour; text that is all but invisible is left out
	 */
	public void text(double worldX, double worldY, double worldZ, String text, float size, int argb) {
		if (collector == null || poseStack == null || (argb >>> 24) < 8) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		float scale = 0.025f * size;
		poseStack.pushPose();
		poseStack.translate(x(worldX), y(worldY), z(worldZ));
		poseStack.rotate(facing);
		// Text is laid out with y pointing down.
		poseStack.scale(scale, -scale, scale);
		collector.submitText(poseStack, -font.width(text) / 2f, -font.lineHeight / 2f,
				Component.literal(text).getVisualOrderText(), false, Font.DisplayMode.NORMAL, FULL_LIGHT, argb, 0,
				(argb & 0xFF000000));
		poseStack.popPose();
	}

	public float x(double worldX) {
		return (float) (worldX - cameraX);
	}

	public float y(double worldY) {
		return (float) (worldY - cameraY);
	}

	public float z(double worldZ) {
		return (float) (worldZ - cameraZ);
	}

	public double cameraX() {
		return cameraX;
	}

	public double cameraY() {
		return cameraY;
	}

	public double cameraZ() {
		return cameraZ;
	}

	/** Seconds on the effects' clock; see {@link FxClock}. */
	public float time() {
		return time;
	}

	/** Seconds since the frame before, never more than a tenth. */
	public float seconds() {
		return seconds;
	}
}
