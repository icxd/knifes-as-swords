#version 330
#extension GL_ARB_separate_shader_objects : require

// Knives, drawn like entities but lit per pixel so that metal can reflect.
//
// Minecraft has no surroundings a knife could mirror, so it mirrors an imagined room instead:
// bright above, dark below, with the two lights Minecraft already has as lamps. Which way is
// "above" comes from the vertex shader, because it differs between the world and menus.

#ifdef GLINT
#include <minecraft:globals.glsl>
#endif
#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
// Red: roughness. Green: metalness. Blue: ambient occlusion.
uniform sampler2D KnifeMaterial;

#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
layout(location = 2) in vec4 vertexColor;
layout(location = 3) in vec3 surfaceNormal;
layout(location = 4) in vec4 lightMapColor;
layout(location = 5) in vec4 overlayColor;
layout(location = 6) in vec2 texCoord0;
layout(location = 7) in vec3 directionToEye;
#ifdef GLINT
layout(location = 8) in vec2 texCoordGlint;
#endif
layout(location = 9) in vec3 roomAbove;

layout(location = 0) out vec4 fragColor;

const vec3 ROOM_ABOVE = vec3(1.55, 1.58, 1.65);
const vec3 ROOM_BELOW = vec3(0.20, 0.19, 0.18);
const float LAMP_STRENGTH = 0.9;
// Wood, rubber and paint get their light from Minecraft's soft shading, which is dimmer than the
// room. What they mirror of the room is dimmed to match, or dark wood would look like dark metal.
const float PLAIN_SHEEN = 0.6;

// What a mirror facing `direction` would show, blurred more the rougher the surface is.
vec3 room(vec3 direction, vec3 above, float roughness) {
    float height = dot(direction, above);
    float blur = mix(0.06, 1.1, roughness);
    return mix(ROOM_BELOW, ROOM_ABOVE, smoothstep(-blur, blur, height));
}

// The glint of one lamp on a surface of the given roughness.
float lamp(vec3 normal, vec3 toEye, vec3 toLamp, float roughness) {
    float facing = max(dot(normal, toLamp), 0.0);
    vec3 halfway = normalize(toLamp + toEye);
    float alpha = roughness * roughness;
    float sharpness = clamp(2.0 / (alpha * alpha) - 2.0, 1.0, 2048.0);
    float glint = pow(max(dot(normal, halfway), 0.0), sharpness) * (sharpness + 8.0) / 25.0;
    return min(glint, 6.0) * facing;
}

vec4 calculateFinalColor(vec4 color) {
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;

#ifdef GLINT
    vec4 glintColor = GlintAlpha * texture(GlintSampler, texCoordGlint);
    color.rgb += glintColor.rgb * glintColor.rgb;
#endif

    return apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    vec3 material = texture(KnifeMaterial, texCoord0).rgb;
    float roughness = clamp(material.r, 0.06, 1.0);
    float metalness = clamp(material.g, 0.0, 1.0);
    float occlusion = mix(1.0, material.b, 0.85);

    vec3 normal = normalize(surfaceNormal);
    vec3 toEye = normalize(directionToEye);
    // Never quite edge-on: interpolated normals can point slightly away from the eye.
    float facing = clamp(dot(normal, toEye), 0.02, 1.0);
    vec3 above = normalize(roomAbove);

    // Lighting is worked out in linear light and turned back at the end.
    vec3 albedo = pow(color.rgb, vec3(2.2));
    // Seen head-on, metal mirrors in its own colour and anything else only a few percent.
    vec3 mirrorColor = mix(vec3(0.04 * (1.0 - 0.6 * roughness)), albedo, metalness);
    // Seen at a slant everything mirrors more, but only smooth surfaces noticeably so: the rise
    // goes with the cube of the smoothness, which leaves wood and rubber dull from every side.
    float smoothness = 1.0 - roughness;
    float slant = smoothness * smoothness * smoothness * pow(1.0 - facing, 5.0);
    vec3 mirror = (mirrorColor + (1.0 - mirrorColor) * slant) * mix(PLAIN_SHEEN, 1.0, metalness);

    // Paint and plastic: the same soft two-light shading everything else in Minecraft has.
    float soft = min(1.0, (max(dot(Light0_Direction, normal), 0.0) + max(dot(Light1_Direction, normal), 0.0)) * MINECRAFT_LIGHT_POWER + MINECRAFT_AMBIENT_LIGHT);
    vec3 lit = albedo * (1.0 - metalness) * soft;

    // Metal, and the sheen on everything else: the room, and the lamps in it.
    lit += room(reflect(-toEye, normal), above, roughness) * mirror;
    lit += (lamp(normal, toEye, normalize(Light0_Direction), roughness) + lamp(normal, toEye, normalize(Light1_Direction), roughness)) * LAMP_STRENGTH * mirror;
    lit *= occlusion;

    color.rgb = pow(clamp(lit, 0.0, 1.0), vec3(1.0 / 2.2));

#ifdef GLINT
    color.a = max(color.a, GlintAlpha);
#endif

    fragColor = calculateFinalColor(color);
}
