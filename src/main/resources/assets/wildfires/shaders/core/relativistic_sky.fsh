#version 150

in vec3 observedDirection;

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec3 Velocity;
uniform float Beta;
uniform float AberrationBeta;
uniform float StarTrailStrength;
out vec4 fragColor;

// Constant-bounded integration: the compiler can unroll this and ordinary cruise frames skip it.
const int STAR_TRAIL_SAMPLES = 12;

vec2 ntmAtlasUv(vec3 direction) {
    vec3 ray = normalize(direction);
    vec3 magnitude = abs(ray);
    vec2 localUv;
    vec2 cell;

    // Exact inverse of NtmOrbitSkyRenderer.NIGHT_FACES.  Choosing by the dominant component
    // lets a shifted ray cross all six cube faces while the screen-facing cube stays unchanged.
    if (magnitude.x >= magnitude.y && magnitude.x >= magnitude.z) {
        if (ray.x < 0.0) {
            localUv = vec2((-ray.z / magnitude.x + 1.0) * 0.5,
                           (1.0 - ray.y / magnitude.x) * 0.5);
            cell = vec2(1.0, 1.0); // atlas cell 4, west
        } else {
            localUv = vec2((ray.z / magnitude.x + 1.0) * 0.5,
                           (1.0 - ray.y / magnitude.x) * 0.5);
            cell = vec2(2.0, 0.0); // atlas cell 2, east
        }
    } else if (magnitude.y >= magnitude.z) {
        localUv = vec2((1.0 - ray.z / magnitude.y) * 0.5,
                       ((ray.y > 0.0 ? -ray.x : ray.x) / magnitude.y + 1.0) * 0.5);
        cell = ray.y > 0.0 ? vec2(1.0, 0.0) : vec2(0.0, 0.0); // up 1 / down 0
    } else {
        localUv = vec2(((ray.z < 0.0 ? ray.x : -ray.x) / magnitude.z + 1.0) * 0.5,
                       (1.0 - ray.y / magnitude.z) * 0.5);
        cell = ray.z < 0.0 ? vec2(2.0, 1.0) : vec2(0.0, 1.0); // north 5 / south 3
    }

    // night.png is 2304x1536 (three by two 768px faces). Keep bilinear filtering half a
    // texel inside the selected face so a moving ray cannot borrow a star from another cell.
    vec2 atlasSize = vec2(textureSize(Sampler0, 0));
    vec2 cellSize = atlasSize / vec2(3.0, 2.0);
    vec2 texelInset = 0.5 / cellSize;
    localUv = clamp(localUv, texelInset, vec2(1.0) - texelInset);
    return (cell + localUv) / vec2(3.0, 2.0);
}

vec2 ntmAtlasUvForAxis(vec3 ray, int axis) {
    vec3 magnitude = abs(ray);
    vec2 localUv;
    vec2 cell;
    if (axis == 0) {
        if (ray.x < 0.0) {
            localUv = vec2((-ray.z / magnitude.x + 1.0) * 0.5,
                           (1.0 - ray.y / magnitude.x) * 0.5);
            cell = vec2(1.0, 1.0);
        } else {
            localUv = vec2((ray.z / magnitude.x + 1.0) * 0.5,
                           (1.0 - ray.y / magnitude.x) * 0.5);
            cell = vec2(2.0, 0.0);
        }
    } else if (axis == 1) {
        localUv = vec2((1.0 - ray.z / magnitude.y) * 0.5,
                       ((ray.y > 0.0 ? -ray.x : ray.x) / magnitude.y + 1.0) * 0.5);
        cell = ray.y > 0.0 ? vec2(1.0, 0.0) : vec2(0.0, 0.0);
    } else {
        localUv = vec2(((ray.z < 0.0 ? ray.x : -ray.x) / magnitude.z + 1.0) * 0.5,
                       (1.0 - ray.y / magnitude.z) * 0.5);
        cell = ray.z < 0.0 ? vec2(2.0, 1.0) : vec2(0.0, 1.0);
    }
    vec2 atlasSize = vec2(textureSize(Sampler0, 0));
    vec2 cellSize = atlasSize / vec2(3.0, 2.0);
    vec2 texelInset = 0.5 / cellSize;
    localUv = clamp(localUv, texelInset, vec2(1.0) - texelInset);
    return (cell + localUv) / vec2(3.0, 2.0);
}

