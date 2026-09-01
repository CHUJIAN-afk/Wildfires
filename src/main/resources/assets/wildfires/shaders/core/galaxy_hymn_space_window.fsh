#version 150

/*
 * Adapted from ArcaneVortex 0.6.8 star_sky.fsh under the user's project-specific
 * visual authorization. Wildfires keeps the polygon geometry world-locked while restoring
 * the source screen-ray camera response as an opaque end-portal-style window.
 */
#define iterations 17
#define formuparam 0.53
#define volsteps 20
#define stepsize 0.1
#define zoom 0.800
#define tile 0.850
#define brightness 0.0015
#define darkmatter 0.100
#define distfading 0.730
#define saturation 0.850

uniform vec2 ScreenSize;
uniform vec3 CameraPosition;
uniform float CameraYaw;
uniform float CameraPitch;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

in vec3 cameraRelativePos;
out vec4 fragColor;

void main() {
    vec4 clipPos = ProjMat * ModelViewMat * vec4(cameraRelativePos, 1.0);
    vec2 displayUV = (clipPos.xy / clipPos.w) * 0.5;
    displayUV.x *= ScreenSize.x / max(1.0, ScreenSize.y);
    vec3 dir = normalize(vec3(displayUV * zoom, 1.0));

    float cp = cos(CameraPitch);
    float sp = sin(CameraPitch);
    dir = vec3(dir.x, dir.y * cp - dir.z * sp, dir.y * sp + dir.z * cp);
    float cy = cos(CameraYaw);
    float sy = sin(CameraYaw);
    dir = vec3(dir.x * cy + dir.z * sy, dir.y, -dir.x * sy + dir.z * cy);

    // No autonomous time animation: the field changes only when the camera
    // translates or when its world-space observation ray rotates.
    vec3 portalCamera = mod(CameraPosition, vec3(4096.0)) * 0.018;
    vec3 from = portalCamera + vec3(1.0, 0.5, -1.5);

    float s = 0.1;
    float fade = 1.0;
    vec3 v = vec3(0.0);
    for (int r = 0; r < volsteps; r++) {
        vec3 p = from + s * dir * 0.5;
        p = abs(vec3(tile) - mod(p, vec3(tile * 2.0)));
        float pa = 0.0;
        float a = 0.0;
        for (int i = 0; i < iterations; i++) {
            p = abs(p) / dot(p, p) - formuparam;
            a += abs(length(p) - pa);
            pa = length(p);
        }
        float dm = max(0.0, darkmatter - a * a * 0.001);
        a *= a * a;
        if (r > 6) fade *= 1.0 - dm;
        v += fade;
        v += vec3(s, s * s, s * s * s * s) * a * brightness * fade;
        fade *= distfading;
        s += stepsize;
    }

    v = mix(vec3(length(v)), v, saturation);
    vec3 finalColor = v * 0.018;
    finalColor = finalColor / (vec3(1.0) + finalColor);
    fragColor = vec4(finalColor, 1.0);
}
