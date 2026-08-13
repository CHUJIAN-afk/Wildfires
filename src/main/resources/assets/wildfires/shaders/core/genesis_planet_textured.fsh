/*
 * Adapted from VS: Genesis planet_textured.fsh.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236
 * Licensed under the Apache License, Version 2.0.
 * Modified by Wildfires: added transition alpha, removed forced far-depth/opaque output, and
 * retained Genesis' original 0.1 surface ambient so the night side remains visually legible.
 * Added Wildfires finite-square-star sampling against rotating satellite OBBs; this produces
 * geometric cube shadows that remain continuous across planet face edges and corners.
 */
#version 150

in vec2 texCoord;
in vec4 vertexColor;
in vec3 surfaceDirection;

uniform sampler2D Sampler0;
uniform vec3 LightDirection;
uniform float Alpha;
uniform vec4 LayerColor;
uniform float ReceiverRadius;
uniform int ShadowCount;
uniform float SunHalfTangent;
uniform vec3 ShadowCenter0;
uniform vec3 ShadowCenter1;
uniform vec3 ShadowCenter2;
uniform vec3 ShadowCenter3;
uniform float ShadowHalfSize0;
uniform float ShadowHalfSize1;
uniform float ShadowHalfSize2;
uniform float ShadowHalfSize3;
uniform vec3 ShadowAxisX0;
uniform vec3 ShadowAxisX1;
uniform vec3 ShadowAxisX2;
uniform vec3 ShadowAxisX3;
uniform vec3 ShadowAxisY0;
uniform vec3 ShadowAxisY1;
uniform vec3 ShadowAxisY2;
uniform vec3 ShadowAxisY3;
uniform vec3 ShadowAxisZ0;
uniform vec3 ShadowAxisZ1;
uniform vec3 ShadowAxisZ2;
uniform vec3 ShadowAxisZ3;

out vec4 fragColor;

bool rayCube(vec3 origin, vec3 direction, vec3 center, float halfSize,
             vec3 axisX, vec3 axisY, vec3 axisZ) {
    vec3 relative = origin - center;
    vec3 localOrigin = vec3(dot(relative, axisX), dot(relative, axisY), dot(relative, axisZ));
    vec3 localDirection = vec3(dot(direction, axisX), dot(direction, axisY), dot(direction, axisZ));
    vec3 safeDirection = localDirection;
    if (abs(safeDirection.x) < 1.0e-6) safeDirection.x = safeDirection.x < 0.0 ? -1.0e-6 : 1.0e-6;
    if (abs(safeDirection.y) < 1.0e-6) safeDirection.y = safeDirection.y < 0.0 ? -1.0e-6 : 1.0e-6;
    if (abs(safeDirection.z) < 1.0e-6) safeDirection.z = safeDirection.z < 0.0 ? -1.0e-6 : 1.0e-6;
    vec3 first = (-vec3(halfSize) - localOrigin) / safeDirection;
    vec3 second = (vec3(halfSize) - localOrigin) / safeDirection;
    vec3 minimum = min(first, second);
    vec3 maximum = max(first, second);
    float entry = max(max(minimum.x, minimum.y), minimum.z);
    float exitDistance = min(min(maximum.x, maximum.y), maximum.z);
    return exitDistance >= max(entry, 1.0e-4);
}

bool sampleBlocked(vec3 point, vec3 direction) {
    if (ShadowCount > 0 && rayCube(point, direction, ShadowCenter0, ShadowHalfSize0,
            ShadowAxisX0, ShadowAxisY0, ShadowAxisZ0)) return true;
    if (ShadowCount > 1 && rayCube(point, direction, ShadowCenter1, ShadowHalfSize1,
            ShadowAxisX1, ShadowAxisY1, ShadowAxisZ1)) return true;
    if (ShadowCount > 2 && rayCube(point, direction, ShadowCenter2, ShadowHalfSize2,
            ShadowAxisX2, ShadowAxisY2, ShadowAxisZ2)) return true;
    if (ShadowCount > 3 && rayCube(point, direction, ShadowCenter3, ShadowHalfSize3,
            ShadowAxisX3, ShadowAxisY3, ShadowAxisZ3)) return true;
    return false;
}

float directVisibility(vec3 point, vec3 directionToStar) {
    if (ShadowCount <= 0) return 1.0;
    vec3 helper = abs(directionToStar.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 right = normalize(cross(helper, directionToStar));
    vec3 up = normalize(cross(directionToStar, right));
    float visible = 0.0;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec3 sampleDirection = normalize(directionToStar
                    + right * (float(x) * SunHalfTangent)
                    + up * (float(y) * SunHalfTangent));
            visible += sampleBlocked(point, sampleDirection) ? 0.0 : 1.0;
        }
    }
    return visible / 9.0;
}

void main() {
    vec4 sampled = texture(Sampler0, texCoord);
    if (sampled.a <= 0.001 || Alpha <= 0.001) {
        discard;
    }
    // Position is the unambiguous planet-local cube corner.  Using the packed normal attribute
    // here caused driver/format-dependent interpolation and almost erased the stellar terminator.
    vec3 normal = normalize(surfaceDirection);
    vec3 directionToStar = -normalize(LightDirection);
    float diffuse = max(dot(normal, directionToStar), 0.0);
    diffuse = 1.0 - 1.0 / (diffuse * diffuse * 5.0 + 1.0);
    // Only direct stellar light is eclipsed. Genesis' ambient term remains intact in the umbra.
    diffuse *= directVisibility(surfaceDirection * ReceiverRadius, directionToStar);
    // Keep Genesis' original ambient term.  Lowering this to 0.035 made the complete night side
    // collapse into display black and visually erased the otherwise-correct stellar terminator.
    float lighting = clamp(0.10 + diffuse, 0.0, 1.0);
    fragColor = vec4(sampled.rgb * vertexColor.rgb * LayerColor.rgb * lighting,
            sampled.a * Alpha * LayerColor.a);
}
