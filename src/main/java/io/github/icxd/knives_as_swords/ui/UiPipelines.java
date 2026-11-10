package io.github.icxd.knives_as_swords.ui;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * How the mod's menus are drawn: Minecraft's way of drawing flat things on screen, with a shader
 * that can make edges soft or round (see {@code ui_glow.fsh}).
 */
public final class UiPipelines {
	private static final RenderPipeline.Snippet SHAPES = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.withVertexShader(id("core/ui_glow"))
			.withFragmentShader(id("core/ui_glow"))
			.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.QUADS)
			.buildSnippet();

	/** Covers what is behind it, as far as it is not see-through: panels, buttons, knobs. */
	static final RenderPipeline SOLID = RenderPipelines.register(RenderPipeline.builder(SHAPES)
			.withLocation(id("pipeline/ui_solid"))
			.withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
			.build());

	/** Adds its colour to what is behind it: glows, sparks, light. */
	static final RenderPipeline LIGHT = RenderPipelines.register(RenderPipeline.builder(SHAPES)
			.withLocation(id("pipeline/ui_light"))
			.withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE,
					BlendFactor.ZERO, BlendFactor.ONE)))
			.build());

	private UiPipelines() {
	}

	/**
	 * Registers the pipelines. Has to happen while the mod initializes, before the game compiles
	 * its shaders for the first time.
	 */
	public static void init() {
		// Loading this class did the work.
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(KnivesAsSwordsClient.MOD_ID, path);
	}
}
