#version 330
#extension GL_ARB_separate_shader_objects : require

// Light without a surface: trails, bolts, sparks.
//
// Every quad says where a pixel lies within its shape (texCoord0) and which shape that is. From
// that comes a soft glow in the quad's colour and, in the middle of it, a core that burns white.
// The result is added to whatever is behind it.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec2 texCoord0;
// x: the shape (-1 a band swept by a blade, -0.5 plain all over, 0 a line, 0.5 a ring, 1 a round light).
// y: how white-hot the core burns. z: how ragged the shape is. Both run from -1 (not at all) to 1.
layout(location = 4) in vec3 shape;

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// Smooth noise from 0 to 1.
float valueNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 t = fract(p);
    t = t * t * (3.0 - 2.0 * t);
    return mix(mix(hash(cell), hash(cell + vec2(1.0, 0.0)), t.x),
               mix(hash(cell + vec2(0.0, 1.0)), hash(cell + vec2(1.0, 1.0)), t.x), t.y);
}

void main() {
    float heat = clamp(shape.y * 0.5 + 0.5, 0.0, 1.0);
    float ragged = clamp(shape.z * 0.5 + 0.5, 0.0, 1.0);
    float strength = vertexColor.a;

    float glow;
    float core;
    if (shape.x > 0.75) {
        // A round light.
        float away = length(texCoord0 * 2.0 - 1.0);
        glow = pow(max(1.0 - away, 0.0), 2.0);
        core = exp(-away * away * 18.0);
    } else if (shape.x > 0.25) {
        // A ring: a thin circle with a faint light inside it.
        float away = length(texCoord0 * 2.0 - 1.0);
        float off = (away - 0.82) / 0.06;
        glow = exp(-off * off) + 0.12 * pow(max(1.0 - away / 0.82, 0.0), 0.5);
        core = exp(-off * off * 5.0);
    } else if (shape.x > -0.25) {
        // A line: a thin core in a wider glow that flickers along its length.
        float away = abs(texCoord0.x * 2.0 - 1.0);
        float flicker = mix(1.0, 0.4 + 1.2 * valueNoise(vec2(texCoord0.y * 11.0, 0.5)), ragged);
        glow = pow(max(1.0 - away, 0.0), 2.5) * flicker;
        core = exp(-away * away * 70.0);
    } else if (shape.x > -0.75) {
        // Plain: the quad's colour as it is, for filling in faces.
        glow = 1.0;
        core = 0.0;
    } else {
        // A band swept by a blade: across runs from the blade's base (0) to its tip (1), and
        // texCoord0.y counts blade lengths travelled, so the streaks stay where they were left.
        float across = texCoord0.x;
        float streaks = 0.6 * valueNoise(vec2(across * 13.0, texCoord0.y * 2.5)) + 0.4 * valueNoise(vec2(across * 34.0, texCoord0.y * 6.0));
        float body = pow(across, 1.6) * mix(1.0, 0.25 + 1.5 * streaks, ragged);
        // The tip draws a bright line just inside the band's outer edge.
        float tipLine = smoothstep(0.78, 0.95, across) * (1.0 - smoothstep(0.95, 1.0, across));
        float inner = smoothstep(0.0, 0.15, across);
        // As a stretch of the band fades, it does not dim evenly but breaks up into shreds.
        float shred = valueNoise(vec2(across * 8.0, texCoord0.y * 4.0)) * (0.35 + 0.55 * ragged);
        float left = smoothstep(shred * 0.7, shred * 0.7 + 0.3, strength);
        glow = (0.6 * body + tipLine) * inner * left;
        core = tipLine * left;
    }

    vec3 light = vertexColor.rgb * glow + vec3(1.0) * core * heat;
    light *= strength * ColorModulator.rgb;
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