vec4 sampleNtmAtlasSeamless(vec3 direction) {
    vec3 ray = normalize(direction);
    vec3 magnitude = abs(ray);
    const float seamWidth = 0.018;
    vec3 weights = vec3(
        1.0 - smoothstep(0.0, seamWidth, max(magnitude.y, magnitude.z) - magnitude.x),
        1.0 - smoothstep(0.0, seamWidth, max(magnitude.x, magnitude.z) - magnitude.y),
        1.0 - smoothstep(0.0, seamWidth, max(magnitude.x, magnitude.y) - magnitude.z));
    weights /= max(1.0e-6, weights.x + weights.y + weights.z);
    vec4 sampled = vec4(0.0);
    if (weights.x > 0.0) sampled += texture(Sampler0, ntmAtlasUvForAxis(ray, 0)) * weights.x;
    if (weights.y > 0.0) sampled += texture(Sampler0, ntmAtlasUvForAxis(ray, 1)) * weights.y;
    if (weights.z > 0.0) sampled += texture(Sampler0, ntmAtlasUvForAxis(ray, 2)) * weights.z;
    return sampled;
}

vec3 inverseAberrationSource(vec3 observed, vec3 velocity, float beta) {
    float gamma = inversesqrt(max(1.0e-6, 1.0 - beta * beta));
    float observedCosine = dot(observed, velocity);
    return normalize(observed + velocity * ((gamma - 1.0) * observedCosine - gamma * beta));
}

void main() {
    vec3 observed = normalize(observedDirection);
    vec3 velocity = normalize(Velocity);
    float visualBeta = clamp(AberrationBeta, 0.0, 0.94);
    float observedCosine = dot(observed, velocity);

    // Inverse source-direction aberration: find which stationary atlas ray arrives at the current
    // rigid sky direction.  Only texture lookup moves; gl_Position never does.
    vec3 source = inverseAberrationSource(observed, velocity, visualBeta);

    float beta = clamp(Beta, 0.0, 0.999999);
    float gamma = inversesqrt(max(1.0e-12, 1.0 - beta * beta));
    // Colour/exposure is radial around the observed forward centre.  The cubemap source ray only
    // selects the sliding star texture and cannot introduce a face-dependent brightness boundary.
    float doppler = 1.0 / max(1.0e-6, gamma * (1.0 - beta * observedCosine));
    vec4 sampled = sampleNtmAtlasSeamless(source);
    float trailMagnitude = abs(StarTrailStrength);
    // The uniform branch is zero throughout cruise/arrival, so their ordinary frames pay no extra
    // atlas samples. A fixed seamless integration makes one stable band without a history buffer,
    // dynamic sample count, or cubemap-face discontinuity.
    if (trailMagnitude > 0.0) {
        // Calculate the two ends once, then cover the complete spherical lookup arc uniformly.
        // Length changes only lookup position: sample count and point-return contract stay fixed.
        float trailExtent = 0.165 * StarTrailStrength;
        float tailBeta = clamp(visualBeta - trailExtent, 0.0, 0.94);
        vec3 tailSource = inverseAberrationSource(observed, velocity, tailBeta);
        vec4 trailResidual = vec4(0.0);
        for (int trailIndex = 1; trailIndex <= STAR_TRAIL_SAMPLES; ++trailIndex) {
            float trailFraction = float(trailIndex) / float(STAR_TRAIL_SAMPLES);
            vec3 trailSource = normalize(mix(source, tailSource, trailFraction));
            vec4 trailSample = sampleNtmAtlasSeamless(trailSource);
            // Smoothly reaches zero at the remote end, so it reads as a fading band rather than
            // a second star. Per-channel maximum prevents overlapping taps from making hot beads.
            float tailFade = 1.0 - smoothstep(0.0, 1.0, trailFraction);
            vec4 residual = max(trailSample - sampled, vec4(0.0)) * tailFade;
            trailResidual = max(trailResidual, residual);
        }
        sampled.rgb += trailResidual.rgb * 0.68;
        sampled.a = max(sampled.a, trailResidual.a * 0.78);
    }
    sampled *= ColorModulator;

    // Strong but bounded SDR proxy. Doppler remains the attachment-equivalent physical k.
    // Rear shoulders remain visible, but the exact rear has no artificial brightness floor.
    float spectral = clamp(log(max(1.0e-12, doppler)) / log(4.0), -1.0, 1.0);
    float blue = max(0.0, spectral);
    float red = max(0.0, -spectral);
    float exposure = clamp(log(max(1.0e-12, doppler)) / log(2.0) / 10.0, -1.0, 1.0);
    float brightness = doppler >= 1.0
        ? 1.0 + 2.5 * exposure
        : pow(clamp(doppler, 0.0, 1.0), 1.65);
    float forwardCone = smoothstep(0.30, 1.0, observedCosine);
    brightness *= 1.0 + 1.8 * forwardCone * forwardCone
        * clamp(visualBeta / 0.94, 0.0, 1.0);
    vec3 tint = mix(vec3(1.0), vec3(0.30, 0.65, 1.80), blue);
    tint = mix(tint, vec3(1.80, 0.42, 0.18), red);
    fragColor = vec4(sampled.rgb * tint * brightness, sampled.a);
}
