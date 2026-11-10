package io.github.icxd.knives_as_swords.render;

import io.github.icxd.knives_as_swords.model.KnifeAnimations;
import io.github.icxd.knives_as_swords.model.KnifeMesh;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * A knife that is ready to draw: its triangles, its uploaded texture, and where it sits inside
 * the unit cube that Minecraft's item transforms expect.
 */
public final class KnifeVisual {
	/**
	 * Model units per metre for knives held in a hand. One factor for all of them, so their
	 * relative sizes stay as they are in the game they come from.
	 */
	private static final float HELD_SCALE = 2.5f;
	/** Where a vanilla sword's grip is in its 16x16 sprite, as a fraction of the cube. */
	private static final float GRIP_X = 3.5f / 16f;
	private static final float GRIP_Y = 3.5f / 16f;
	/**
	 * In first person (when the knife's own animations are off) the knife is held further along
	 * the sword's diagonal and shown larger: a sword reaches well into view from where the hand
	 * is, and a knife held at the same spot would mostly stay below the screen's edge.
	 */
	private static final float FIRST_PERSON_SCALE = 3.4f;
	private static final float FIRST_PERSON_GRIP = 7f / 16f;
	/** How much of the cube's width a knife fills as an icon. */
	private static final float ICON_FILL = 0.92f;

	private final Appearance appearance;
	private final KnifeMesh mesh;
	private final @Nullable KnifeAnimations animations;
	private final Identifier texture;
	private final Identifier surfaceTexture;
	private final RenderType renderType;
	private final RenderType glintRenderType;
	private final RenderType shinyRenderType;
	private final RenderType shinyGlintRenderType;
	private final Matrix4fc heldTransform;
	private final Matrix4fc firstPersonTransform;
	private final Matrix4fc iconTransform;
	private final Vector3fc[] extents;

	KnifeVisual(Appearance appearance, KnifeMesh mesh, @Nullable KnifeAnimations animations, Identifier texture,
			Identifier surfaceTexture) {
		this.appearance = appearance;
		this.mesh = mesh;
		this.animations = animations;
		this.texture = texture;
		this.surfaceTexture = surfaceTexture;
		this.renderType = RenderTypes.entitySolid(texture);
		this.glintRenderType = RenderTypes.entitySolidGlint(texture);
		this.shinyRenderType = KnifePipelines.renderType(texture, surfaceTexture, false);
		this.shinyGlintRenderType = KnifePipelines.renderType(texture, surfaceTexture, true);

		Vector3f min = mesh.min();
		Vector3f max = mesh.max();
		// The handle is the part below the origin; hold it halfway down.
		Vector3f grip = new Vector3f((min.x + max.x) / 2f, min.y * 0.5f, 0f);
		this.heldTransform = new Matrix4f()
				.translate(GRIP_X, GRIP_Y, 0.5f)
				.mul(spriteOrientation(true))
				.scale(HELD_SCALE)
				.translate(-grip.x, -grip.y, -grip.z);

		this.firstPersonTransform = new Matrix4f()
				.translate(FIRST_PERSON_GRIP, FIRST_PERSON_GRIP, 0.5f)
				.mul(spriteOrientation(true))
				.scale(FIRST_PERSON_SCALE)
				.translate(-grip.x, -grip.y, -grip.z);

		// As an icon the knife is centred and fills the slot along the diagonal, like a sword sprite.
		Vector3f center = new Vector3f(min).add(max).mul(0.5f);
		float diagonalSpan = ((max.y - min.y) + (max.z - min.z)) / (float) Math.sqrt(2.0);
		this.iconTransform = new Matrix4f()
				.translate(0.5f, 0.5f, 0.5f)
				.mul(spriteOrientation(false))
				.scale(ICON_FILL / Math.max(diagonalSpan, 1e-4f))
				.translate(-center.x, -center.y, -center.z);

		this.extents = new Vector3fc[] {
				new Vector3f(min.x, min.y, min.z), new Vector3f(max.x, min.y, min.z),
				new Vector3f(min.x, max.y, min.z), new Vector3f(max.x, max.y, min.z),
				new Vector3f(min.x, min.y, max.z), new Vector3f(max.x, min.y, max.z),
				new Vector3f(min.x, max.y, max.z), new Vector3f(max.x, max.y, max.z)};
	}

	/**
	 * Turns a knife (blade along +Y, flat in the YZ plane, cutting edge towards +Z) the way a
	 * sword sprite lies in its cube: flat in the XY plane, pointing from the lower left to the
	 * upper right.
	 *
	 * @param edgeToUpperLeft which side of that diagonal the cutting edge ends up on. In a hand,
	 *     vanilla's sword transforms turn the sprite's upper left half towards the ground and
	 *     away from the holder, which is where an edge belongs.
	 */
	private static Matrix4f spriteOrientation(boolean edgeToUpperLeft) {
		return new Matrix4f()
				.rotateZ((float) Math.toRadians(-45.0))
				.rotateY((float) Math.toRadians(edgeToUpperLeft ? -90.0 : 90.0));
	}

	public Appearance appearance() {
		return appearance;
	}

	public KnifeMesh mesh() {
		return mesh;
	}

	/** The knife's first-person animations; {@code null} when it has none or they could not be read. */
	public @Nullable KnifeAnimations animations() {
		return animations;
	}

	public Identifier texture() {
		return texture;
	}

	/** The texture saying how rough and how metallic the knife is; only the shiny way of drawing uses it. */
	public Identifier surfaceTexture() {
		return surfaceTexture;
	}

	/**
	 * @param glint whether to add the shimmer of enchanted items
	 * @param shiny whether to draw with reflections, or plainly like any other item
	 */
	public RenderType renderType(boolean glint, boolean shiny) {
		if (shiny) {
			return glint ? shinyGlintRenderType : shinyRenderType;
		}
		return glint ? glintRenderType : renderType;
	}

	/** Places the knife in the item cube for the given display context. */
	public Matrix4fc cubeTransform(ItemDisplayContext context) {
		return switch (context) {
			case THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> heldTransform;
			case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND -> firstPersonTransform;
			default -> iconTransform;
		};
	}

	/** The corners of the knife's bounding box, in the knife's own space. */
	public Vector3fc[] extents() {
		return extents;
	}
}
