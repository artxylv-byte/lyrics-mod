// Artlyrics skies: every sky is worked out per pixel on the GPU from the viewing direction.
// Shared by the world shader (sky_world) and the menu previews (sky_gui).
//
// Directions use Minecraft's axes: +y up, yaw 0 = south (+z), yaw 90 = west (-x), yaw 180 = north (-z).
// Ids follow com.artlyrics.sky.SkyTheme.

#define ART_PI 3.14159265
#define ART_TAU 6.28318531

float art_jitter = 0.0;
float art_starsOn = 1.0;

// ---------------------------------------------------------------------------------------------- helpers

float art_sat(float x) {
    return clamp(x, 0.0, 1.0);
}

float art_hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

float art_hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float art_hash13(vec3 p3) {
    p3 = fract(p3 * 0.1031);
    p3 += dot(p3, p3.zyx + 31.32);
    return fract((p3.x + p3.y) * p3.z);
}

vec2 art_hash22(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.xx + p3.yz) * p3.zy);
}

vec3 art_hash32(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yxz + 33.33);
    return fract((p3.xxy + p3.yzz) * p3.zyx);
}

float art_noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = art_hash12(i);
    float b = art_hash12(i + vec2(1.0, 0.0));
    float c = art_hash12(i + vec2(0.0, 1.0));
    float d = art_hash12(i + vec2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float art_noise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float n000 = art_hash13(i);
    float n100 = art_hash13(i + vec3(1.0, 0.0, 0.0));
    float n010 = art_hash13(i + vec3(0.0, 1.0, 0.0));
    float n110 = art_hash13(i + vec3(1.0, 1.0, 0.0));
    float n001 = art_hash13(i + vec3(0.0, 0.0, 1.0));
    float n101 = art_hash13(i + vec3(1.0, 0.0, 1.0));
    float n011 = art_hash13(i + vec3(0.0, 1.0, 1.0));
    float n111 = art_hash13(i + vec3(1.0, 1.0, 1.0));
    return mix(mix(mix(n000, n100, u.x), mix(n010, n110, u.x), u.y),
               mix(mix(n001, n101, u.x), mix(n011, n111, u.x), u.y), u.z);
}

float art_fbm2(vec2 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    float norm = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= octaves) {
            break;
        }
        sum += amp * art_noise2(p);
        norm += amp;
        p = mat2(1.6, 1.2, -1.2, 1.6) * p + vec2(17.1, 9.2);
        amp *= 0.5;
    }
    return sum / norm;
}

float art_fbm3(vec3 p, int octaves) {
    const mat3 rot = mat3(0.00, 0.80, 0.60, -0.80, 0.36, -0.48, -0.60, -0.48, 0.64);
    float sum = 0.0;
    float amp = 0.5;
    float norm = 0.0;
    for (int i = 0; i < 8; i++) {
        if (i >= octaves) {
            break;
        }
        sum += amp * art_noise3(p);
        norm += amp;
        p = rot * p * 2.03 + vec3(3.1, 1.7, 5.3);
        amp *= 0.5;
    }
    return sum / norm;
}

vec2 art_rot(vec2 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}

vec3 art_rotAxis(vec3 v, vec3 k, float a) {
    float c = cos(a);
    float s = sin(a);
    return v * c + cross(k, v) * s + k * dot(k, v) * (1.0 - c);
}

// Direction for a yaw and elevation in degrees (the same as SkyGeometry.direction in Java).
vec3 art_dir(float yawDeg, float elevDeg) {
    float y = radians(yawDeg);
    float e = radians(elevDeg);
    return vec3(-sin(y) * cos(e), sin(e), cos(y) * cos(e));
}

float art_yaw(vec3 d) {
    return atan(-d.x, d.z);
}

float art_elev(vec3 d) {
    return asin(clamp(d.y, -1.0, 1.0));
}

// Coordinates of d on a flat picture held up at direction c: x to the right, y up (as a viewer facing c sees
// it), in radians near the middle; z is the cosine of the angle from c (negative = behind).
vec3 art_local(vec3 d, vec3 c) {
    vec3 r = normalize(cross(c, vec3(0.0, 1.0, 0.0)));
    vec3 u = cross(r, c);
    float z = dot(d, c);
    if (z <= 0.0) {
        return vec3(1e4, 1e4, z);
    }
    return vec3(dot(d, r) / z, dot(d, u) / z, z);
}

// A sky gradient from the horizon up; below the horizon it darkens.
vec3 art_gradient(float e, vec3 horizon, vec3 middle, vec3 zenith) {
    if (e < 0.0) {
        return mix(horizon * 0.55, horizon, smoothstep(-0.35, 0.0, e));
    }
    vec3 c = mix(horizon, middle, smoothstep(0.0, 0.38, e));
    return mix(c, zenith, smoothstep(0.28, 1.0, e));
}

// Bright light turns white and highlights roll off softly instead of clipping.
vec3 art_finish(vec3 c) {
    c = max(c, vec3(0.0));
    float m = max(c.r, max(c.g, c.b));
    c += vec3(max(m - 1.0, 0.0) * 0.35);
    vec3 knee = vec3(0.8);
    vec3 hi = knee + (1.0 - knee) * (1.0 - exp(-(c - knee) / (1.0 - knee)));
    return mix(c, hi, step(knee, c));
}

// ---------------------------------------------------------------------------------------------- stars

vec3 art_starTint(float h) {
    vec3 c = mix(vec3(0.66, 0.78, 1.0), vec3(1.0, 0.98, 0.94), smoothstep(0.0, 0.45, h));
    return mix(c, vec3(1.0, 0.80, 0.58), smoothstep(0.78, 1.0, h));
}

// One layer of stars: at most one star per cell of a cube map around the viewer. Stars stay round and
// sharp at any resolution: smaller than a pixel they get fainter instead of smaller.
vec3 art_starLayer(vec3 d, float scale, float density, float px, float t, float bright, float seed) {
    vec3 a = abs(d);
    vec2 uv;
    float m;
    float face;
    if (a.x >= a.y && a.x >= a.z) {
        m = a.x;
        uv = d.yz / m;
        face = d.x > 0.0 ? 1.0 : 2.0;
    } else if (a.y >= a.z) {
        m = a.y;
        uv = d.xz / m;
        face = d.y > 0.0 ? 3.0 : 4.0;
    } else {
        m = a.z;
        uv = d.xy / m;
        face = d.z > 0.0 ? 5.0 : 6.0;
    }
    vec2 p = uv * scale;
    vec2 cell = floor(p);
    vec2 key = cell + vec2(face * 517.0 + seed * 91.0, face * 263.0 - seed * 37.0);
    vec3 h = art_hash32(key);
    if (h.x > density) {
        return vec3(0.0);
    }
    vec2 sp = cell + 0.25 + 0.5 * art_hash22(key + 19.19);
    float dist = length(p - sp);
    float pixel = px * scale / (m * sqrt(m));
    float core = mix(0.016, 0.045, h.y * h.y);
    float r = max(core, pixel * 0.62);
    float g = exp(-dist * dist / (r * r)) * (core / r);
    float mag = bright * (0.2 + 0.8 * pow(h.y, 3.0));
    float twinkle = 0.78 + 0.22 * sin(t * (1.1 + 3.4 * h.z) + h.x * 97.0);
    float halo = exp(-dist / (r * 3.5)) * 0.05 * smoothstep(0.7, 1.0, h.y) * bright;
    return art_starTint(h.z) * (g * mag * twinkle + halo);
}

// The whole star field, turning very slowly around the pole star.
vec3 art_stars(vec3 d, float px, float t, float amount, int detail) {
    vec3 r = art_rotAxis(d, normalize(vec3(0.0, 0.72, -0.69)), t * 0.0016);
    vec3 c = art_starLayer(r, 38.0, 0.26, px, t, 1.0, 2.0);
    c += art_starLayer(r, 13.0, 0.17, px, t, 1.9, 3.0);
    if (detail > 0) {
        c += art_starLayer(r, 95.0, 0.34, px, t, 0.55, 1.0);
    }
    return c * amount * art_starsOn * smoothstep(-0.04, 0.2, d.y);
}

