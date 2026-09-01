#version 150

uniform sampler2D DepthSampler;
uniform sampler2D ColorSampler;

uniform mat4 projectionMatrix;
uniform mat4 modelViewMatrix;

uniform vec3 cameraPos;
uniform vec3 projectilePos;
uniform vec2 screenSize;
uniform float time;
uniform float yaw;
uniform float pitch;

uniform float lightIntensity;
uniform float effectAlpha;

in vec2 texCoord;
out vec4 fragColor;

vec3 reconstructWorldPos(vec2 uv, float depth)
{
    float ndcDepth = depth * 2.0 - 1.0;
    vec4 ndcPos = vec4(uv * 2.0 - 1.0, ndcDepth, 1.0);
    vec4 viewPos = projectionMatrix * ndcPos;
    viewPos /= viewPos.w;
    vec4 worldPos = modelViewMatrix * viewPos;
    return worldPos.xyz;
}

vec3 computeNormal(vec3 worldPos)
{
    vec3 dx = dFdx(worldPos);
    vec3 dy = dFdy(worldPos);
    return normalize(cross(dx, dy));
}

vec3 chromaticAberration3D(vec2 uv, vec3 worldPos, vec3 normal, float intensity)
{
    vec3 blastDir = normalize(worldPos - projectilePos);
    float dist = length(worldPos - projectilePos);

    float chromaStrength = intensity * 0.008 / (1.0 + dist * 0.02);

    float normalInfluence = abs(dot(normal, blastDir));
    chromaStrength *= (0.5 + 0.5 * normalInfluence);

    vec2 screenCenter = vec2(0.5);
    vec2 radialDir = normalize(uv - screenCenter + vec2(0.001));

    vec2 blastScreen = vec2(dot(blastDir, vec3(1.0, 0.0, 0.0)), dot(blastDir, vec3(0.0, 1.0, 0.0)));

    vec2 chromaDir = normalize(mix(radialDir, normalize(blastScreen + vec2(0.001)), 0.4));

    vec2 offsetR = chromaDir * chromaStrength * 1.2;
    vec2 offsetG = vec2(0.0);
    vec2 offsetB = -chromaDir * chromaStrength * 1.0;

    float depthR = texture(DepthSampler, clamp(uv + offsetR, 0.0, 1.0)).r;
    float depthG = texture(DepthSampler, clamp(uv + offsetG, 0.0, 1.0)).r;
    float depthB = texture(DepthSampler, clamp(uv + offsetB, 0.0, 1.0)).r;

    return vec3(depthR, depthG, depthB);
}

float computeBrightness(vec2 uv, float depth, float skyMask)
{
    if (depth >= 0.9999) return 0.0;

    vec3 wp = reconstructWorldPos(uv, depth);
    vec3 n = computeNormal(wp);

    float sf = smoothstep(0.15, 0.5, n.y);

    vec3 toPixel = wp - projectilePos;
    float lightDist = length(toPixel);
    vec3 incomingLight = -toPixel / max(lightDist, 0.001);

    float NdotL = max(dot(n, incomingLight), 0.0);
    float distAtten = 1.0 / (1.0 + lightDist * 0.003 + lightDist * lightDist * 0.00005);

    float ambient = 0.03;
    float diffuse = NdotL * distAtten;

    float effectiveAmbient = mix(ambient, 0.95, lightIntensity);
    float effectiveDiffuse = diffuse * mix(0.05, 4.0, lightIntensity);

    float brightness = effectiveAmbient + effectiveDiffuse;
    brightness = clamp(brightness, 0.0, 1.0);
    brightness *= (1.0 - sf);

    return brightness;
}

