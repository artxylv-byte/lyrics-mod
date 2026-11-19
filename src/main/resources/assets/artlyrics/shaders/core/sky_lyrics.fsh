#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <artlyrics:sky.glsl>

// Puts the lyrics into the sky. The words were drawn into their own layer (Sampler0, premultiplied); here they
// get light around them, soft edges and a little shimmer, fade into the haze near the horizon, and are laid
// into the sky before the land and clouds are drawn, so those pass in front of them.
uniform sampler2D Sampler0;

layout(std140) uniform ArtlyricsLyrics {
    // x: seconds, y: look (0 starlight, 1 smoke, 2 neon, 3 clean), z: glow strength, w: daylight (0..1)
    vec4 LyricParams;
    // x: radians one font pixel covers
    vec4 LyricParams2;
};

in vec3 viewDir;
in vec2 screenUv;

out vec4 fragColor;

vec4 ringAvg(vec2 uv, vec2 texel, float r, float rot) {
    vec4 s = vec4(0.0);
    for (int i = 0; i < 8; i++) {
        float a = rot + float(i) * 0.7853982;
        s += texture(Sampler0, uv + vec2(cos(a), sin(a)) * r * texel);
    }
    return s * 0.125;
}

void main() {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 texel = 1.0 / size;
    // Screen pixels per font pixel, so the light scales with the letters.
    float fp = clamp(LyricParams2.x * size.y * ProjMat[1][1] * 0.5, 0.25, 40.0);
    float t = LyricParams.x;
    int look = int(LyricParams.y + 0.5);
    float glow = LyricParams.z;
    float day = LyricParams.w;
    vec2 uv = screenUv;

    // Most of the screen has no words near it: skip it quickly.
    vec4 probe = texture(Sampler0, uv) + ringAvg(uv, texel, 5.0 * fp, 0.2) + ringAvg(uv, texel, 10.0 * fp, 0.6);
    if (probe.a < 0.0015) {
        discard;
    }

    // The air shimmers a little.
    vec2 q = gl_FragCoord.xy / (fp * 7.0);
    vec2 shimmer = (vec2(art_noise2(q + vec2(t * 0.55, -t * 0.35)), art_noise2(q.yx - vec2(t * 0.45, t * 0.3))) - 0.5) * 0.45 * fp * texel;
    vec2 suv = uv + (look == 1 ? vec2(0.0) : shimmer);

    vec4 core = texture(Sampler0, suv) * 0.55 + ringAvg(suv, texel, 0.55 * fp, 0.0) * 0.45;
    vec4 g1 = ringAvg(suv, texel, 2.0 * fp, 0.2);
    vec4 g2 = (ringAvg(suv, texel, 4.5 * fp, 0.6) + ringAvg(suv, texel, 4.5 * fp, 0.99)) * 0.5;
    vec4 g3 = ringAvg(suv, texel, 9.0 * fp, 0.1);
    vec3 tint = core.a > 0.02 ? core.rgb / core.a : (g1.a > 0.005 ? g1.rgb / g1.a : (g2.a > 0.002 ? g2.rgb / g2.a : vec3(1.0)));

    vec3 rgb;
    float a;
    if (look == 1) {
        // Smoke: puffy trails like an aeroplane's skywriting, drifting and curling in the wind.
        vec2 p = gl_FragCoord.xy / fp;
        vec2 wind = vec2(t * 0.5, t * 0.2);
        vec2 off = (vec2(art_fbm2(p * 0.11 + wind * 0.05, 3), art_fbm2(p * 0.11 + 7.3 - wind * 0.04, 3)) - 0.5) * 2.8 * fp * texel;
        vec2 wuv = uv + off;
        float a0 = texture(Sampler0, wuv).a;
        float a1 = ringAvg(wuv, texel, 1.6 * fp, 0.3).a;
        float a2 = ringAvg(wuv, texel, 3.4 * fp, 0.8).a;
        float body = a0 * 0.4 + a1 * 0.45 + a2 * 0.4;
        float puff = art_fbm2(p * 0.32 + wind * 0.1, 4);
        float dens = smoothstep(0.1, 0.55, body * (0.5 + 0.95 * puff));
        float above = ringAvg(wuv + vec2(0.0, 1.8 * fp) * texel, texel, 2.0 * fp, 0.0).a;
        float lit = art_sat(0.62 + (body - above) * 2.2);
        vec3 smokeDay = mix(vec3(0.70, 0.72, 0.78), vec3(1.0, 0.99, 0.97), lit);
        vec3 smokeNight = mix(tint * 0.45, tint * 1.05, lit);
        vec3 c = mix(smokeNight, mix(smokeDay, tint, 0.15), day);
        a = dens * 0.92;
        rgb = c * a + g2.rgb * glow * 0.35 * (1.0 - day);
    } else if (look == 2) {
        // Neon: a white-hot tube of light in the letter's color, buzzing faintly.
        float flick = 0.95 + 0.05 * sin(t * 37.0) * sin(t * 23.0);
        vec3 halo = g1.rgb * 0.7 + g2.rgb * 0.55 + g3.rgb * 0.4;
        // The tube: the letter's color, whiter in the middle of each stroke.
        float inner = smoothstep(0.55, 1.0, core.a);
        vec3 tube = mix(tint * 1.05, vec3(1.0), inner * 0.45) * core.a;
        rgb = tube * flick + halo * glow * mix(1.4, 0.7, day) * flick;
        a = core.a * 0.97 + g2.a * 0.22 * day;
    } else if (look == 3) {
        // Clean: just the words, softened, with a faint light around them.
        rgb = core.rgb + (g1.rgb * 0.35 + g2.rgb * 0.2) * glow;
        a = core.a;
    } else {
        // Starlight: letters made of light, glowing into the sky, with specks that twinkle.
        vec3 halo = g1.rgb * 0.55 + g2.rgb * 0.42 + g3.rgb * 0.3;
        vec2 cell = floor(gl_FragCoord.xy / max(2.0, fp * 0.9));
        float h = art_hash12(cell);
        float twinkle = pow(max(sin(t * (2.0 + h * 3.0) + h * 40.0), 0.0), 16.0) * step(0.8, h);
        // By day the light around the letters is lost in the bright sky, so they get deeper and a soft shade.
        rgb = core.rgb * mix(1.08, 0.82, day) + vec3(1.0, 0.97, 0.9) * twinkle * core.a * 0.85 + halo * glow * mix(1.7, 0.45, day);
        a = core.a * 0.95 + g2.a * 0.28 * day;
    }

    // Far down near the horizon the words sink into the haze, like anything in the sky.
    vec3 d = normalize(viewDir);
    float horizon = smoothstep(-0.05, 0.06, d.y);
    float haze = (1.0 - smoothstep(0.0, 0.32, d.y)) * 0.45;
    rgb = mix(rgb, FogColor.rgb * a, haze);
    rgb *= horizon;
    a *= horizon;
    rgb += vec3((art_hash12(gl_FragCoord.xy + 3.0) - 0.5) / 255.0) * a;
    fragColor = vec4(clamp(rgb, 0.0, 1.0), clamp(a, 0.0, 1.0));
}
