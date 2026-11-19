#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

// One triangle that covers the screen: where on the lyrics layer each pixel is, and which way it looks.
in vec3 Position;

out vec3 viewDir;
out vec2 screenUv;

void main() {
    vec4 view = inverse(ProjMat) * vec4(Position.xy, 0.0, 1.0);
    viewDir = inverse(mat3(ModelViewMat)) * (view.xyz / view.w);
    screenUv = Position.xy * 0.5 + 0.5;
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
