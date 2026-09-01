#version 150

in vec3 Position;
in vec4 Color;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;

out vec4 vertexColor;
out vec3 worldPos;
out vec3 viewPos;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    vertexColor = Color;
    worldPos = Position;
    viewPos = (ModelViewMat * vec4(Position, 1.0)).xyz;
}
