#version 330
#extension GL_ARB_separate_shader_objects : require

// The mod's menus: flat shapes with soft or round edges.
//
// There is no texture. Where a pixel lies within its quad (texCoord0) says what the quad is:
//   around (0, 0):   plain, the quad's colour all over
//   around (11, 11): light fading out from that point, gone at a distance of 1
//   around (21, 21): a disc of radius 1 about that point, with a clean edge
// Quarter discs make round corners; quads with the light point on one edge make the glow along a
// side.

#include <minecraft:dynamictransforms.glsl>

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;

layout(location = 0) out vec4 fragColor;

void main() {
    float cover;
    if (texCoord0.x < 5.0) {
        cover = 1.0;
    } else if (texCoord0.x < 16.0) {
        float near = clamp(1.0 - length(texCoord0 - 11.0), 0.0, 1.0);
        cover = near * near;
    } else {
        float away = length(texCoord0 - 21.0);
        float edge = max(fwidth(away), 1e-4);
        cover = 1.0 - smoothstep(1.0 - edge, 1.0, away);
    }

    vec4 color = vec4(vertexColor.rgb, vertexColor.a * cover) * ColorModulator;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
