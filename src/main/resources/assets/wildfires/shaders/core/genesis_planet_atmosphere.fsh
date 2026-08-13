/*
 * Adapted from VS: Genesis planet_atmosphere.fsh.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236
 * Licensed under the Apache License, Version 2.0.
 * Modified by Wildfires: retained Genesis' diagonal-plane cube-limb and illumination model,
 * removed the fixed cube rotation, and made palette, density and LOD alpha data-driven.
 * Added the same finite square-star / rotating satellite OBB visibility used by the surface pass.
 */
#version 150

in vec3 localEntryPosition;

uniform vec3 CameraPosition;
uniform vec3 LightDirection;
uniform vec3 DayColor;
uniform vec3 SunsetColor;
uniform vec3 NightColor;
uniform vec3 RegionBrightness;
uniform vec2 LightTransitions;
uniform vec2 LimbParameters;
uniform vec2 OpacityExposure;
uniform float AtmosphereThickness;
uniform float Density;
uniform float Alpha;
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

float cubeRadius(vec3 point) {
    vec3 absolute = abs(point);
    return max(max(absolute.x, absolute.y), absolute.z);
}

bool rayCubeShadow(vec3 origin, vec3 direction, vec3 center, float halfSize,
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

bool atmosphereSampleBlocked(vec3 point, vec3 direction) {
    if (ShadowCount > 0 && rayCubeShadow(point, direction, ShadowCenter0, ShadowHalfSize0,
            ShadowAxisX0, ShadowAxisY0, ShadowAxisZ0)) return true;
    if (ShadowCount > 1 && rayCubeShadow(point, direction, ShadowCenter1, ShadowHalfSize1,
            ShadowAxisX1, ShadowAxisY1, ShadowAxisZ1)) return true;
    if (ShadowCount > 2 && rayCubeShadow(point, direction, ShadowCenter2, ShadowHalfSize2,
            ShadowAxisX2, ShadowAxisY2, ShadowAxisZ2)) return true;
    if (ShadowCount > 3 && rayCubeShadow(point, direction, ShadowCenter3, ShadowHalfSize3,
            ShadowAxisX3, ShadowAxisY3, ShadowAxisZ3)) return true;
    return false;
}

float atmosphereDirectVisibility(vec3 point, vec3 directionToStar) {
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
            visible += atmosphereSampleBlocked(point, sampleDirection) ? 0.0 : 1.0;
        }
    }
    return visible / 9.0;
}

vec2 rayBox(vec3 origin, vec3 direction, float halfSize) {
    vec3 safeDirection = direction;
    if (abs(safeDirection.x) < 1.0e-6) safeDirection.x = safeDirection.x < 0.0 ? -1.0e-6 : 1.0e-6;
    if (abs(safeDirection.y) < 1.0e-6) safeDirection.y = safeDirection.y < 0.0 ? -1.0e-6 : 1.0e-6;
    if (abs(safeDirection.z) < 1.0e-6) safeDirection.z = safeDirection.z < 0.0 ? -1.0e-6 : 1.0e-6;
    vec3 first = (-vec3(halfSize) - origin) / safeDirection;
    vec3 second = (vec3(halfSize) - origin) / safeDirection;
    vec3 minimum = min(first, second);
    vec3 maximum = max(first, second);
    return vec2(max(max(minimum.x, minimum.y), minimum.z),
                min(min(maximum.x, maximum.y), maximum.z));
}

