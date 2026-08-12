/*
 * Adapted from VS: Genesis planet_textured.vsh.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236
 * Licensed under the Apache License, Version 2.0.
 * Modified by Wildfires: removed unused fog/size varyings and added explicit alpha support.
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
    surfaceDirection = normalize(Position);
}