// A band of the galaxy across the sky. n: the band's axis; core: where it is brightest (in the band).
// Returns the light and, in w, how close d is to the band (for extra stars).
vec4 art_band(vec3 d, vec3 n, vec3 core, int detail, float t) {
    float b = dot(d, n);
    vec3 flat0 = d - n * b;
    float len = length(flat0);
    float toCore = acos(clamp(dot(flat0 / max(len, 1e-4), core), -1.0, 1.0));
    float coreness = exp(-toCore * toCore / 0.45);
    float width = 0.11 + 0.11 * coreness;
    float band = exp(-b * b / (width * width));
    if (band < 0.002) {
        return vec4(0.0);
    }
    int oct = detail >= 2 ? 6 : (detail == 1 ? 5 : 3);
    vec3 q = d + vec3(0.0, 0.0, t * 0.0004);
    float clouds = art_fbm3(q * 4.5, oct);
    float grain = art_fbm3(q * 19.0 + 3.1, max(oct - 2, 2));
    float dust = smoothstep(0.46, 0.74, art_fbm3(q * 8.5 + vec3(7.7, 1.3, 4.2), oct));
    float laneWave = 0.012 * sin(toCore * 6.0) + (art_fbm3(q * 3.0 + 9.0, 3) - 0.5) * 0.05;
    float lane = exp(-pow((b - laneWave) / (0.025 + 0.035 * coreness), 2.0));
    float glow = band * (0.3 + 1.2 * clouds * clouds) * (0.7 + 0.6 * grain);
    float dark = 1.0 - 0.75 * dust * exp(-b * b / 0.012) - 0.6 * lane * (0.3 + 0.7 * coreness);
    vec3 tint = mix(vec3(0.58, 0.66, 0.95), vec3(1.0, 0.84, 0.62), coreness);
    vec3 col = tint * glow * max(dark, 0.06) * (0.14 + 0.55 * coreness);
    col += vec3(0.75, 0.2, 0.3) * band * smoothstep(0.64, 0.82, art_fbm3(q * 13.0 + 11.0, 3)) * 0.1;
    return vec4(col, band);
}

// Soft clouds on a flat layer above the viewer, lit from the side of `sun`.
vec3 art_clouds(vec3 d, float t, vec3 col, vec3 lit, vec3 shade, vec3 sun, int detail, float cover, float alpha) {
    if (d.y < 0.012) {
        return col;
    }
    vec2 q = d.xz / (d.y + 0.06) * 0.8 + vec2(t * 0.012, t * 0.005);
    int oct = detail >= 2 ? 6 : (detail == 1 ? 5 : 3);
    float n = art_fbm2(q, oct);
    float dens = smoothstep(1.0 - cover, 1.0 - cover + 0.32, n);
    if (dens < 0.001) {
        return col;
    }
    vec2 towardSun = normalize(sun.xz + vec2(1e-4)) * 0.09;
    float n2 = art_fbm2(q + towardSun, oct);
    float light = art_sat(0.5 + (n - n2) * 5.0);
    float edge = 1.0 - smoothstep(0.0, 0.7, dens);
    vec3 cc = mix(shade, lit, light) + lit * edge * 0.25;
    return mix(col, cc, dens * smoothstep(0.012, 0.2, d.y) * alpha);
}

// ---------------------------------------------------------------------------------------------- Milky Way

vec3 art_skyMilkyWay(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(0.05, 0.065, 0.10), vec3(0.02, 0.028, 0.058), vec3(0.008, 0.01, 0.028));
    col += vec3(0.03, 0.07, 0.04) * exp(-max(d.y, 0.0) * 9.0) * smoothstep(-0.05, 0.02, d.y); // airglow
    vec3 core = art_dir(160.0, 26.0);
    vec3 cr = normalize(cross(core, vec3(0.0, 1.0, 0.0)));
    vec3 cu = cross(cr, core);
    vec3 along = normalize(cr * 0.55 + cu * 0.84);
    vec3 n = normalize(cross(core, along));
    vec4 band = art_band(d, n, core, detail, t);
    col += band.rgb * 2.0;
    col += art_stars(d, px, t, 1.0 + band.a * 1.5, detail);
    col += art_starLayer(d, 70.0, 0.5 * band.a, px, t, 0.9, 6.0);
    return col;
}

// ---------------------------------------------------------------------------------------------- ringed planet

float art_ringDensity(float r) {
    if (r < 1.3 || r > 2.36) {
        return 0.0;
    }
    float d = smoothstep(1.3, 1.42, r) * (1.0 - smoothstep(2.24, 2.36, r));
    d *= 0.55 + 0.45 * art_noise2(vec2(r * 52.0, 3.0));
    d *= 0.75 + 0.25 * art_noise2(vec2(r * 160.0, 7.0));
    d *= 1.0 - 0.92 * exp(-pow((r - 1.95) / 0.028, 2.0));
    d *= 1.0 - 0.45 * exp(-pow((r - 2.12) / 0.012, 2.0));
    d *= mix(0.35, 1.0, smoothstep(1.42, 1.62, r));
    return d;
}

vec3 art_planetBands(vec3 np, vec3 axis, float t) {
    float lat = dot(np, axis);
    float warp = art_fbm3(np * 3.2 + vec3(0.0, 0.0, t * 0.004), 4);
    float b = lat * 7.0 + (warp - 0.5) * 0.85;
    float f = fract(b);
    vec3 cream = vec3(0.93, 0.85, 0.67);
    vec3 tan0 = vec3(0.80, 0.62, 0.42);
    vec3 brown = vec3(0.55, 0.38, 0.25);
    vec3 c = mix(cream, tan0, smoothstep(0.15, 0.45, f));
    c = mix(c, brown, smoothstep(0.62, 0.86, f) * (0.5 + 0.5 * sin(floor(b) * 2.3)));
    c *= 0.9 + 0.2 * art_noise3(vec3(lat * 70.0, np.x * 3.0, np.z * 3.0));
    float pole = smoothstep(0.72, 0.95, abs(lat));
    return mix(c, vec3(0.62, 0.66, 0.70), pole * 0.7);
}

vec3 art_skyPlanet(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(0.12, 0.09, 0.24), vec3(0.045, 0.036, 0.12), vec3(0.014, 0.012, 0.045));
    col += art_stars(d, px, t, 0.9, detail);
    vec3 n = normalize(vec3(-0.35, 0.62, 0.70));
    col += art_band(d, n, normalize(cross(n, vec3(1.0, 0.0, 0.0))), detail, t).rgb * 0.45;
    vec3 C = art_dir(160.0, 34.0);
    vec3 L = art_local(d, C);
    if (L.z <= 0.0) {
        return col;
    }
    float R = 0.15;
    vec2 q = art_rot(L.xy / R, 0.36);
    float pxr = px / R;
    float r2 = dot(q, q);
    vec3 light = normalize(vec3(-0.78, 0.30, 0.55));
    vec3 rn = normalize(vec3(0.0, 1.0, 0.30));

    // A glow of atmosphere around the lit edge.
    float rr0 = sqrt(r2);
    float rimSide = art_sat(dot(normalize(vec3(q, 0.001)), light) * 0.6 + 0.5);
    col += vec3(0.95, 0.82, 0.62) * 0.13 * exp(-max(rr0 - 1.0, 0.0) * 7.0) * step(1.0, rr0) * rimSide;

    // Rings: where the viewing ray crosses the ring plane.
    float s = (q.x * rn.x + q.y * rn.y + 6.0 * rn.z) / rn.z;
    vec3 hp = vec3(q, 6.0 - s);
    float rr = length(hp);
    float ringA = art_ringDensity(rr) * 0.92;
    float shadowOnRing = 1.0;
    float sl = -dot(hp, light);
    if (sl > 0.0 && length(hp + light * sl) < 1.0) {
        shadowOnRing = 0.1;
    }
    vec3 ringCol = vec3(0.88, 0.80, 0.66) * (0.75 + 0.25 * art_noise2(vec2(rr * 90.0, 1.0)));
    ringCol *= (0.3 + 0.7 * abs(dot(rn, light))) * shadowOnRing;

    // The planet: a lit sphere with drifting bands.
    float pz = r2 < 1.0 ? sqrt(1.0 - r2) : -1e3;
    vec3 pc = vec3(0.0);
    float pa = 1.0 - smoothstep(1.0 - pxr * 1.5, 1.0 + pxr * 0.5, rr0);
    if (r2 < 1.0) {
        vec3 nrm = vec3(q, pz);
        vec3 np = art_rotAxis(nrm, rn, t * 0.02);
        vec3 albedo = art_planetBands(np, rn, t);
        float lambert = dot(nrm, light);
        float lit = smoothstep(-0.12, 0.35, lambert) * (0.3 + 0.7 * max(lambert, 0.0));
        float s2 = -dot(nrm, rn) / dot(light, rn);
        if (s2 > 0.0) {
            vec3 pt = nrm + light * s2;
            lit *= 1.0 - 0.8 * art_ringDensity(length(pt));
        }
        float limb = pow(pz, 0.28);
        pc = albedo * lit * limb * 1.15;
        pc += vec3(1.0, 0.85, 0.6) * pow(1.0 - pz, 3.0) * art_sat(lambert + 0.2) * 0.35;
        pc += albedo * 0.025; // faint light from the rings on the night side
    }
    if (hp.z < pz) {
        col = mix(col, ringCol, ringA);
        col = mix(col, pc, pa);
    } else {
        col = mix(col, pc, pa);
        col = mix(col, ringCol, ringA);
    }

    // A small moon nearby.
    vec2 mq = (L.xy / R - vec2(2.9, 1.25)) / 0.11;
    float mr2 = dot(mq, mq);
    if (mr2 < 1.6) {
        float ma = 1.0 - smoothstep(1.0 - pxr * 12.0, 1.0 + pxr * 4.0, sqrt(mr2));
        vec3 mn = vec3(mq, sqrt(max(1.0 - mr2, 0.0)));
        float ml = smoothstep(-0.1, 0.4, dot(mn, light)) * (0.4 + 0.6 * max(dot(mn, light), 0.0));
        vec3 mc = vec3(0.78, 0.76, 0.72) * (0.8 + 0.2 * art_noise3(mn * 6.0)) * ml;
        col = mix(col, mc, ma);
    }
    return col;
}

