#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <artlyrics:sky.glsl>

// A live preview of a sky in the menu. The vertex color carries the sky (red), the time in twentieths of a
// second (green and blue) and the opacity (alpha); UV runs 0..1 across the tile.
uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    int sky = int(vertexColor.r * 255.0 + 0.5);
    float t = (floor(vertexColor.g * 255.0 + 0.5) * 256.0 + floor(vertexColor.b * 255.0 + 0.5)) / 20.0;
    vec2 uv = texCoord0;
    float aspect = abs(dFdy(uv.y)) / max(abs(dFdx(uv.x)), 1e-6);
    vec3 d = art_previewDir(sky, uv, aspect);
    float px = max(length(dFdx(d)), length(dFdy(d)));
    vec3 color = sky == 0 ? art_vanillaPreview(d) : artlyrics_sky(d, sky, t, px, 1, gl_FragCoord.xy).rgb;
    float hills = art_silhouette(uv, aspect, float(sky));
    color = mix(color, vec3(0.03, 0.03, 0.045), hills);
    float keep = texture(Sampler0, vec2(0.5)).a;
    fragColor = vec4(color, vertexColor.a * keep) * ColorModulator;
}
