#version 150

uniform vec2 ShadowCenter;
uniform float ShadowRadius;
uniform float EclipseIntensity;
uniform float PenumbraIntensity;
uniform vec3 SkyColor;
uniform vec3 MoonTint;
uniform float MoonAlpha;

in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec2 point = vec2((texCoord0.x - 0.5) * 2.0, (0.5 - texCoord0.y) * 2.0);
    vec2 shadowOffset = abs(point - ShadowCenter);
    float shadowDistance = max(shadowOffset.x, shadowOffset.y);

    // The body is an 8x8 Minecraft moon. Its normalized width is two units, so one
    // moon texel is exactly 2/8 = 0.25. Both the umbra and penumbra stay square.
    const float PENUMBRA_WIDTH = 0.25;
    float antialiasWidth = max(fwidth(shadowDistance), 0.001);
    float umbra = 1.0 - smoothstep(ShadowRadius - antialiasWidth,
            ShadowRadius + antialiasWidth, shadowDistance);
    float outerShadow = 1.0 - smoothstep(ShadowRadius + PENUMBRA_WIDTH - antialiasWidth,
            ShadowRadius + PENUMBRA_WIDTH + antialiasWidth, shadowDistance);
    float penumbra = clamp(outerShadow - umbra, 0.0, 1.0);

    float umbraIntensity = clamp(EclipseIntensity, 0.0, 1.0);
    float visualIntensity = clamp(max(PenumbraIntensity, umbraIntensity), 0.0, 1.0);

    // The whole visible lunar face begins reddening as soon as the one-pixel
    // penumbra has positive contact. Pixels inside that penumbra keep the same
    // base red and receive a fixed, deeper-red addition.
    float baseRedAlpha = 0.80 * pow(visualIntensity, 0.55) * (1.0 - umbra);
    const float PENUMBRA_EXTRA_RED_ALPHA = 0.16;
    float penumbraExtraAlpha = PENUMBRA_EXTRA_RED_ALPHA * penumbra;
    // The umbra is an actual opaque pixel-disc cover. Daylight fading belongs in RGB,
    // not in its alpha, otherwise the original sky leaks through the shadow center.
    float umbraAlpha = umbra;
    float opacity = clamp(baseRedAlpha + penumbraExtraAlpha + umbraAlpha, 0.0, 1.0);
    if (opacity <= 0.001) {
        discard;
    }

    vec3 inheritedSky = clamp(SkyColor, 0.0, 1.0);
    vec3 inheritedMoon = clamp(MoonTint, 0.0, 1.0);
    float moonVisibility = clamp(MoonAlpha, 0.0, 1.0);
    // Mixing every eclipse layer toward the live sky color is algebraically equivalent
    // to the ordinary lunar fade over a sky-colored body cover, while keeping the umbra
    // itself opaque. Sunrise, sunset and weather therefore tint the shadow continuously.
    vec3 bloodRed = mix(inheritedSky,
            inheritedMoon * vec3(0.48, 0.035, 0.018), moonVisibility);
    vec3 penumbraDeepRed = mix(inheritedSky,
            inheritedMoon * vec3(0.18, 0.010, 0.008), moonVisibility);
    vec3 umbraColor = mix(inheritedSky,
            inheritedMoon * vec3(0.042, 0.003, 0.002), moonVisibility);
    vec3 weightedColor = bloodRed * baseRedAlpha
            + penumbraDeepRed * penumbraExtraAlpha
            + umbraColor * umbraAlpha;
    fragColor = vec4(weightedColor
            / max(baseRedAlpha + penumbraExtraAlpha + umbraAlpha, 0.0001), opacity);
}