// ---------------------------------------------------------------------------------------------- spiral galaxy

vec3 art_skyGalaxy(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(0.07, 0.055, 0.15), vec3(0.028, 0.022, 0.075), vec3(0.008, 0.007, 0.03));
    col += art_stars(d, px, t, 1.0, detail);
    col += vec3(0.30, 0.20, 0.55) * pow(art_fbm3(d * 2.6 + 5.0, 4), 3.0) * 0.18;
    vec3 C = art_dir(205.0, 40.0);
    vec3 L = art_local(d, C);
    if (L.z <= 0.0) {
        return col;
    }
    float Rg = 0.36;
    vec2 p = art_rot(L.xy / Rg, -0.55);
    float incl = 0.45;
    vec2 q = vec2(p.x, p.y / incl);
    float r = length(q);
    if (r > 1.6) {
        return col;
    }
    float th = atan(q.y, q.x) + t * 0.012;
    int oct = detail >= 2 ? 5 : 4;
    float warp = art_fbm2(q * 2.4 + 3.0, oct);
    float phase = 2.0 * th - 4.4 * log(r + 0.035) + (warp - 0.5) * 2.2;
    float arm = pow(0.5 + 0.5 * cos(phase), 2.4);
    float lane = pow(0.5 + 0.5 * cos(phase - 0.85), 7.0);
    float disk = exp(-r * 2.9) * (1.0 - smoothstep(0.75, 1.35, r));
    float bulge = exp(-r * r * 34.0);
    vec2 rq = art_rot(q, t * 0.012);
    float clumps = art_fbm2(rq * 8.0 + 1.7, oct);
    float knots = smoothstep(0.66, 0.9, art_fbm2(rq * 15.0 + 4.0, 4)) * arm;
    float resolved = smoothstep(0.75, 0.95, art_noise2(rq * 70.0)) * arm;
    vec3 g = vec3(0.56, 0.68, 1.0) * disk * (0.22 + 1.35 * arm * (0.45 + 0.8 * clumps));
    g += vec3(1.0, 0.86, 0.64) * (bulge * 2.4 + disk * 0.28);
    g *= 1.0 - 0.75 * art_sat(lane * 1.6) * smoothstep(0.06, 0.28, r) * disk / max(disk, 0.05);
    g += vec3(1.0, 0.42, 0.62) * knots * disk * 1.5;
    g += vec3(0.85, 0.92, 1.0) * resolved * disk * 1.3;
    // Dust across the near side of the bulge.
    float dustBar = exp(-pow((p.y + 0.02) / 0.035, 2.0)) * exp(-abs(p.x) * 2.5) * step(p.y, 0.05);
    g *= 1.0 - 0.55 * dustBar * smoothstep(0.0, 0.2, abs(p.x) + 0.1);
    col += g * 1.15;
    return col;
}

// ---------------------------------------------------------------------------------------------- aurora

float art_auroraSheet(vec2 q, float t) {
    vec2 w = q + (vec2(art_fbm2(q * 0.5 + vec2(t * 0.018, 1.3), 3), art_fbm2(q * 0.5 + vec2(5.2, -t * 0.015), 3)) - 0.5) * 2.6;
    float s = w.y * 1.9 + sin(w.x * 0.5 + t * 0.05) * 0.9;
    float idx = floor(s);
    float f = fract(s) - 0.5;
    float strength = 0.3 + 0.7 * art_hash11(idx * 13.7 + 2.0);
    float sheet = exp(-f * f * 40.0) + exp(-f * f * 300.0) * 0.6;
    float rays = 0.4 + 0.6 * art_noise2(vec2(w.x * 7.5 + t * 0.22, idx * 3.1));
    rays *= 0.65 + 0.35 * art_noise2(vec2(w.x * 26.0 - t * 0.5, idx + 7.0));
    float pulse = 0.75 + 0.25 * sin(t * 0.7 + w.x * 1.3 + idx);
    return sheet * strength * rays * pulse;
}

vec3 art_aurora(vec3 d, float t, int detail) {
    if (d.y < 0.004) {
        return vec3(0.0);
    }
    int steps = detail >= 2 ? 40 : (detail == 1 ? 26 : 14);
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 48; i++) {
        if (i >= steps) {
            break;
        }
        float fi = (float(i) + art_jitter) / float(steps);
        float h = 1.0 + fi * 1.9;
        vec2 q = d.xz * (h / d.y) * 0.22;
        float north = smoothstep(0.02, 0.45, -q.y) * exp(-length(q) * 0.16);
        if (north < 0.005) {
            continue;
        }
        float c = art_auroraSheet(q, t);
        float fade = exp(-fi * 2.4);
        vec3 tint = mix(vec3(0.16, 1.0, 0.56), vec3(0.58, 0.30, 1.0), smoothstep(0.16, 0.8, fi));
        tint = mix(tint, vec3(1.0, 0.28, 0.45), smoothstep(0.72, 1.0, fi) * 0.45);
        acc += tint * c * fade * north;
    }
    return acc * (9.0 / float(steps));
}

vec3 art_skyAurora(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(0.03, 0.10, 0.13), vec3(0.012, 0.042, 0.072), vec3(0.004, 0.012, 0.03));
    col += art_stars(d, px, t, 0.85, detail);
    vec3 a = art_aurora(d, t, detail);
    col += a;
    // The aurora lights the air a little.
    col += vec3(0.02, 0.07, 0.04) * smoothstep(0.0, 0.5, -d.z) * exp(-max(d.y, 0.0) * 4.0);
    return col;
}

// ---------------------------------------------------------------------------------------------- eclipse

