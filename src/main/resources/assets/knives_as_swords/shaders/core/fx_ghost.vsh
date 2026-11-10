#version 330
#extension GL_ARB_separate_shader_objects : require

// A knife's own shape drawn as light: the glow around it, and its afterimages. See fx_ghost.fsh.

#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>

layout(location = 0) in vec3 Position;
// x: where in the bands of light running along the knife this vertex is (one band per unit).
// y: how much light the surface gives off evenly, on top of its bright rim.
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;
layout(location = 3) in vec3 Normal;

layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
layout(location = 2) out vec4 vertexColor;
layout(location = 3) out vec2 texCoord0;
layout(location = 4) out vec3 surfaceNormal;
layout(location = 5) out vec3 directionToEye;

void main() {
    vec4 eyePosition = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * eyePosition;

    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    vertexColor = Color;
    texCoord0 = UV0;

    // The normal comes in the space positions are given in; the eye has to be taken there too.
    // In menus the projection is flat and the viewer is straight in front of the screen.
    surfaceNormal = Normal;
    mat3 eyeToModelSpace = transpose(mat3(ModelViewMat));
    if (ProjMat[3][3] == 0.0) {
        directionToEye = eyeToModelSpace * -eyePosition.xyz;
    } else {
        directionToEye = eyeToModelSpace * vec3(0.0, 0.0, 1.0);
    }
}
