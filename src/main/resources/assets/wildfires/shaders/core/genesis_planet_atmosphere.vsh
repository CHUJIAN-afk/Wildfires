/*
 * Adapted from VS: Genesis planet_atmosphere.vsh.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236
 * Licensed under the Apache License, Version 2.0.
 * Modified by Wildfires for a unit static cube mesh and data-driven atmosphere color.
 */
#version 150

in vec3 Position;
in vec4 Color;

out vec3 localEntryPosition;

uniform mat4 ProjMat;
uniform mat4 ModelViewMat;
uniform float AtmosphereThickness;

void main() {
    localEntryPosition = (Color.rgb * 2.0 - 1.0) * AtmosphereThickness;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
