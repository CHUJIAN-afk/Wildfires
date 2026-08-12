/*
 * Adapted from VS: Genesis planet_atmosphere.fsh.
 * Copyright 2025 jamesgreen26, JSJBDEV, Verquinox, jcm236
 * Licensed under the Apache License, Version 2.0.
 * Modified by Wildfires: retained Genesis' diagonal-plane cube-limb and illumination model,
 * removed the fixed cube rotation, and made palette, density and LOD alpha data-driven.
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

out vec4 fragColor;

float cubeRadius(vec3 point) {
    vec3 absolute = abs(point);
    return max(max(absolute.x, absolute.y), absolute.z);
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
    opacity = clamp(opacity * max(Density, 0.0) * OpacityExposure.y * Alpha,
            0.0, OpacityExposure.x);
    if (opacity <= 0.001) {
        discard;
    }
    fragColor = vec4(color, opacity);
}
