#version 330
#extension GL_ARB_separate_shader_objects : require

// A knife's own shape drawn as light: the glow around it, and its afterimages.
//
// The surface is brightest where it turns away from the viewer, which draws the knife's outline
// in light and leaves its middle clear. Bands of brightness run along it. The result is added to
// whatever is behind it.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
layout(location = 4) in vec3 surfaceNormal;
layout(location = 5) in vec3 directionToEye;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

void main() {
    vec3 normal = normalize(surfaceNormal);
    vec3 toEye = normalize(directionToEye);
    float rim = pow(1.0 - clamp(abs(dot(normal, toEye)), 0.0, 1.0), 1.8);

    // Bands of light travel along the knife: narrow bright crests with dim stretches between.
    float band = 0.5 + 0.5 * sin(6.2831853 * texCoord0.x);
    band = band * band * band;
    float glow = rim * mix(0.5, 1.0, band) + texCoord0.y * mix(0.25, 2.2, band);

    vec3 light = vertexColor.rgb * glow + vec3(1.0) * pow(rim, 4.0) * 0.6;
    light *= vertexColor.a * ColorModulator.rgb;
    light *= 1.0 - total_fog_value(sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd);

    // The blending multiplies colour by alpha; split the light so that the product is the light again.
    float amount = clamp(max(light.r, max(light.g, light.b)), 0.0, 1.0);
    vec4 color = vec4(amount > 0.0 ? light / amount : vec3(0.0), amount);

#ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, color.a);
#else
#ifdef OIT_ACCUMULATE
    color = sampleColorForAccumulation(color);
#endif
    fragColor = color;
#endif
}