vec3 art_skyEclipse(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(0.06, 0.075, 0.14), vec3(0.025, 0.035, 0.08), vec3(0.01, 0.014, 0.04));
    // During totality the whole horizon glows like a sunset.
    float hz = exp(-max(d.y, 0.0) * 13.0) * smoothstep(-0.06, 0.0, d.y);
    col += vec3(0.62, 0.32, 0.12) * hz * 0.4 + vec3(0.3, 0.14, 0.22) * exp(-max(d.y, 0.0) * 4.5) * 0.1;
    col += art_stars(d, px, t, 0.75, detail);
    vec3 S = art_dir(180.0, 32.0);
    vec3 L = art_local(d, S);
    if (L.z <= 0.0) {
        return col;
    }
    float Rm = 0.062;
    vec2 p = L.xy / Rm;
    float r = length(p);
    float a = atan(p.y, p.x);
    float pxr = px / Rm;
    vec2 ca = vec2(cos(a), sin(a));
    int oct = detail >= 2 ? 5 : 4;
    float big = art_fbm2(ca * 2.2 + vec2(t * 0.004, 0.0), oct);
    float fine = art_fbm2(ca * 22.0 + vec2(0.0, -t * 0.01), oct);
    float rays = art_fbm2(ca * 60.0 + vec2(r * 0.05, 0.0), 3);
    float lobes = 0.5 + 0.5 * pow(abs(cos(a - 0.35)), 1.5);
    float rr = max(r, 1.0);
    float corona = pow(rr, -1.9) * (0.3 + 1.4 * big * lobes) * (0.5 + 0.6 * fine + 0.35 * rays);
    corona += pow(rr, -1.3) * 0.12 * pow(rays, 2.0) * lobes;
    float inner = exp(-(rr - 1.0) * 9.0) * 1.7;
    col += vec3(0.88, 0.93, 1.0) * (corona * 1.3 + inner) * smoothstep(0.97, 1.0, r);
    // Glow of the corona spreading into the sky.
    col += vec3(0.5, 0.6, 0.85) * exp(-r * 0.3) * 0.16;
    // Red chromosphere and prominences at the edge.
    float limb = exp(-pow((r - 1.0) / max(0.014, pxr), 2.0));
    float prom = smoothstep(0.6, 0.78, art_fbm2(ca * 10.0 + vec2(t * 0.02, 1.0), 4));
    prom *= exp(-max(r - 1.0, 0.0) * 26.0) * step(1.0, r);
    col += vec3(1.0, 0.22, 0.32) * (limb * 0.9 + prom * 1.6);
    // The moon's disc, with a hint of earthshine.
    float disc = 1.0 - smoothstep(1.0 - pxr * 1.2, 1.0 + pxr * 0.6, r);
    vec3 earthshine = vec3(0.012, 0.014, 0.02) * (0.7 + 0.6 * art_fbm2(p * 2.0, 3));
    col = mix(col, earthshine, disc);
    // The diamond ring: a bead of sunlight that flashes now and then.
    float cycle = fract(t / 28.0);
    float flash = smoothstep(0.0, 0.06, cycle) * (1.0 - smoothstep(0.1, 0.28, cycle));
    vec2 bead = vec2(cos(2.25), sin(2.25)) * 1.01;
    vec2 pb = p - bead;
    float db = length(pb);
    col += vec3(1.0, 0.97, 0.9) * flash * (exp(-db * db / 0.003) * 7.0 + exp(-db * 2.2) * 0.9);
    col += vec3(1.0, 0.95, 0.88) * flash * 0.5 * (exp(-abs(pb.y) * 70.0) * exp(-abs(pb.x) * 0.8) + exp(-abs(pb.x) * 70.0) * exp(-abs(pb.y) * 0.8));
    return col;
}

// ---------------------------------------------------------------------------------------------- black hole

vec3 art_diskLight(float rd, float ang, float t, int oct) {
    if (rd < 1.9 || rd > 8.0) {
        return vec3(0.0);
    }
    float spin = t * 0.9 * pow(rd / 2.5, -1.5);
    vec2 ca = vec2(cos(ang + spin), sin(ang + spin)) * rd;
    float turb = art_fbm2(ca * 1.8 + vec2(rd * 0.6, 0.0), oct);
    float streaks = art_noise2(vec2(rd * 9.0, (ang + spin) * 3.0));
    float heat = pow(2.3 / rd, 1.1);
    vec3 c = mix(vec3(0.9, 0.28, 0.05), vec3(1.0, 0.74, 0.4), art_sat(heat * 1.2));
    c = mix(c, vec3(1.0, 0.96, 0.9), art_sat((heat - 0.7) * 2.0));
    float edge = smoothstep(1.9, 2.4, rd) * (1.0 - smoothstep(5.5, 8.0, rd));
    return c * heat * 2.4 * edge * (0.45 + 0.75 * turb) * (0.8 + 0.4 * streaks);
}

vec3 art_skyBlackHole(vec3 d, float t, float px, int detail) {
    vec3 B = art_dir(200.0, 32.0);
    vec3 L = art_local(d, B);
    float Rs = 0.058;
    vec3 seen = d;
    if (L.z > 0.0) {
        // Light from behind bends around the hole, so the sky behind it is seen pulled outward.
        float th = length(L.xy);
        float thE = Rs * 2.4;
        float beta = th - thE * thE / max(th, 1e-5);
        vec3 side = normalize(d - B * dot(d, B) + vec3(1e-6));
        seen = normalize(B * cos(beta) + side * sin(beta));
    }
    vec3 col = art_gradient(d.y, vec3(0.035, 0.032, 0.06), vec3(0.012, 0.012, 0.03), vec3(0.004, 0.004, 0.014));
    vec3 n = normalize(vec3(0.25, 0.48, 0.84));
    vec4 band = art_band(seen, n, normalize(cross(n, vec3(0.0, 1.0, 0.0))), detail, t);
    col += band.rgb * 0.9;
    col += art_stars(seen, px, t, 1.1, detail);
    if (L.z <= 0.0) {
        return col;
    }
    vec2 p = art_rot(L.xy / Rs, 0.14);
    float r = length(p);
    float pxr = px / Rs;
    int oct = detail >= 2 ? 5 : 3;
    // The far side of the disk, bent over and under the hole.
    float a = atan(p.y, p.x);
    if (r > 1.0 && r < 3.2) {
        float rl = 2.0 + (r - 1.0) * 1.8;
        vec3 lensed = art_diskLight(rl, a + ART_PI, t, oct);
        float over = smoothstep(0.0, 0.5, p.y / r) * 1.0 + smoothstep(0.0, 0.6, -p.y / r) * 0.35;
        col += lensed * over * (1.0 - smoothstep(1.3, 3.0, r)) * 0.9;
    }
    // Photon ring.
    col += vec3(1.0, 0.85, 0.6) * exp(-pow((r - 1.04) / max(0.025, pxr), 2.0)) * 1.4;
    // The shadow.
    float shadow = 1.0 - smoothstep(1.0 - pxr, 1.0 + pxr, r);
    col = mix(col, vec3(0.0), shadow);
    // The disk itself, seen nearly edge-on: its near half crosses in front of the shadow.
    float incl = 0.17;
    vec2 e = vec2(p.x, p.y / incl);
    float rd = length(e);
    float ang = atan(e.y, e.x);
    vec3 diskc = art_diskLight(rd, ang, t, oct);
    float doppler = 1.0 + 0.7 * cos(ang);
    diskc *= doppler * doppler;
    float near = step(p.y, 0.0);
    float visible = mix(1.0 - shadow, 1.0, near);
    col += diskc * visible;
    col += vec3(1.0, 0.6, 0.3) * exp(-r * 0.6) * 0.05;
    return col;
}

// ---------------------------------------------------------------------------------------------- nebula