float diagonalMinimum(vec3 rayStart, vec3 rayDirection, float limit) {
    float minimumRadius = cubeRadius(rayStart);
    float denominator;
    float distance;
    vec3 intersection;

    denominator = rayDirection.y - rayDirection.x;
    if (abs(denominator) > 1.0e-4) {
        distance = (rayStart.x - rayStart.y) / denominator;
        if (distance >= 0.0 && distance <= limit) {
            intersection = rayStart + rayDirection * distance;
            minimumRadius = min(minimumRadius, cubeRadius(intersection));
        }
    }
    denominator = rayDirection.y + rayDirection.x;
    if (abs(denominator) > 1.0e-4) {
        distance = -(rayStart.x + rayStart.y) / denominator;
        if (distance >= 0.0 && distance <= limit) {
            intersection = rayStart + rayDirection * distance;
            minimumRadius = min(minimumRadius, cubeRadius(intersection));
        }
    }
    denominator = rayDirection.y - rayDirection.z;
    if (abs(denominator) > 1.0e-4) {
        distance = (rayStart.z - rayStart.y) / denominator;
        if (distance >= 0.0 && distance <= limit) {
            intersection = rayStart + rayDirection * distance;
            minimumRadius = min(minimumRadius, cubeRadius(intersection));
        }
    }
    denominator = rayDirection.y + rayDirection.z;
    if (abs(denominator) > 1.0e-4) {
        distance = -(rayStart.z + rayStart.y) / denominator;
        if (distance >= 0.0 && distance <= limit) {
            intersection = rayStart + rayDirection * distance;
            minimumRadius = min(minimumRadius, cubeRadius(intersection));
        }
    }
    denominator = rayDirection.x - rayDirection.z;
    if (abs(denominator) > 1.0e-4) {
        distance = (rayStart.z - rayStart.x) / denominator;
        if (distance >= 0.0 && distance <= limit) {
            intersection = rayStart + rayDirection * distance;
            minimumRadius = min(minimumRadius, cubeRadius(intersection));
        }
    }
    denominator = rayDirection.x + rayDirection.z;
    if (abs(denominator) > 1.0e-4) {
        distance = -(rayStart.z + rayStart.x) / denominator;
        if (distance >= 0.0 && distance <= limit) {
            intersection = rayStart + rayDirection * distance;
            minimumRadius = min(minimumRadius, cubeRadius(intersection));
        }
    }
    return minimumRadius;
}

void main() {
    vec3 rayDirection = normalize(localEntryPosition - CameraPosition);
    vec2 outerHit = rayBox(CameraPosition, rayDirection, AtmosphereThickness);
    if (outerHit.y < max(outerHit.x, 0.0)) {
        discard;
    }

    vec2 innerHit = rayBox(CameraPosition, rayDirection, 1.0);
    float surfaceDistance = innerHit.x;
    if (surfaceDistance < -0.1 || innerHit.y < max(innerHit.x, 0.0)) {
        surfaceDistance = outerHit.y;
    }

    float outerEntry = max(outerHit.x, 0.0);
    vec3 atmosphereEntry = CameraPosition + rayDirection * outerEntry;
    vec3 surfaceEntry = CameraPosition + rayDirection * surfaceDistance;
    float minimumRadius = diagonalMinimum(CameraPosition, rayDirection,
            max(surfaceDistance, outerHit.y));

    float opacity;
    if (minimumRadius < 1.0) {
        opacity = 1.0 - dot(normalize(surfaceEntry), -rayDirection);
        opacity = pow(max(opacity, 0.0), LimbParameters.y) * LimbParameters.x;
    } else {
        float shellWidth = max(AtmosphereThickness - 1.0, 1.0e-5);
        opacity = clamp(1.0 - (minimumRadius - 1.0) / shellWidth, 0.0, 1.0);
        opacity = pow(max(opacity, 0.0), LimbParameters.y);
    }

    vec3 shellEntry = atmosphereEntry;
    vec3 shellExit = surfaceEntry;
    vec3 light = normalize(LightDirection);
    float shellPath = max(length(shellEntry - shellExit), 1.0e-5);
    // Keep Genesis' original path-normalized entry/exit lighting. It confines atmosphere colour
    // to the actual cube shell instead of inventing an orbit-wide blue/orange sunset composite.
    float lightDot = (dot(shellEntry, -light) + dot(shellExit, -light))
            / (2.0 * shellPath);
    float dayFactor = clamp(lightDot * LightTransitions.x, 0.0, 1.0);
    float nightFactor = clamp(lightDot * -LightTransitions.y, 0.0, 1.0);
    vec3 dayColor = DayColor * RegionBrightness.x;
    vec3 sunsetColor = SunsetColor * RegionBrightness.y;
    vec3 nightColor = NightColor * RegionBrightness.z;
    vec3 color = mix(mix(sunsetColor, nightColor, nightFactor), dayColor, dayFactor);
    float visibility = atmosphereDirectVisibility((shellEntry + shellExit) * 0.5, -light);
    // Preserve 25% ambient scattering at totality and leave the already-night region unchanged.
    float shadowInfluence = (1.0 - visibility) * (1.0 - nightFactor) * 0.75;
    color = mix(color, nightColor, shadowInfluence);
    opacity = clamp(opacity * max(Density, 0.0) * OpacityExposure.y * Alpha,
            0.0, OpacityExposure.x);
    if (opacity <= 0.001) {
        discard;
    }
    fragColor = vec4(color, opacity);
}
