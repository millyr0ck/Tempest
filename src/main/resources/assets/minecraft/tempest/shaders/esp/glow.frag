#version 120

// Glow around whatever was drawn into the entity framebuffer. Yuri's Shader ESP glow (AGPL-3.0,
// itself LiquidBounce's), kept in shape (every nearby silhouette sample adds a little alpha,
// less the further away it is) but changed to weigh each sample by how much of the pixel the
// entity covers, and to fade out at the body's edge by the same coverage, instead of yes/no
// tests. With the denser sampling GlowShader asks for, the halo and its edges come out smooth.

uniform sampler2D texture;
uniform vec2 texelSize; // spacing between samples

uniform vec3 color;

uniform float radius;
uniform float divider;
uniform float maxSample;

void main() {
    vec2 uv = gl_TexCoord[0].xy;
    float centre = texture2D(texture, uv).a;

    float alpha = 0.0;
    for (float x = -radius; x <= radius; x++) {
        for (float y = -radius; y <= radius; y++) {
            float coverage = texture2D(texture, uv + vec2(texelSize.x * x, texelSize.y * y)).a;
            alpha += coverage * max(0.0, (maxSample - distance(vec2(x, y), vec2(0.0))) / divider);
        }
    }

    gl_FragColor = vec4(color, min(alpha, 1.0) * (1.0 - centre));
}