vec3 art_skyNebula(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(0.06, 0.035, 0.10), vec3(0.022, 0.014, 0.045), vec3(0.008, 0.005, 0.018));
    col += art_stars(d, px, t, 0.9, detail);
    vec3 C = art_dir(140.0, 36.0);
    float ang = acos(clamp(dot(d, C), -1.0, 1.0));
    if (ang > 1.2) {
        return col;
    }
    int oct = detail >= 2 ? 6 : (detail == 1 ? 5 : 3);
    vec3 q = d * 2.3 + vec3(0.0, t * 0.0025, t * 0.001);
    vec3 warp = vec3(art_fbm3(q + 1.7, 4), art_fbm3(q + 9.2, 4), art_fbm3(q + 4.4, 4));
    vec3 w = q + (warp - 0.5) * 2.0;
    float shape = exp(-ang * ang / 0.22) * smoothstep(0.3, 0.62, art_fbm3(w * 0.9 + 2.0, 4));
    float hyd = art_fbm3(w * 1.7, oct);
    float oxy = art_fbm3(w * 2.4 + 5.0, oct);
    float dust = smoothstep(0.48, 0.72, art_fbm3(w * 3.2 + 2.0, oct));
    float fil = 1.0 - abs(art_fbm3(w * 4.0 + 8.0, oct) * 2.0 - 1.0);
    vec3 neb = vec3(0.98, 0.24, 0.44) * pow(hyd, 2.2) * 2.6;
    neb += vec3(0.16, 0.78, 0.88) * pow(oxy, 3.0) * 2.3;
    neb += vec3(1.0, 0.62, 0.22) * pow(hyd * oxy, 2.0) * 1.6;
    neb += vec3(1.0, 0.8, 0.95) * pow(fil, 8.0) * 0.45;
    neb *= shape * (1.0 - 0.82 * dust);
    col = col * (1.0 - 0.5 * dust * shape) + neb;
    // Young stars glowing inside it.
    col += art_starLayer(d, 22.0, 0.35 * shape, px, t, 2.4, 8.0);
    return col;
}

// ---------------------------------------------------------------------------------------------- moons

// A moon of radius 1 at p, lit from `light`; alpha is its coverage.
vec3 art_moon(vec2 p, float pxr, vec3 light, float seed, vec3 tint, out float alpha) {
    float r2 = dot(p, p);
    float r = sqrt(r2);
    alpha = 1.0 - smoothstep(1.0 - pxr * 1.2, 1.0 + pxr * 0.6, r);
    if (r2 >= 1.0) {
        return vec3(0.0);
    }
    vec3 n = vec3(p, sqrt(1.0 - r2));
    float maria = smoothstep(0.46, 0.66, art_fbm3(n * 2.1 + seed, 5));
    float pits = art_fbm3(n * 9.0 + seed * 3.0, 4);
    float craters = smoothstep(0.55, 0.75, pits) - smoothstep(0.75, 0.9, pits) * 0.6;
    vec3 albedo = tint * (0.9 - 0.36 * maria) * (0.88 + 0.22 * craters) * (0.93 + 0.14 * art_noise3(n * 30.0 + seed));
    float lam = dot(n, light);
    float lit = smoothstep(-0.06, 0.28, lam) * (0.35 + 0.65 * max(lam, 0.0));
    float limb = pow(n.z, 0.2);
    return albedo * (lit * limb * 1.25 + 0.035);
}

vec3 art_skyTwinMoons(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(0.06, 0.17, 0.21), vec3(0.026, 0.085, 0.125), vec3(0.008, 0.025, 0.05));
    col += art_stars(d, px, t, 0.75, detail);
    vec3 M1 = art_dir(120.0, 32.0);
    vec3 M2 = art_dir(141.0, 21.0);
    float a1 = acos(clamp(dot(d, M1), -1.0, 1.0));
    float a2 = acos(clamp(dot(d, M2), -1.0, 1.0));
    col += vec3(0.55, 0.75, 1.0) * (exp(-a1 * 4.5) * 0.16 + exp(-a1 * 22.0) * 0.25);
    col += vec3(1.0, 0.65, 0.78) * (exp(-a2 * 9.0) * 0.08 + exp(-a2 * 35.0) * 0.15);
    float alpha;
    vec3 L1 = art_local(d, M1);
    if (L1.z > 0.0) {
        vec3 m = art_moon(L1.xy / 0.075, px / 0.075, normalize(vec3(-0.55, 0.25, 0.8)), 1.0, vec3(0.92, 0.95, 1.0), alpha);
        col = mix(col, m, alpha);
    }
    vec3 L2 = art_local(d, M2);
    if (L2.z > 0.0) {
        vec3 m = art_moon(L2.xy / 0.032, px / 0.032, normalize(vec3(0.85, 0.1, 0.3)), 7.0, vec3(1.0, 0.78, 0.74), alpha);
        col = mix(col, m, alpha);
    }
    // Thin clouds drifting past, lit by the big moon.
    col = art_clouds(d, t, col, vec3(0.5, 0.65, 0.75), vec3(0.05, 0.11, 0.15), M1, detail, 0.38, 0.75);
    return col;
}

vec3 art_skyMoonHalo(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(0.11, 0.13, 0.19), vec3(0.055, 0.07, 0.115), vec3(0.02, 0.028, 0.06));
    col += art_stars(d, px, t, 0.5, detail);
    vec3 M = art_dir(165.0, 34.0);
    float ang = acos(clamp(dot(d, M), -1.0, 1.0));
    // A thin icy veil that the moonlight shines through.
    vec2 vq = d.xz / (d.y + 0.15) * 1.3 + vec2(t * 0.008, 0.0);
    float veil = art_fbm2(vec2(vq.x * 0.6, vq.y * 1.6), 5);
    col += vec3(0.6, 0.7, 0.85) * exp(-ang * 3.2) * 0.22 * (0.55 + 0.7 * veil);
    col += vec3(0.7, 0.78, 0.9) * exp(-ang * 14.0) * 0.35;
    // The 22-degree ring: sharp and reddish on the inside, fading out on the outside.
    float R = radians(22.0);
    float x = ang - R;
    float ring = x < 0.0 ? exp(-x * x / 0.00005) : exp(-x / 0.03);
    vec3 rc = mix(vec3(1.0, 0.62, 0.42), vec3(0.75, 0.85, 1.0), art_sat(x / 0.025));
    col += rc * ring * 0.3 * (0.65 + 0.55 * veil);
    // Moon dogs either side.
    for (int i = 0; i < 2; i++) {
        float side = i == 0 ? -1.0 : 1.0;
        vec3 P = art_dir(165.0 + side * 25.5, 34.0);
        vec3 Lp = art_local(d, P);
        if (Lp.z > 0.0) {
            vec2 q = Lp.xy;
            float spot = exp(-(q.x * q.x / 0.00015 + q.y * q.y / 0.0004));
            float tail = exp(-abs(q.y) / 0.008) * exp(-max(q.x * side, 0.0) / 0.06) * step(0.0, q.x * side);
            col += mix(vec3(1.0, 0.7, 0.5), vec3(0.85, 0.9, 1.0), art_sat(q.x * side / 0.01)) * (spot * 0.5 + tail * 0.07);
        }
    }
    // Ice crystals glittering in the air.
    col += art_starLayer(d, 140.0, 0.08, px, t * 9.0, 0.9, 12.0) * smoothstep(0.6, 0.0, d.y);
    vec3 L = art_local(d, M);
    if (L.z > 0.0) {
        float alpha;
        vec3 m = art_moon(L.xy / 0.032, px / 0.032, normalize(vec3(0.1, 0.1, 1.0)), 3.0, vec3(1.0, 0.98, 0.94), alpha);
        col = mix(col, m * 1.15, alpha);
    }
    return col;
}

// ---------------------------------------------------------------------------------------------- meteors

vec3 art_meteors(vec3 d, float t, float px) {
    vec3 R = art_dir(48.0, 52.0);
    vec3 r1 = normalize(cross(R, vec3(0.0, 1.0, 0.0)));
    vec3 u1 = cross(r1, R);
    vec3 acc = vec3(0.0);
    for (int i = 0; i < 32; i++) {
        float fi = float(i);
        float period = 1.8 + 2.2 * art_hash11(fi * 3.1 + 0.4);
        float tt = t + art_hash11(fi * 7.7) * period;
        float cyc = floor(tt / period);
        float ph = tt - cyc * period;
        float dur = 0.9 + 0.8 * art_hash11(cyc * 1.31 + fi * 4.1);
        if (ph > dur) {
            continue;
        }
        float k = ph / dur;
        float ha = art_hash11(cyc * 5.1 + fi * 2.2);
        float hb = art_hash11(cyc * 2.3 + fi * 1.7);
        float hc = art_hash11(cyc * 9.7 + fi * 0.3);
        float a = ha * ART_TAU;
        vec3 outward = normalize(r1 * cos(a) + u1 * sin(a));
        float start = 0.12 + 0.5 * hb;
        float len = 0.3 + 0.32 * hc;
        float hk = start + len * k;
        float tk = start + len * max(k - 0.45, 0.0);
        vec3 head = R * cos(hk) + outward * sin(hk);
        vec3 tail = R * cos(tk) + outward * sin(tk);
        vec3 ab = head - tail;
        float s = clamp(dot(d - tail, ab) / max(dot(ab, ab), 1e-9), 0.0, 1.0);
        float dist = length(d - (tail + ab * s));
        float w = max(px * 1.2, 0.0015);
        float line = exp(-dist * dist / (w * w)) * (0.0015 / w);
        float fade = sqrt(sin(k * ART_PI));
        vec3 c = mix(vec3(0.55, 0.7, 1.0), vec3(1.0, 0.97, 0.9), s) * (0.15 + 0.85 * s * s);
        if (hc > 0.82) {
            c = mix(c, vec3(0.45, 1.0, 0.62), 0.6 * s);
        }
        acc += c * line * fade * 3.2;
        float hd = length(d - head);
        acc += vec3(1.0, 0.95, 0.86) * exp(-hd * hd / (w * w * 7.0)) * fade * 1.8 * (0.0015 / w);
    }
    return acc * smoothstep(0.0, 0.06, d.y);
}