void main()
{
    vec2 uv = gl_FragCoord.xy / screenSize;
    float depth = texture(DepthSampler, uv).r;

    float burstPower = lightIntensity * lightIntensity;

    if (depth >= 0.9999)
    {
        float skyFlash = burstPower * 0.6;
        fragColor = vec4(vec3(skyFlash), effectAlpha);
        return;
    }

    vec3 worldPos = reconstructWorldPos(uv, depth);
    vec3 normal = computeNormal(worldPos);

    float skyFacing = smoothstep(0.15, 0.5, normal.y);

    vec3 toPixel = worldPos - projectilePos;
    float distToBlast = length(toPixel);
    vec3 blastDir = toPixel / max(distToBlast, 0.001);
    vec3 incomingLight = -blastDir;

    float elapsedPhase = 1.0 - lightIntensity;
    float shockwaveRadius = elapsedPhase * 80.0;
    float shockwaveWidth = 6.0 + elapsedPhase * 15.0;
    float shockwaveDist = abs(distToBlast - shockwaveRadius);
    float shockwave = exp(-shockwaveDist * shockwaveDist / (shockwaveWidth * shockwaveWidth));
    shockwave *= lightIntensity * 1.5;
    shockwave = clamp(shockwave, 0.0, 1.0);

    float NdotL = max(dot(normal, incomingLight), 0.0);
    float distAtten = 1.0 / (1.0 + distToBlast * mix(0.01, 0.005, burstPower)
    + distToBlast * distToBlast * mix(0.0002, 0.00005, burstPower));

    float ambient = 0.03;
    float diffuse = NdotL * distAtten;

    float effectiveAmbient = mix(ambient, 0.97, burstPower);
    float effectiveDiffuse = diffuse * mix(0.05, 5.0, burstPower);

    float brightness = effectiveAmbient + effectiveDiffuse;

    brightness += shockwave * 0.8;

    brightness = clamp(brightness, 0.0, 1.0);

    brightness *= (1.0 - skyFacing);

    float chromaIntensity = burstPower * 1.5;

    if (chromaIntensity > 0.01)
    {
        float chromaStrength = chromaIntensity * 0.012 / (1.0 + distToBlast * 0.015);
        float normalInfluence = 0.5 + 0.5 * abs(dot(normal, blastDir));
        chromaStrength *= normalInfluence;

        vec2 screenCenter = vec2(0.5);
        vec2 radialDir = uv - screenCenter;
        float radialLen = length(radialDir);
        radialDir = radialDir / max(radialLen, 0.001);

        float distFactor = exp(-distToBlast * 0.03) * 2.0;
        chromaStrength *= (0.5 + distFactor);

        vec2 chromaDir = radialDir;

        vec2 offsetR = chromaDir * chromaStrength * 1.5;
        vec2 offsetB = -chromaDir * chromaStrength * 1.2;

        float depthR = texture(DepthSampler, clamp(uv + offsetR, 0.0, 1.0)).r;
        float depthB = texture(DepthSampler, clamp(uv - offsetB, 0.0, 1.0)).r;

        float brightnessR;
        if (depthR >= 0.9999) brightnessR = burstPower * 0.6;
        else
        {
            vec3 wpR = reconstructWorldPos(uv + offsetR, depthR);
            vec3 nR = computeNormal(wpR);
            float sfR = smoothstep(0.15, 0.5, nR.y);
            vec3 toR = wpR - projectilePos;
            float dR = length(toR);
            float NdotLR = max(dot(nR, -toR / max(dR, 0.001)), 0.0);
            float attR = 1.0 / (1.0 + dR * 0.005 + dR * dR * 0.00005);
            brightnessR = mix(ambient, 0.97, burstPower) + NdotLR * attR * mix(0.05, 5.0, burstPower);
            float swDistR = abs(dR - shockwaveRadius);
            brightnessR += exp(-swDistR * swDistR / (shockwaveWidth * shockwaveWidth)) * lightIntensity * 1.2;
            brightnessR = clamp(brightnessR, 0.0, 1.0) * (1.0 - sfR);
        }

        float brightnessB;
        if (depthB >= 0.9999) {
            brightnessB = burstPower * 0.6;
        } else {
            vec3 wpB = reconstructWorldPos(uv + offsetB, depthB);
            vec3 nB = computeNormal(wpB);
            float sfB = smoothstep(0.15, 0.5, nB.y);
            vec3 toB = wpB - projectilePos;
            float dB = length(toB);
            float NdotLB = max(dot(nB, -toB / max(dB, 0.001)), 0.0);
            float attB = 1.0 / (1.0 + dB * 0.005 + dB * dB * 0.00005);
            brightnessB = mix(ambient, 0.97, burstPower) + NdotLB * attB * mix(0.05, 5.0, burstPower);
            float swDistB = abs(dB - shockwaveRadius);
            brightnessB += exp(-swDistB * swDistB / (shockwaveWidth * shockwaveWidth)) * lightIntensity * 1.2;
            brightnessB = clamp(brightnessB, 0.0, 1.0) * (1.0 - sfB);
        }

        float chromaMix = clamp(chromaIntensity, 0.0, 1.0);
        float finalR = mix(brightness, brightnessR, chromaMix);
        float finalG = brightness;
        float finalB = mix(brightness, brightnessB, chromaMix);

        float overexposure = burstPower * burstPower * 0.3;

        finalR += overexposure * 1.0;
        finalG += overexposure * 0.9;
        finalB += overexposure * 0.95;

        finalR = clamp(finalR, 0.0, 1.0);
        finalG = clamp(finalG, 0.0, 1.0);
        finalB = clamp(finalB, 0.0, 1.0);

        fragColor = vec4(finalR, finalG, finalB, effectAlpha);
    }
    else fragColor = vec4(vec3(brightness), effectAlpha);
}