#version 120

// Outline around whatever was drawn into the entity framebuffer. Started from Yuri's Shader ESP
// outline (AGPL-3.0, itself LiquidBounce's), which made a yes/no choice per pixel and so showed
// every stair-step of the silhouette. This version works with coverage instead: the framebuffer
// is twice the screen's resolution and sampled smoothly, so "texture.a" says how much of a
// pixel the entity covers. The inner edge follows that coverage and the outer edge fades over
// one pixel.

uniform sampler2D texture;
uniform vec2 texelSize; // one screen pixel
uniform vec4 color;
uniform float radius;   // outline width in screen pixels

void main(void) {
    vec2 uv = gl_TexCoord[0].xy;
    float centre = texture2D(texture, uv).a;

    // How strongly the silhouette reaches this pixel: full within the width, fading out over
    // the last pixel.
    float reach = 0.0;
    float span = ceil(radius + 0.5);
    for (float x = -span; x <= span; x++) {
        for (float y = -span; y <= span; y++) {
            float dist = length(vec2(x, y));
            if (dist > radius + 0.5) continue;

            float coverage = texture2D(texture, uv + vec2(x, y) * texelSize).a;
            reach = max(reach, coverage * (1.0 - smoothstep(radius - 0.5, radius + 0.5, dist)));
        }
    }

    // Only outside the body: fully covered pixels get nothing, partly covered ones the remainder.
    gl_FragColor = vec4(color.rgb, color.a * reach * (1.0 - centre));
}