vec3 art_skyMeteors(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(0.05, 0.07, 0.13), vec3(0.02, 0.03, 0.07), vec3(0.006, 0.01, 0.03));
    vec3 n = normalize(vec3(-0.5, 0.55, 0.67));
    vec4 band = art_band(d, n, normalize(cross(n, vec3(0.0, 0.0, 1.0))), detail, t);
    col += band.rgb * 0.8;
    col += art_stars(d, px, t, 1.0 + band.a, detail);
    col += art_meteors(d, t, px);
    return col;
}

// ---------------------------------------------------------------------------------------------- comet

vec3 art_skyComet(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(0.11, 0.11, 0.21), vec3(0.035, 0.045, 0.11), vec3(0.008, 0.012, 0.04));
    vec3 W = art_dir(250.0, 0.0);
    col += vec3(0.45, 0.25, 0.12) * exp(-max(d.y, 0.0) * 9.0) * pow(max(dot(normalize(vec3(d.x, 0.0, d.z) + vec3(1e-5)), W), 0.0), 3.0) * 0.35;
    col += art_stars(d, px, t, 0.9, detail);
    vec3 H = art_dir(250.0, 16.0);
    vec3 L = art_local(d, H);
    if (L.z <= 0.0) {
        return col;
    }
    vec2 p = L.xy;
    vec2 ax = normalize(vec2(0.52, 0.85));
    float along = dot(p, ax);
    float across = dot(p, vec2(-ax.y, ax.x));
    float a = max(along, 0.0);
    float bent = across + a * a * 0.85;
    float w = 0.006 + a * 0.2;
    float dust = exp(-bent * bent / (w * w)) * exp(-a * 2.4) * smoothstep(-0.012, 0.03, along);
    float stri = 0.7 + 0.3 * art_noise2(vec2(bent / max(w, 1e-3) * 4.0, a * 3.0 - t * 0.02));
    float acrossIon = across - a * 0.04;
    float wi = 0.0018 + a * 0.028;
    float ion = exp(-acrossIon * acrossIon / (wi * wi)) * exp(-a * 1.4) * smoothstep(-0.006, 0.02, along);
    float knots = 0.55 + 0.45 * art_noise2(vec2(a * 28.0 - t * 0.7, 0.0));
    float pr = length(p);
    float coma = exp(-pr / 0.005) * 2.8 + exp(-pr / 0.018) * 0.7;
    col += vec3(1.0, 0.93, 0.78) * dust * stri * 1.05;
    col += vec3(0.42, 0.68, 1.0) * ion * knots * 0.95;
    col += vec3(0.72, 1.0, 0.9) * coma;
    return col;
}

// ---------------------------------------------------------------------------------------------- light pillars

vec3 art_skyPillars(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(0.07, 0.065, 0.13), vec3(0.028, 0.026, 0.065), vec3(0.008, 0.01, 0.026));
    col += art_stars(d, px, t, 0.75, detail);
    float yaw = art_yaw(d);
    float e = art_elev(d);
    float ce = cos(e);
    for (int i = 0; i < 22; i++) {
        float fi = float(i);
        float py = (fi + 0.8 * art_hash11(fi * 4.13 + 0.7)) / 22.0 * ART_TAU;
        float dy = abs(mod(yaw - py + ART_PI, ART_TAU) - ART_PI) * ce;
        float w = max(0.006 + 0.014 * art_hash11(fi * 1.9), px * 0.8);
        float h = 0.25 + 0.45 * art_hash11(fi * 7.3);
        float beam = exp(-dy * dy / (w * w));
        float vert = exp(-max(e, 0.0) / h * 1.5) * smoothstep(-0.02, 0.01, e);
        float shimmer = 0.6 + 0.4 * art_noise2(vec2(fi * 7.0, e * 16.0 - t * 0.5));
        float pulse = 0.6 + 0.4 * sin(t * (0.25 + 0.35 * art_hash11(fi * 2.7)) + fi * 2.0);
        float hue = art_hash11(fi * 5.3);
        vec3 c = hue < 0.35 ? vec3(1.0, 0.92, 0.78) : (hue < 0.6 ? vec3(1.0, 0.7, 0.4) : (hue < 0.82 ? vec3(0.78, 0.6, 1.0) : vec3(0.5, 0.9, 1.0)));
        col += c * beam * vert * shimmer * pulse * 1.2;
        col += c * exp(-dy / (w * 7.0)) * vert * pulse * 0.08;
        col += c * exp(-dy / (w * 3.0)) * exp(-max(e, 0.0) * 30.0) * 0.25 * pulse;
    }
    col += art_starLayer(d, 150.0, 0.06, px, t * 8.0, 0.8, 14.0) * smoothstep(0.5, 0.0, d.y);
    return col;
}

// ---------------------------------------------------------------------------------------------- star rift

vec3 art_skyRift(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(0.08, 0.045, 0.13), vec3(0.03, 0.016, 0.062), vec3(0.01, 0.006, 0.025));
    col += art_stars(d, px, t, 0.9, detail);
    vec3 n = normalize(vec3(0.55, 0.38, -0.74));
    vec3 C = art_dir(90.0, 35.0);
    vec3 e1 = normalize(C - n * dot(C, n));
    vec3 e2 = cross(n, e1);
    float b = dot(d, n);
    float along = atan(dot(d, e2), dot(d, e1));
    float span = 1.0 - smoothstep(0.35, 1.05, abs(along));
    if (span <= 0.0 && abs(b) > 0.3) {
        return col;
    }
    float jag = (art_fbm2(vec2(along * 5.0, 1.3), 5) - 0.5) * 0.1 + (art_noise2(vec2(along * 34.0, 4.0)) - 0.5) * 0.012;
    float dist = abs(b - jag);
    float width = 0.004 + 0.05 * span * (0.55 + 0.45 * art_noise2(vec2(along * 4.0 - t * 0.04, 2.0)));
    float aa = max(px * 1.5, width * 0.15);
    float inside = (1.0 - smoothstep(width - aa, width + aa, dist)) * step(0.001, span);
    vec3 q = d * 6.0 + vec3(t * 0.02, 0.0, -t * 0.015);
    vec3 other = vec3(0.45, 0.16, 0.85) * art_fbm3(q * 2.0, 4) * 1.6;
    other += vec3(0.2, 0.9, 1.0) * pow(art_fbm3(q * 3.3 + 4.0, 4), 3.0) * 2.6;
    other += art_starLayer(d, 110.0, 0.7, px, t, 2.2, 5.0);
    col = mix(col, other, inside);
    float edge = exp(-pow((dist - width) / max(width * 0.35 + px, px * 1.5), 2.0)) * span;
    float energy = 0.55 + 0.45 * art_noise2(vec2(along * 42.0 - t * 1.8, dist * 220.0));
    vec3 ec = mix(vec3(1.0, 0.32, 1.0), vec3(0.42, 0.95, 1.0), art_noise2(vec2(along * 7.0 + t * 0.3, 0.0)));
    col += ec * edge * energy * 1.5;
    col += vec3(0.5, 0.22, 0.8) * exp(-dist / (0.045 + width)) * span * 0.28;
    // Sparks drifting out of it.
    vec2 g = vec2(along * 60.0, (b - jag) * 60.0 + t * 0.6 * sign(b - jag));
    vec2 cell = floor(g);
    float hs = art_hash12(cell);
    vec2 f = fract(g) - 0.5 - (art_hash22(cell) - 0.5) * 0.6;
    float spark = exp(-dot(f, f) * 90.0) * step(hs, 0.18) * exp(-dist * 9.0) * span;
    col += ec * spark * 1.4;
    return col;
}

