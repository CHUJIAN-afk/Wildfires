/* Adapted from Waterfall Billboard Directional; SPDX-License-Identifier: CC-BY-NC-SA-4.0 */
#version 150

uniform sampler2D Sampler0;
uniform vec4 StartTint;
uniform float Brightness;

in vec2 texCoord0;
in float directionalFactor;
out vec4 fragColor;

void main() {
    vec4 col = texture(Sampler0, texCoord0);
    if (col.a <= 0.01) discard;
    fragColor = clamp(col * StartTint * directionalFactor * Brightness, 0.0, 50.0);
}
