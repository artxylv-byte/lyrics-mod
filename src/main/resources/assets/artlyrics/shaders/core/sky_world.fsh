#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <artlyrics:sky.glsl>

layout(std140) uniform ArtlyricsSky {
    // x: seconds, y: sky id, z: strength, w: stars (0/1)
    vec4 SkyParams;
    // x: how much daylight there is (0..1), y: detail (0..2)
    vec4 SkyParams2;
};

in vec3 viewDir;

out vec4 fragColor;

void main() {
    vec3 d = normalize(viewDir);
    float px = max(length(dFdx(d)), length(dFdy(d)));
    art_starsOn = SkyParams.w;
    vec4 sky = artlyrics_sky(d, int(SkyParams.y + 0.5), SkyParams.x, px, int(SkyParams2.y + 0.5), gl_FragCoord.xy);
    // Meet Minecraft's horizon (and the fog over distant land) smoothly, wider in daylight.
    float top = mix(0.06, 0.15, SkyParams2.x);
    float h = smoothstep(-0.03, top, d.y);
    vec3 color = mix(FogColor.rgb, sky.rgb, h);
    float alpha = sky.a * SkyParams.z * smoothstep(-0.12, -0.02, d.y);
    fragColor = vec4(color, alpha);
}
