#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// One triangle that covers the screen; each pixel gets the direction it looks in.
in vec3 Position;

out vec3 viewDir;

void main() {
    vec4 view = inverse(ProjMat) * vec4(Position.xy, 0.0, 1.0);
    viewDir = inverse(mat3(ModelViewMat)) * (view.xyz / view.w);
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
