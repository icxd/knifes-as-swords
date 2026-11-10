package io.github.icxd.knives_as_swords.fx;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

/**
 * How effects get on screen: two ways of drawing light, both added on top of what is already
 * there, hidden by anything in front of them, and hiding nothing themselves.
 *
 * <p>"Energy" draws the quads of an {@link FxBatch}. "Ghost" draws a knife's own triangles as a
 * shell of light, brightest where the surface turns away from the viewer.
 *
 * <p>Minecraft has a second way of drawing see-through things in the world ("improved
 * transparency"), which takes three more pipelines per look; without them the game stops with an
 * error as soon as that option is on.
 */
public final class FxPipelines {
	/**
	 * Light is added to what is there. Alpha is added too, and in full rather than squared as
	 * Minecraft's own additive blending has it: a menu picture is pasted onto the screen only where
	 * its alpha is not zero, and squaring rounds faint light down to exactly that, which cuts a
	 * hard edge into every glow.
	 */
	private static final BlendFunction ADD_LIGHT = new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE,
			BlendFactor.ONE, BlendFactor.ONE);

	/** Shared by both: the vertex layout, and depth that is tested but never written. */
	private static final RenderPipeline.Snippet LIGHT_SNIPPET = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL)
			.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
			.buildSnippet();

	private static final RenderPipeline.Snippet ENERGY_SNIPPET = RenderPipeline.builder(LIGHT_SNIPPET)
			.withVertexShader(id("core/fx_energy"))
			.withFragmentShader(id("core/fx_energy"))
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.withCull(false)
			.buildSnippet();
	private static final RenderPipeline ENERGY = RenderPipelines.register(RenderPipeline.builder(ENERGY_SNIPPET)
			.withLocation(id("pipeline/fx_energy"))
			.withColorTargetState(new ColorTargetState(ADD_LIGHT))
			.build());
	private static final OitPipelineSet OIT_ENERGY = RenderPipelines.register(OitPipelineSet
			.builder(KnivesAsSwordsClient.MOD_ID + "_fx_energy",
					RenderPipeline.builder(ENERGY_SNIPPET).withShaderDefine("OIT_ADDITIVE"))
			.build());

	private static final RenderPipeline.Snippet GHOST_SNIPPET = RenderPipeline.builder(LIGHT_SNIPPET)
			.withVertexShader(id("core/fx_ghost"))
			.withFragmentShader(id("core/fx_ghost"))
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withCull(true)
			.buildSnippet();
	private static final RenderPipeline GHOST = RenderPipelines.register(RenderPipeline.builder(GHOST_SNIPPET)
			.withLocation(id("pipeline/fx_ghost"))
			.withColorTargetState(new ColorTargetState(ADD_LIGHT))
			.build());
	private static final OitPipelineSet OIT_GHOST = RenderPipelines.register(OitPipelineSet
			.builder(KnivesAsSwordsClient.MOD_ID + "_fx_ghost",
					RenderPipeline.builder(GHOST_SNIPPET).withShaderDefine("OIT_ADDITIVE"))
			.build());

	private static final RenderType ENERGY_TYPE = RenderType.create(KnivesAsSwordsClient.MOD_ID + "_fx_energy",
			RenderSetup.builder(ENERGY).setOitPipelines(OIT_ENERGY).createRenderSetup());
	private static final RenderType GHOST_TYPE = RenderType.create(KnivesAsSwordsClient.MOD_ID + "_fx_ghost",
			RenderSetup.builder(GHOST).setOitPipelines(OIT_GHOST).createRenderSetup());

	private FxPipelines() {
	}

	/**
	 * Registers the pipelines. Has to happen while the mod initializes, before the game compiles
	 * its shaders for the first time.
	 */
	public static void init() {
		// Loading this class did the work.
	}

	/** For the quads of an {@link FxBatch}; see {@link FxDraw#batch}. */
	public static RenderType energy() {
		return ENERGY_TYPE;
	}

	/** For a knife's triangles drawn as light; see {@link FxDraw#ghost}. */
	public static RenderType ghost() {
		return GHOST_TYPE;
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(KnivesAsSwordsClient.MOD_ID, path);
	}
}