// ---------------------------------------------------------------------------------------------- sky lanterns

vec4 art_lanternLayer(vec3 d, float t, float px, float fl) {
    float yaw = art_yaw(d);
    float e = art_elev(d);
    float cells = 46.0 - fl * 12.0;
    float cellAngle = ART_TAU / cells;
    vec2 g = vec2(yaw / cellAngle, e / cellAngle);
    g.y -= t * (0.05 + 0.03 * fl);
    vec2 cell = floor(g);
    float hs = art_hash12(cell + fl * 31.0);
    if (hs > 0.32) {
        return vec4(0.0);
    }
    vec2 off = (art_hash22(cell + fl * 11.0) - 0.5) * 0.5;
    vec2 f = fract(g) - 0.5 - off;
    f.x += sin(t * 0.4 + hs * 30.0) * 0.06;
    float size = 0.09 + 0.05 * fl + 0.03 * hs;
    vec2 s = f / size;
    float aa = px / cellAngle / size * 1.5;
    float taper = 1.0 - 0.18 * (s.y + 1.0) * 0.5;
    float body = max(abs(s.x) / (0.75 * taper), abs(s.y) / 1.0);
    float shape = 1.0 - smoothstep(1.0 - aa, 1.0 + aa, body);
    float flicker = 0.85 + 0.15 * sin(t * (7.0 + hs * 9.0) + hs * 50.0);
    vec3 inner = mix(vec3(1.0, 0.82, 0.4), vec3(1.0, 0.38, 0.12), art_sat((s.y + 1.0) * 0.5));
    vec3 c = inner * flicker * (1.15 - 0.25 * abs(s.x));
    float glow = exp(-length(s) * 1.4) * 0.35 * flicker;
    float rise = smoothstep(-0.02, 0.06, e) * (1.0 - smoothstep(0.9, 1.25, e));
    float dim = 0.55 + 0.45 * fl / 2.0;
    return vec4(c * shape * dim + vec3(1.0, 0.55, 0.2) * glow * dim, shape) * rise;
}

vec3 art_skyLanterns(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(0.34, 0.17, 0.2), vec3(0.09, 0.065, 0.17), vec3(0.02, 0.022, 0.075));
    col += art_stars(d, px, t, 0.55, detail) * smoothstep(0.15, 0.6, d.y);
    for (int i = 0; i < 3; i++) {
        vec4 l = art_lanternLayer(d, t, px, float(i));
        col = col * (1.0 - l.a * 0.9) + l.rgb;
    }
    return col;
}

// ---------------------------------------------------------------------------------------------- sakura dusk

vec4 art_petalLayer(vec3 d, float t, float px, float fl) {
    float yaw = art_yaw(d);
    float e = art_elev(d);
    float cells = 64.0 - fl * 20.0;
    float cellAngle = ART_TAU / cells;
    vec2 g = vec2(yaw / cellAngle, e / cellAngle);
    g.y += t * (0.25 + 0.1 * fl);
    g.x += t * 0.08;
    vec2 cell = floor(g);
    float hs = art_hash12(cell + fl * 37.0);
    if (hs > 0.42) {
        return vec4(0.0);
    }
    vec2 off = (art_hash22(cell + fl * 13.0) - 0.5) * 0.5;
    vec2 f = fract(g) - 0.5 - off;
    f.x += sin(t * 0.9 + hs * 40.0) * 0.08;
    float spin = t * (0.6 + hs * 2.5) + hs * 40.0;
    vec2 pp = art_rot(f, spin);
    float size = 0.07 + 0.035 * fl;
    float tumble = 0.45 + 0.55 * abs(cos(t * (1.2 + hs) + hs * 20.0));
    vec2 s = pp / vec2(size, size * 0.62 * tumble + 0.02);
    float notch = 0.25 * exp(-pow((s.x - 0.95) / 0.18, 2.0) - s.y * s.y * 8.0);
    float body = length(s) + notch;
    float aa = px / cellAngle / size * 1.5;
    float shape = 1.0 - smoothstep(0.85 - aa, 0.95 + aa, body);
    vec3 c = mix(vec3(0.98, 0.55, 0.74), vec3(1.0, 0.86, 0.92), art_sat(s.x * 0.4 + 0.5)) * (0.8 + 0.2 * tumble);
    return vec4(c, shape * 0.95);
}

vec3 art_skySakura(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(1.0, 0.76, 0.70), vec3(0.86, 0.58, 0.78), vec3(0.40, 0.38, 0.72));
    vec3 S = art_dir(250.0, -3.0);
    float sd = max(dot(d, S), 0.0);
    col += vec3(1.0, 0.72, 0.52) * pow(sd, 14.0) * 0.5 + vec3(1.0, 0.6, 0.62) * pow(sd, 3.0) * 0.14;
    col = art_clouds(d, t, col, vec3(1.0, 0.84, 0.88), vec3(0.66, 0.48, 0.70), S, detail, 0.5, 0.85);
    col += art_stars(d, px, t, 0.35, detail) * smoothstep(0.55, 0.9, d.y);
    for (int i = 0; i < 3; i++) {
        vec4 p = art_petalLayer(d, t, px, float(i));
        col = mix(col, p.rgb, p.a);
    }
    return col;
}

// ---------------------------------------------------------------------------------------------- golden hour

vec3 art_skyGolden(vec3 d, float t, float px, int detail) {
    vec3 S = art_dir(270.0, 5.0);
    float sd = dot(d, S);
    vec3 col = art_gradient(d.y, vec3(1.0, 0.62, 0.32), vec3(0.66, 0.58, 0.64), vec3(0.22, 0.38, 0.70));
    col += vec3(1.0, 0.52, 0.22) * pow(max(sd, 0.0), 7.0) * 0.55;
    col += vec3(1.0, 0.75, 0.45) * pow(max(sd, 0.0), 60.0) * 0.6;
    float ang = acos(clamp(sd, -1.0, 1.0));
    // Rays of light fanning out from the sun.
    vec3 L = art_local(d, S);
    if (L.z > 0.0) {
        float a = atan(L.y, L.x);
        float rays = art_noise2(vec2(a * 10.0, t * 0.03)) * art_noise2(vec2(a * 23.0, 3.0 - t * 0.02));
        col += vec3(1.0, 0.72, 0.42) * smoothstep(0.2, 0.6, rays) * exp(-ang * 2.0) * 0.16 * smoothstep(0.0, 0.1, d.y);
    }
    // Clouds catching the light: orange and pink on the sun's side, violet in the shade.
    vec3 lit = mix(vec3(1.0, 0.78, 0.6), vec3(1.0, 0.62, 0.42), art_sat(sd));
    col = art_clouds(d, t, col, lit, vec3(0.52, 0.42, 0.58), S, detail, 0.46, 0.9);
    // The sun.
    float disc = 1.0 - smoothstep(0.022 - px, 0.022 + px, ang);
    col += vec3(1.0, 0.86, 0.62) * exp(-ang * 20.0) * 1.1;
    col = mix(col, vec3(1.7, 1.42, 1.0), disc);
    return col;
}

// ---------------------------------------------------------------------------------------------- synth sunset

