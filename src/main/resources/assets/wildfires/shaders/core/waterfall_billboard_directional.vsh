/* Adapted from Waterfall Billboard Directional; SPDX-License-Identifier: CC-BY-NC-SA-4.0 */
#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Scale;
uniform vec3 CameraPosition;
uniform vec3 Direction;
uniform float DirectionScale;

out vec2 texCoord0;
out float directionalFactor;

void main() {
    vec4 viewPos = ModelViewMat * vec4(0.0, 0.0, 0.0, 1.0);
    viewPos.xy += Position.xy * Scale.xy;
    gl_Position = ProjMat * viewPos;
    vec3 objectCamera = CameraPosition / Scale;
    vec3 viewDelta = objectCamera - Position;
    vec3 viewDirection = viewDelta * inversesqrt(max(dot(viewDelta, viewDelta), 1.0e-12));
    float facing = clamp(dot(Direction, viewDirection), 0.0, 1.0);
    directionalFactor = DirectionScale == 0.0 ? 1.0 : pow(facing, DirectionScale);
    texCoord0 = UV0;
}
