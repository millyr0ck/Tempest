#version 120

// Ambience's Overlay: one pass over a copy of the finished world picture (before the HUD).
// Colour grade, chromatic aberration, light leak, vignette, film grain and letterbox bars,
// each off when its amount is 0. Coordinates come from gl_FragCoord, so the quad's texture
// coordinates don't matter.

uniform sampler2D scene;
uniform vec2 resolution;     // screen size in pixels
uniform vec2 texSize;        // size of the copy's texture, which can be bigger than the screen
uniform float time;          // seconds, wrapped by the caller

uniform int grade;           // 0 none, 1 film, 2 warm, 3 cool, 4 teal & orange, 5 faded, 6 mono
uniform float gradeAmount;   // 0..1
uniform float aberration;    // 0..1
uniform float leak;          // 0..1
uniform float vignette;      // 0..1
uniform float grain;         // 0..1
uniform float grainSize;     // pixels per grain
uniform float letterbox;     // bar height as a share of the screen height, each side

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 saturate3(vec3 c, float s) {
    return mix(vec3(luma(c)), c, s);
}

// Soft S-curve; amount 0 leaves it alone.
vec3 contrast(vec3 c, float amount) {
    vec3 s = c * c * (3.0 - 2.0 * c);
    return mix(c, s, amount);
}

vec4 sampleScene(vec2 screenUv) {
    return texture2D(scene, clamp(screenUv, 0.0, 1.0) * resolution / texSize);
}

float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

vec3 applyGrade(vec3 c) {
    float l = luma(c);
    if (grade == 1) {
        // film: gentle contrast, a little less colour, cool shadows and warm highlights
        c = contrast(c, 0.45);
        c = saturate3(c, 0.9);
        c += vec3(0.035, 0.012, -0.03) * l;
        c += vec3(-0.015, 0.005, 0.03) * (1.0 - l);
    } else if (grade == 2) {
        c *= vec3(1.08, 1.0, 0.86);
        c = contrast(c, 0.2);
    } else if (grade == 3) {
        c *= vec3(0.88, 0.99, 1.1);
        c = contrast(c, 0.2);
    } else if (grade == 4) {
        // teal shadows, orange highlights
        vec3 shadowTint = vec3(0.86, 0.98, 1.12);
        vec3 highTint = vec3(1.13, 0.97, 0.82);
        c *= mix(shadowTint, highTint, smoothstep(0.15, 0.75, l));
        c = contrast(c, 0.35);
        c = saturate3(c, 1.08);
    } else if (grade == 5) {
        // faded: lifted blacks, softer contrast, a little warmth
        c = c * 0.86 + vec3(0.075, 0.07, 0.06);
        c = saturate3(c, 0.78);
    } else if (grade == 6) {
        c = contrast(vec3(l), 0.5);
    }
    return clamp(c, 0.0, 1.0);
}

void main() {
    vec2 uv = gl_FragCoord.xy / resolution;
    vec2 centered = uv - 0.5;
    float aspect = resolution.x / resolution.y;

    // chromatic aberration: red and blue pulled apart towards the edges
    vec3 col;
    if (aberration > 0.0) {
        vec2 offset = centered * aberration * 0.012;
        col.r = sampleScene(uv + offset).r;
        col.g = sampleScene(uv).g;
        col.b = sampleScene(uv - offset).b;
    } else {
        col = sampleScene(uv).rgb;
    }

    if (grade != 0 && gradeAmount > 0.0) col = mix(col, applyGrade(col), gradeAmount);

    // light leak: two warm glows drifting slowly at the top corners, screen-blended
    if (leak > 0.0) {
        vec2 a = vec2(aspect, 1.0);
        vec2 p1 = vec2(-0.05 + 0.08 * sin(time * 0.11), 0.92 + 0.06 * cos(time * 0.07));
        vec2 p2 = vec2(1.05 + 0.06 * cos(time * 0.09), 0.8 + 0.08 * sin(time * 0.05));
        float d1 = length((uv - p1) * a), d2 = length((uv - p2) * a);
        vec3 glow = vec3(1.0, 0.45, 0.2) * exp(-d1 * d1 * 5.0) + vec3(1.0, 0.72, 0.4) * exp(-d2 * d2 * 7.0);
        glow *= leak * (0.85 + 0.15 * sin(time * 0.6));
        col = 1.0 - (1.0 - col) * (1.0 - clamp(glow, 0.0, 1.0));
    }

    // vignette
    if (vignette > 0.0) {
        float d = length(centered * vec2(aspect, 1.0)) / length(vec2(aspect, 1.0) * 0.5);
        col *= 1.0 - vignette * 0.75 * smoothstep(0.3, 1.05, d);
    }

    // film grain, new every frame, a touch stronger in the shadows
    if (grain > 0.0) {
        vec2 cell = floor(gl_FragCoord.xy / max(grainSize, 1.0));
        float seed = floor(time * 24.0);
        float n = hash(cell + vec2(seed * 17.0, seed * 31.0)) + hash(cell * 1.7 + vec2(seed * 7.0, 3.0)) - 1.0;
        col += n * grain * 0.16 * (1.15 - luma(col) * 0.6);
    }

    // letterbox bars, with a one pixel soft edge
    if (letterbox > 0.0) {
        float edge = 1.0 / resolution.y;
        float inside = smoothstep(letterbox - edge, letterbox, uv.y) * smoothstep(letterbox - edge, letterbox, 1.0 - uv.y);
        col *= inside;
    }

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
