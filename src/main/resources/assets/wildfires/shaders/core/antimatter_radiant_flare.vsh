/* Waterfall-derived; SPDX-License-Identifier: CC-BY-NC-SA-4.0 */
#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 Scale;

out vec2 texCoord0;

void main() {
    vec4 viewPos = ModelViewMat * vec4(0.0, 0.0, 0.0, 1.0);
    viewPos.xy += Position.xy * Scale;
    gl_Position = ProjMat * viewPos;
    texCoord0 = UV0;
}
