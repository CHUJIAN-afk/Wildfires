#version 150

in vec3 Position;
in vec2 UV0;

out vec3 observedDirection;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

void main() {
    // Keep the cube itself rigid.  Relativistic motion belongs to the atlas lookup in the
    // fragment shader, so stars slide across cube faces without folding the sky geometry.
    observedDirection = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
