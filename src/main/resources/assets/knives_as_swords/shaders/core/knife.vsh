#version 330
#extension GL_ARB_separate_shader_objects : require

// Knives, drawn like entities but lit per pixel so that metal can reflect.
// Laid out like Minecraft's own entity shader; the normal and the direction to the eye are new.

#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec3 surfaceNormal;
layout(location = 4) out vec4 lightMapColor;
layout(location = 5) out vec4 overlayColor;
layout(location = 6) out vec2 texCoord0;
layout(location = 7) out vec3 directionToEye;
#ifdef GLINT
layout(location = 8) out vec2 texCoordGlint;
#endif
layout(location = 9) out vec3 roomAbove;

void main() {
    vec4 eyePosition = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * eyePosition;

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);

    vertexColor = Color;
    // Normals arrive in the space the light directions are given in; the eye has to be taken there too.
    // In menus the projection is flat and the viewer is straight in front of the screen.
    surfaceNormal = Normal;
    mat3 eyeToLightSpace = transpose(mat3(ModelViewMat));
    if (ProjMat[3][3] == 0.0) {
        directionToEye = eyeToLightSpace * -eyePosition.xyz;
        // In the world the two lights lean in from either side of straight up, so between them is "above".
        // (In the Nether they oppose each other; the first one alone has to do.)
        vec3 between = Light0_Direction + Light1_Direction;
        roomAbove = dot(between, between) > 0.01 ? normalize(between) : normalize(Light0_Direction);
    } else {
        directionToEye = eyeToLightSpace * vec3(0.0, 0.0, 1.0);
        // In menus the bright part of the room is over the viewer's shoulder (screens count y downwards),
        // so that a knife lying flat in a slot shows its colours instead of the room's dark half.
        roomAbove = eyeToLightSpace * normalize(vec3(-0.25, -0.55, 0.8));
    }

    lightMapColor = sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    texCoord0 = UV0;

#ifdef GLINT
    texCoordGlint = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
#endif
}
