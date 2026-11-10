package io.github.icxd.knives_as_swords.visuals.world;

import io.github.icxd.knives_as_swords.fx.FxBatch;
import io.github.icxd.knives_as_swords.fx.FxMath;
import io.github.icxd.knives_as_swords.fx.Gradient;
import io.github.icxd.knives_as_swords.fx.WorldFrame;
import io.github.icxd.knives_as_swords.visuals.Setting;
import io.github.icxd.knives_as_swords.visuals.Visual;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The outline around the block the player is looking at, drawn as glowing lines in colours of
 * their choice, with the faces lit up faintly if wanted.
 *
 * <p>It restyles the outline the game draws anyway, around the same block and hidden by whatever
 * is in front of it in the same way.
 */
public final class BlockOutline extends Visual {
	/** The faces stand off the block by this much, in blocks, so that they do not flicker against it. */
	private static final float LIFT = 0.003f;

	private final Setting.Colors colors = colors("colors", 0x8B5CFF, 0x3FC8FF);
	private final Setting.Slider cycle = slider("cycle", 0.3f, 0f, 1f, 0.05f);
	private final Setting.Slider width = slider("width", 1f, 0.5f, 3f, 0.1f);
	private final Setting.Toggle fill = toggle("fill", true);
	private final Setting.Slider fillStrength = slider("fill_strength", 0.15f, 0.05f, 0.5f, 0.05f);
	/** Whether the game's own dark outline stays as well; glowing lines alone are faint on bright blocks. */
	private final Setting.Toggle keepVanilla = toggle("keep_vanilla", false);

	private @Nullable BlockPos pos;
	private @Nullable VoxelShape shape;
	private final float[] line = new float[6];
	private final float[] corners = new float[12];
	private final int[] cornerColors = new int[4];
	private int edge;

	public BlockOutline() {
		super("block_outline", Category.WORLD, false);
	}

	/**
	 * The game is about to draw its outline: remember around what.
	 *
	 * @return whether the game should still draw its own
	 */
	public boolean outline(BlockOutlineRenderState state) {
		pos = state.pos();
		shape = state.shape();
		return keepVanilla.on();
	}

	@Override
	public void world(WorldFrame frame) {
		BlockPos at = pos;
		VoxelShape around = shape;
		// Good for one frame only: without a block in the crosshair next time, there is nothing to draw.
		pos = null;
		shape = null;
		if (at == null || around == null || around.isEmpty()) {
			return;
		}
		FxBatch batch = frame.batch();
		float x = frame.x(at.getX()), y = frame.y(at.getY()), z = frame.z(at.getZ());
		Gradient gradient = colors.get();
		float time = frame.time();
		float cycling = cycle.get();
		float halfWidth = 0.02f * width.get();

		if (fill.on()) {
			int light = FxMath.fade(gradient.at(0.5f, time, cycling), fillStrength.get());
			around.forAllBoxes((x1, y1, z1, x2, y2, z2) -> box(batch, x + (float) x1 - LIFT, y + (float) y1 - LIFT,
					z + (float) z1 - LIFT, x + (float) x2 + LIFT, y + (float) y2 + LIFT, z + (float) z2 + LIFT, light));
		}
		edge = 0;
		around.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
			line[0] = x + (float) x1;
			line[1] = y + (float) y1;
			line[2] = z + (float) z1;
			line[3] = x + (float) x2;
			line[4] = y + (float) y2;
			line[5] = z + (float) z2;
			// Each edge a little further along the colours than the last.
			batch.polyline(line, 2, halfWidth, gradient.at((edge++ % 12) / 11f, time, cycling), 0.5f, 0f, false);
		});
	}

	/** The six faces of a box, as plain light. */
	private void box(FxBatch batch, float x1, float y1, float z1, float x2, float y2, float z2, int argb) {
		java.util.Arrays.fill(cornerColors, argb);
		face(batch, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
		face(batch, x1, y2, z1, x2, y2, z1, x2, y2, z2, x1, y2, z2);
		face(batch, x1, y1, z1, x2, y1, z1, x2, y2, z1, x1, y2, z1);
		face(batch, x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
		face(batch, x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
		face(batch, x2, y1, z1, x2, y1, z2, x2, y2, z2, x2, y2, z1);
	}

	private void face(FxBatch batch, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
			float cz, float dx, float dy, float dz) {
		corners[0] = ax;
		corners[1] = ay;
		corners[2] = az;
		corners[3] = bx;
		corners[4] = by;
		corners[5] = bz;
		corners[6] = cx;
		corners[7] = cy;
		corners[8] = cz;
		corners[9] = dx;
		corners[10] = dy;
		corners[11] = dz;
		batch.plain(corners, cornerColors);
	}
}
