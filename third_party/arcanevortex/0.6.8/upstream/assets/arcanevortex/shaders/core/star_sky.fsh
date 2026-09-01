#version 150

#define iterations 17
#define formuparam 0.53
#define volsteps 20
#define stepsize 0.1
#define zoom 0.800
#define tile 0.850
#define speed 0.01
#define brightness 0.0015
#define darkmatter 0.100
#define distfading 0.730
#define saturation 0.850
#define M_PI 3.1415926535897932384626433832795

uniform float time;
uniform float iZoom;
uniform vec2 screenSize;
uniform vec3 cameraPos;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

uniform float yaw;
uniform float pitch;

in vec4 vertexColor;
in vec3 worldPos;
in vec3 viewPos;

out vec4 fragColor;

mat4 rotationMatrix(vec3 axis, float angle) {
    axis = normalize(axis);
    float s = sin(angle);
    float c = cos(angle);
    float oc = 1.0 - c;

    return mat4(oc * axis.x * axis.x + c,           oc * axis.x * axis.y - axis.z * s,  oc * axis.z * axis.x + axis.y * s,  0.0,
    oc * axis.x * axis.y + axis.z * s,  oc * axis.y * axis.y + c,           oc * axis.y * axis.z - axis.x * s,  0.0,
    oc * axis.z * axis.x - axis.y * s,  oc * axis.y * axis.z + axis.x * s,  oc * axis.z * axis.z + c,           0.0,
    0.0,                                0.0,                                0.0,                                1.0);
}

void main() {
    vec4 clipPos = ProjMat * ModelViewMat * vec4(worldPos, 1.0);
    vec2 ndc = clipPos.xy / clipPos.w;

    vec2 uv = ndc * 0.5;

    float aspectRatio = screenSize.x / screenSize.y;
    vec2 displayUV = uv;
    displayUV.x *= aspectRatio;

    vec3 dir = vec3(displayUV * zoom + iZoom, 1.0);
    float times = time * speed + 0.25;

    vec3 rayDir = normalize(dir);

    float cp = cos(pitch);
    float sp = sin(pitch);
    rayDir = vec3(
    rayDir.x,
    rayDir.y * cp - rayDir.z * sp,
    rayDir.y * sp + rayDir.z * cp);

    float cy = cos(yaw);
    float sy = sin(yaw);
    rayDir = vec3(
    rayDir.x * cy + rayDir.z * sy,
    rayDir.y,
    -rayDir.x * sy + rayDir.z * cy
    );

    dir = rayDir;

    float a1 = times * 0.3 + sin(times * 0.3) * 0.05;
    float a2 = times * 0.2 + cos(times * 0.7) * 0.05;

    vec3 axis1 = normalize(vec3(sin(times * 0.5), cos(times * 0.4), sin(times * 0.2)));
    vec3 axis2 = normalize(vec3(cos(times * 0.3), sin(times * 0.6), cos(times * 0.4)));

    mat4 rot1 = rotationMatrix(axis1, a1);
    mat4 rot2 = rotationMatrix(axis2, a2);

    dir = (rot1 * vec4(dir, 0.0)).xyz;
    dir = (rot2 * vec4(dir, 0.0)).xyz;

    vec3 from = vec3(1.0, 0.5, 0.5);
    from += vec3(times * 2.0, times, -2.0);

    from = (rot1 * vec4(from, 0.0)).xyz;
    from = (rot2 * vec4(from, 0.0)).xyz;

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

    vec3 finalColor = v * 0.01;
    float alpha = max(0.5, length(finalColor));

    fragColor = vec4(finalColor, alpha);
}
