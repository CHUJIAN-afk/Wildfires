/* Waterfall-derived; SPDX-License-Identifier: CC-BY-NC-SA-4.0 */
#version 150

uniform sampler2D Sampler0;
uniform float Time;
uniform vec4 StartTint;
uniform vec4 EndTint;
uniform float TintFalloff;
uniform float Falloff;
uniform float Fresnel;
uniform float FresnelInvert;
uniform float Noise;
uniform float Brightness;
uniform float FadeIn;
uniform float FadeOut;
uniform float FalloffStart;
uniform float Symmetry;
uniform float SymmetryStrength;
uniform float Seed;
uniform float SpeedX;
uniform float SpeedY;
uniform float TileX;
uniform float TileY;
uniform float ClipBrightness;

in vec2 texCoord0;
in vec4 vertexColor;
in vec3 viewDirection;
in vec3 viewNormal;
in vec3 plumeDirection;
in float plumePos;
out vec4 fragColor;

float saturate(float value) { return clamp(value, 0.0, 1.0); }

void main() {
    vec2 scrollUV = texCoord0 + vec2(SpeedX * Time + Seed, SpeedY * Time + Seed);
    vec4 c = texture(Sampler0, scrollUV * vec2(TileX, TileY));
    vec3 plumeFlow = normalize(cross(cross(plumeDirection, viewNormal), viewNormal));
    vec3 view = normalize(cross(cross(viewDirection, plumeFlow), plumeFlow));
    float viewdot = abs(dot(viewNormal, view));
    float rim = smoothstep(0.0, 1.0, saturate(viewdot));
    float rim2 = clamp(1.0 - rim, 0.001, 10.0);
    float g = min(1.0, (1.0 + FalloffStart) * texCoord0.g);
    float fade = pow(g, Falloff);
    float v = pow(fade * (rim * 0.5 + 0.5), TintFalloff);
    vec4 gradient = mix(EndTint, StartTint, min(1.0, v));
    vec4 col = mix(vec4(0.5), c, Noise);
    vec4 noiseValue = mix(col, vec4(1.0), fade);
    fade *= smoothstep(0.0, FadeIn, plumePos);
    float fOut = FadeOut + 0.0001;
    fade *= max(0.0, saturate(viewdot) - max(0.0, (fOut + plumePos - 1.0) / fOut));
    float pi = 3.1415926535;
    fade *= 1.0 - SymmetryStrength + SymmetryStrength * pow(cos(Symmetry * pi * texCoord0.x), 2.0);
    vec4 emission = clamp(gradient
            * pow(vec4(rim), (vec4(1.0) - noiseValue + 0.5 * Noise) * Fresnel)
            * pow(vec4(rim2), clamp((vec4(1.0) - noiseValue + 0.5 * Noise) * FresnelInvert, 0.001, 10.0))
            * fade * noiseValue * Brightness * vertexColor, 0.0, ClipBrightness);
    fragColor = vec4(emission.rgb, 1.0);
}
