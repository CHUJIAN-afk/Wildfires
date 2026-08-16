/* Waterfall-derived; SPDX-License-Identifier: CC-BY-NC-SA-4.0 */
#version 150

uniform sampler2D Sampler0;
uniform vec4 StartTint;

in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec4 col = texture(Sampler0, texCoord0);
    if (col.a <= 0.01) discard;
    fragColor = clamp(col * StartTint * 2.0, 0.0, 50.0);
}
