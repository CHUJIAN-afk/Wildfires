/*
 * Adapted from VS: Genesis planet_textured.vsh.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236
 * Licensed under the Apache License, Version 2.0.
 * Modified by Wildfires: removed unused fog/size varyings, added explicit alpha support, and
 * retains the actual cube surface position for seam-free three-dimensional satellite shadows.
 */
#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in vec3 Normal;

out vec2 texCoord;
out vec4 vertexColor;
out vec3 surfaceDirection;

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord = UV0;
    vertexColor = Color;
    // Linear interpolation now stays on the actual planar cube face. The fragment shader still
    // normalizes this value for Genesis lighting, while eclipse rays use the unnormalized point.
    surfaceDirection = Position;
}