vec3 art_skySynth(vec3 d, float t, float px, int detail) {
    vec3 col = art_gradient(d.y, vec3(1.0, 0.3, 0.55), vec3(0.42, 0.10, 0.50), vec3(0.07, 0.03, 0.20));
    col += vec3(1.0, 0.25, 0.6) * exp(-max(d.y, 0.0) * 30.0) * 0.45 * smoothstep(-0.03, 0.0, d.y);
    col += art_stars(d, px, t, 0.7, detail) * smoothstep(0.25, 0.6, d.y);
    vec3 S = art_dir(180.0, 11.0);
    vec3 L = art_local(d, S);
    if (L.z > 0.0) {
        float R = 0.16;
        vec2 p = L.xy / R;
        float r = length(p);
        float pxr = px / R;
        col += vec3(1.0, 0.3, 0.62) * exp(-max(r - 1.0, 0.0) * 3.2) * 0.4 * step(1.0, r);
        col += vec3(1.0, 0.5, 0.4) * exp(-r * 1.1) * 0.12;
        vec3 sunc = mix(vec3(1.0, 0.2, 0.6), vec3(1.0, 0.88, 0.32), smoothstep(-0.95, 0.75, p.y));
        float gapSize = art_sat((0.18 - p.y) * 0.4);
        float f = fract(p.y * 7.0 + t * 0.12);
        float aa = pxr * 7.0;
        float keep = smoothstep(gapSize - aa, gapSize + aa, f) + step(gapSize, 0.0);
        float disc = (1.0 - smoothstep(1.0 - pxr, 1.0 + pxr, r)) * art_sat(keep);
        col = mix(col, sunc * 1.2, disc);
    }
    float e = art_elev(d);
    float yaw = art_yaw(d);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float y0 = 0.06 + fi * 0.045;
        float thick = 0.004 + 0.003 * fi;
        float strip = exp(-pow((e - y0 - 0.004 * sin(yaw * 3.0 + fi)) / thick, 2.0));
        strip *= smoothstep(0.35, 0.65, art_noise2(vec2(yaw * (5.0 + fi * 2.0) + t * (0.015 + fi * 0.01), fi * 7.0)));
        col = mix(col, vec3(0.32, 0.07, 0.32) * (1.0 - fi * 0.15), strip * 0.85);
    }
    return col;
}

// ---------------------------------------------------------------------------------------------- entry points

// Where each sky's main object is (yaw, elevation in degrees); previews look that way.
vec2 art_hero(int sky) {
    if (sky == 1) return vec2(160.0, 26.0);
    if (sky == 2) return vec2(160.0, 34.0);
    if (sky == 3) return vec2(205.0, 40.0);
    if (sky == 4) return vec2(185.0, 24.0);
    if (sky == 5) return vec2(180.0, 32.0);
    if (sky == 6) return vec2(200.0, 32.0);
    if (sky == 7) return vec2(140.0, 36.0);
    if (sky == 8) return vec2(128.0, 28.0);
    if (sky == 9) return vec2(165.0, 30.0);
    if (sky == 10) return vec2(40.0, 34.0);
    if (sky == 11) return vec2(244.0, 24.0);
    if (sky == 12) return vec2(10.0, 14.0);
    if (sky == 13) return vec2(90.0, 35.0);
    if (sky == 14) return vec2(300.0, 22.0);
    if (sky == 15) return vec2(250.0, 18.0);
    if (sky == 16) return vec2(270.0, 10.0);
    if (sky == 17) return vec2(180.0, 14.0);
    return vec2(180.0, 30.0);
}

// The viewing direction for a point of a preview tile (uv: 0..1 from the top left).
vec3 art_previewDir(int sky, vec2 uv, float aspect) {
    vec2 h = art_hero(sky);
    float camElev = clamp(h.y - 14.0, 3.0, 22.0);
    vec3 f = art_dir(h.x, camElev);
    vec3 r = normalize(cross(f, vec3(0.0, 1.0, 0.0)));
    vec3 u = cross(r, f);
    float th = tan(radians(30.0));
    vec2 s = vec2((uv.x * 2.0 - 1.0) * aspect, 1.0 - uv.y * 2.0) * th;
    return normalize(f + r * s.x + u * s.y);
}

// Minecraft's own sky, for the "no sky" preview.
vec3 art_vanillaPreview(vec3 d) {
    vec3 col = mix(vec3(0.75, 0.85, 1.0), vec3(0.47, 0.65, 1.0), smoothstep(0.0, 0.5, d.y));
    vec3 L = art_local(d, art_dir(150.0, 40.0));
    if (L.z > 0.0 && abs(L.x) < 0.05 && abs(L.y) < 0.05) {
        col = vec3(1.0, 0.98, 0.85);
    }
    vec2 q = d.xz / max(d.y, 0.05) * 1.6;
    float cl = step(0.72, art_noise2(floor(q * 2.0) * 0.35));
    return mix(col, vec3(1.0), cl * 0.9 * smoothstep(0.12, 0.35, d.y));
}

// Blocky hills and trees along the bottom of a preview tile (1 = solid).
float art_silhouette(vec2 uv, float aspect, float seed) {
    float rows = 56.0;
    float cols = floor(rows * aspect);
    vec2 g = floor(vec2(uv.x * cols, (1.0 - uv.y) * rows));
    float x = g.x;
    float hgt = floor(5.0 + 3.5 * art_noise2(vec2(x * 0.045 + seed * 3.0, 0.0)) + 2.5 * art_noise2(vec2(x * 0.13 + seed, 5.0)));
    float solid = step(g.y, hgt);
    float cellW = 13.0;
    float ci = floor(x / cellW);
    float fx = x - ci * cellW;
    float hasTree = step(art_hash11(ci * 3.7 + seed * 1.3 + 0.5), 0.55);
    float tx = 3.0 + floor(art_hash11(ci * 1.3 + seed) * 7.0);
    float xt = ci * cellW + tx;
    float baseH = floor(5.0 + 3.5 * art_noise2(vec2(xt * 0.045 + seed * 3.0, 0.0)) + 2.5 * art_noise2(vec2(xt * 0.13 + seed, 5.0)));
    float trunkH = 3.0 + floor(art_hash11(ci * 9.1 + seed) * 3.0);
    float dx = fx - tx;
    float trunk = step(abs(dx), 0.0) * step(g.y, baseH + trunkH) * step(baseH, g.y);
    float cy = g.y - (baseH + trunkH);
    float crown = step(abs(dx), 2.0) * step(0.0, cy) * step(cy, 2.0);
    crown = max(crown, step(abs(dx), 1.0) * step(cy, 3.0) * step(0.0, cy));
    return max(solid, hasTree * max(trunk, crown));
}

// The color of a sky (rgb) and how much it covers Minecraft's own (a).
// px: the angle one screen pixel covers; detail: 0 (low) .. 2 (high).
vec4 artlyrics_sky(vec3 dir, int sky, float t, float px, int detail, vec2 fragCoord) {
    if (sky <= 0) {
        return vec4(0.0);
    }
    vec3 d = normalize(dir);
    px = max(px, 1e-5);
    art_jitter = art_hash12(fragCoord + fract(t) * 37.0);
    vec3 c;
    if (sky == 1) c = art_skyMilkyWay(d, t, px, detail);
    else if (sky == 2) c = art_skyPlanet(d, t, px, detail);
    else if (sky == 3) c = art_skyGalaxy(d, t, px, detail);
    else if (sky == 4) c = art_skyAurora(d, t, px, detail);
    else if (sky == 5) c = art_skyEclipse(d, t, px, detail);
    else if (sky == 6) c = art_skyBlackHole(d, t, px, detail);
    else if (sky == 7) c = art_skyNebula(d, t, px, detail);
    else if (sky == 8) c = art_skyTwinMoons(d, t, px, detail);
    else if (sky == 9) c = art_skyMoonHalo(d, t, px, detail);
    else if (sky == 10) c = art_skyMeteors(d, t, px, detail);
    else if (sky == 11) c = art_skyComet(d, t, px, detail);
    else if (sky == 12) c = art_skyPillars(d, t, px, detail);
    else if (sky == 13) c = art_skyRift(d, t, px, detail);
    else if (sky == 14) c = art_skyLanterns(d, t, px, detail);
    else if (sky == 15) c = art_skySakura(d, t, px, detail);
    else if (sky == 16) c = art_skyGolden(d, t, px, detail);
    else c = art_skySynth(d, t, px, detail);
    c = art_finish(c);
    c += vec3((art_hash12(fragCoord * 1.37 + 11.0) - 0.5) / 255.0);
    return vec4(c, 1.0);
}
