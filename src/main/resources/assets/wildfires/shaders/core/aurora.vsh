#version 150

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 Resolution;
uniform float NodeWidth;
uniform float WavePhase;
uniform float Pole;

out vec2 texCoord0;

void main() {
    texCoord0 = UV0;
    float nodeIndex = UV0.x * Resolution.x / max(NodeWidth, 0.0001);
    vec3 animated = Position;
    float wave = cos(radians(nodeIndex * 8.0 + WavePhase));
    animated.z += Pole * wave * 6.0;
    animated.y += step(0.25, UV0.y) * wave * 1.5;
    gl_Position = ProjMat * ModelViewMat * vec4(animated, 1.0);
}
