/* Waterfall-derived; SPDX-License-Identifier: CC-BY-NC-SA-4.0 */
#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 ModelScale;
uniform vec3 PlumeDirection;
uniform float ExpandOffset;
uniform float ExpandLinear;
uniform float ExpandSquare;
uniform float ExpandBounded;

out vec2 texCoord0;
out vec4 vertexColor;
out vec3 viewDirection;
out vec3 viewNormal;
out vec3 plumeDirection;
out float plumePos;

float bounded(float x) { return 1.0 - exp(-3.0 * x); }
float boundedDeriv(float x) { return 3.0 * exp(-3.0 * x); }

void main() {
    float arg = -dot(Position, PlumeDirection);
    float value = ExpandOffset + ExpandLinear * arg + ExpandSquare * arg * arg + ExpandBounded * bounded(arg);
    vec3 displaced = Position + Normal * value;
    float deriv = ExpandLinear + ExpandSquare * 2.0 * arg + ExpandBounded * boundedDeriv(arg);
    vec3 recalculatedNormal = normalize(Normal + deriv * PlumeDirection);
    vec3 localPosition = displaced * ModelScale;
    vec4 viewPosition = ModelViewMat * vec4(localPosition, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    viewDirection = normalize(-viewPosition.xyz);
    viewNormal = normalize(mat3(ModelViewMat) * normalize(recalculatedNormal / ModelScale));
    plumeDirection = normalize(mat3(ModelViewMat) * (PlumeDirection * ModelScale));
    plumePos = arg;
    gl_Position = ProjMat * viewPosition;
}
