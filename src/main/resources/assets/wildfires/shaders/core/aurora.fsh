#version 150

// Created by Mattenii: https://www.shadertoy.com/view/MsjfRG
// CC BY-NC-SA 3.0. Adapted from TFCCaelum's split shader to Minecraft's
// Forge-managed GLSL 150 pipeline and the wildfires namespace.

uniform float Time;
uniform vec2 Resolution;
uniform vec4 TopColor;
uniform vec4 MiddleColor;
uniform vec4 BottomColor;
uniform float Alpha;

in vec2 texCoord0;
out vec4 fragColor;

const vec2 HASH_CONST = vec2(12.9898, 78.233);
const vec3 LUMA_FACTORS = vec3(0.2841, 0.5722, 0.1437);
const float LUMA_THRESHOLD = 0.25;

float hash(vec2 co) {
    return fract(sin(dot(co, HASH_CONST)) * 43758.5453);
}

float hash(float x, float y) {
    return hash(vec2(x, y));
}

float shash(vec2 co) {
    float x = co.x;
    float y = co.y;
    float corners = (hash(x - 1.0, y - 1.0) + hash(x + 1.0, y - 1.0)
            + hash(x - 1.0, y + 1.0) + hash(x + 1.0, y + 1.0)) / 16.0;
    float sides = (hash(x - 1.0, y) + hash(x + 1.0, y)
            + hash(x, y - 1.0) + hash(x, y + 1.0)) / 8.0;
    float center = hash(co) / 4.0;
    return corners + sides + center;
}

float noise(vec2 co) {
    vec2 pos = floor(co);
    vec2 fpos = co - pos;
    fpos = (3.0 - 2.0 * fpos) * fpos * fpos;

    float c1 = shash(pos);
    float c2 = shash(pos + vec2(0.0, 1.0));
    float c3 = shash(pos + vec2(1.0, 0.0));
    float c4 = shash(pos + vec2(1.0, 1.0));
    return mix(mix(c1, c3, fpos.x), mix(c2, c4, fpos.x), fpos.y);
}

float pnoise1(vec2 co) {
    return noise(co);
}

float pnoise2(vec2 co) {
    return (noise(co) + noise(2.0 * co) * 0.5) / 1.5;
}

float pnoise3(vec2 co) {
    return (noise(co) + noise(2.0 * co) * 0.5
            + noise(4.0 * co) * 0.25) / 1.75;
}

float pnoise4(vec2 co) {
    return (noise(co) + noise(2.0 * co) * 0.5
            + noise(4.0 * co) * 0.25
            + noise(8.0 * co) * 0.125) / 1.875;
}

vec2 fbm1(vec2 p) {
    return vec2(pnoise1(p + vec2(Time, 0.0)),
            pnoise1(p + vec2(-Time, 0.0)));
}

vec2 fbm2(vec2 p) {
    return vec2(pnoise2(p + vec2(Time, 0.0)),
            pnoise2(p + vec2(-Time, 0.0)));
}

vec2 fbm3(vec2 p) {
    return vec2(pnoise3(p + vec2(Time, 0.0)),
            pnoise3(p + vec2(-Time, 0.0)));
}

vec2 fbm4(vec2 p) {
    return vec2(pnoise4(p + vec2(Time, 0.0)),
            pnoise4(p + vec2(-Time, 0.0)));
}

float fbm2_1(vec2 p) {
    return pnoise1(p + 10.0 * fbm1(p) + vec2(0.0, Time));
}

float fbm2_2(vec2 p) {
    return pnoise2(p + 10.0 * fbm2(p) + vec2(0.0, Time));
}

float fbm2_3(vec2 p) {
    return pnoise3(p + 10.0 * fbm3(p) + vec2(0.0, Time));
}

float fbm2_4(vec2 p) {
    return pnoise4(p + 10.0 * fbm4(p) + vec2(0.0, Time));
}

vec3 lights(vec2 co) {
    float red = fbm2_1(co * vec2(1.0, 0.5));
    float displacement = pnoise1(2.0 * co + vec2(0.3 * Time));
    vec3 redColor = TopColor.rgb * red
            * smoothstep(0.0, 2.5 + displacement * red, co.y)
            * smoothstep(-5.0, 1.0, 5.0 - co.y - 2.0 * displacement);

    float green = fbm2_4(co * vec2(2.0, 0.5));
    vec3 greenColor = 0.8 * MiddleColor.rgb
            * clamp(2.0 * pow((3.0 - 2.0 * green) * green * green, 2.5)
                    - 0.5 * co.y, 0.0, 1.0)
            * smoothstep(-2.0 * displacement, 0.0, co.y)
            * smoothstep(0.0, 0.3, 1.1 + displacement - co.y);

    green = fbm2_2(co * vec2(1.0, 0.2));
    greenColor += 0.5 * MiddleColor.rgb
            * clamp(2.0 * pow((3.0 - 2.0 * green) * green * green, 2.5)
                    - 0.5 * co.y, 0.0, 1.0)
            * smoothstep(-2.0 * displacement, 0.0, co.y)
            * smoothstep(0.0, 0.3, 1.1 + displacement - co.y);

    float lower = pnoise1(vec2(5.0 * co.x, 5.0 * Time));
    vec3 lowerColor = BottomColor.rgb * pow(lower + 0.1, 2.0)
            * smoothstep(-2.0 * displacement, 0.0, co.y + 0.2)
            * smoothstep(-lower, 0.0, -co.y - 0.4);

    return redColor + greenColor + lowerColor;
}

void main() {
    vec2 uv = texCoord0;
    vec2 co = (uv * Resolution) / Resolution.y;

    float verticalDisplacement = 0.15
            * pnoise1(vec2(5.0 * uv.x, 0.3 * Time));
    vec2 auroraCoordinate = co;
    auroraCoordinate.y -= verticalDisplacement;
    auroraCoordinate *= 10.0 * uv.x + 20.0;

    vec3 color = 0.5 * lights(auroraCoordinate)
            * (smoothstep(0.3, 0.6,
                    pnoise1(vec2(10.0 * uv.x, 0.3 * Time)))
            + 0.5 * smoothstep(0.5, 0.7,
                    pnoise1(vec2(10.0 * uv.x, Time))));

    float adjustedAlpha = Alpha;
    float luminosity = dot(color, LUMA_FACTORS);
    if (luminosity < LUMA_THRESHOLD) {
        adjustedAlpha *= luminosity / LUMA_THRESHOLD;
        float difference = LUMA_THRESHOLD - luminosity;
        color *= 1.0 + LUMA_FACTORS * difference;
    }

    fragColor = vec4(color, adjustedAlpha);
}
