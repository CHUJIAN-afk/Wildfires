/*
 * Adapted from VS: Genesis planet_textured.fsh.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236
 * Licensed under the Apache License, Version 2.0.
 * Modified by Wildfires: added transition alpha, removed forced far-depth/opaque output, and
 * retained Genesis' original 0.1 surface ambient so the night side remains visually legible.
 */
#version 150

in vec2 texCoord;
in vec4 vertexColor;
in vec3 surfaceDirection;

uniform sampler2D Sampler0;
uniform vec3 LightDirection;
uniform float Alpha;
uniform vec4 LayerColor;

out vec4 fragColor;

void main() {
    vec4 sampled = texture(Sampler0, texCoord);
    if (sampled.a <= 0.001 || Alpha <= 0.001) {
        discard;
    }
    // Position is the unambiguous planet-local cube corner.  Using the packed normal attribute
    // here caused driver/format-dependent interpolation and almost erased the stellar terminator.
    vec3 normal = normalize(surfaceDirection);
    float diffuse = max(dot(normal, -normalize(LightDirection)), 0.0);
    diffuse = 1.0 - 1.0 / (diffuse * diffuse * 5.0 + 1.0);
    // Keep Genesis' original ambient term.  Lowering this to 0.035 made the complete night side
    // collapse into display black and visually erased the otherwise-correct stellar terminator.
    float lighting = clamp(0.10 + diffuse, 0.0, 1.0);
    fragColor = vec4(sampled.rgb * vertexColor.rgb * LayerColor.rgb * lighting,
            sampled.a * Alpha * LayerColor.a);
}
