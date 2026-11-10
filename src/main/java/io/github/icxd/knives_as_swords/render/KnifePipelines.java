package io.github.icxd.knives_as_swords.render;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import io.github.icxd.knives_as_swords.KnivesAsSwordsClient;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;

/**
 * The mod's own way of drawing a knife: Minecraft's solid entity pipeline with a shader that
 * lights per pixel and knows how rough and how metallic the surface is, so that metal shines.
 *
 * <p>Everything else is as for entities (vertex layout, lightmap, overlay, fog, depth), which
 * keeps knives consistent with the world around them.
 */
public final class KnifePipelines {
	/** The second texture a knife is drawn with; see {@code knife.fsh}. */
	private static final String MATERIAL_SAMPLER = "KnifeMaterial";
	private static final BindGroupLayout MATERIAL_LAYOUT = BindGroupLayout.builder()
			.withUniform(MATERIAL_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
			.build();
	private static final Identifier SHADER = id("core/knife");

	private static final RenderPipeline KNIFE = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
					.withLocation(id("pipeline/knife"))
					.withVertexShader(SHADER)
					.withFragmentShader(SHADER)
					.withBindGroupLayout(BindGroupLayouts.SAMPLER1)
					.withBindGroupLayout(MATERIAL_LAYOUT)
					.withColorTargetState(ColorTargetState.DEFAULT)
					.build());
	/** The same with the shimmer of enchanted items on top. */
	private static final RenderPipeline KNIFE_GLINT = RenderPipelines.register(
			RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET, RenderPipelines.GLINT_SNIPPET)
					.withLocation(id("pipeline/knife_glint"))
					.withVertexShader(SHADER)
					.withFragmentShader(SHADER)
					.withBindGroupLayout(BindGroupLayouts.SAMPLER1)
					.withBindGroupLayout(MATERIAL_LAYOUT)
					.withColorTargetState(ColorTargetState.DEFAULT)
					.build());

	private KnifePipelines() {
	}

	/**
	 * Registers the pipelines. Has to happen while the mod initializes, before the game compiles
	 * its shaders for the first time.
	 */
	public static void init() {
		// Loading this class did the work.
	}

	/**
	 * @param color the knife's colour texture
	 * @param material its surface texture: roughness, metalness and ambient occlusion
	 */
	static RenderType renderType(Identifier color, Identifier material, boolean glint) {
		RenderSetup.RenderSetupBuilder setup = RenderSetup.builder(glint ? KNIFE_GLINT : KNIFE)
				.withTexture("Sampler0", color)
				.withTexture(MATERIAL_SAMPLER, material);
		if (glint) {
			setup.withTexture("GlintSampler", ItemFeatureRenderer.ENCHANTED_GLINT_ITEM)
					.setTextureTransform(TextureTransform.ENTITY_GLINT_TEXTURING);
		}
		return RenderType.create(glint ? "knives_as_swords_knife_glint" : "knives_as_swords_knife",
				setup.useLightmap().useOverlay().setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
						.createRenderSetup());
	}

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(KnivesAsSwordsClient.MOD_ID, path);
	}
}
