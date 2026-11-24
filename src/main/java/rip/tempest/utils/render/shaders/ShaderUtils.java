package rip.tempest.utils.render.shaders;

import rip.tempest.utils.Util;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;

import java.io.*;

import static org.lwjgl.opengl.GL20.*;

public class ShaderUtils extends Util {
    private final int programID;

    public ShaderUtils(String fragmentShaderLoc, String vertexShaderLoc) {
        int program = glCreateProgram();
        try {
            int fragmentShaderID = switch (fragmentShaderLoc) {
                case "shadow" -> createShader(new ByteArrayInputStream(shadow.getBytes()), GL_FRAGMENT_SHADER);
                case "roundRectTexture" ->
                        createShader(new ByteArrayInputStream(roundRectTexture.getBytes()), GL_FRAGMENT_SHADER);
                case "roundRectOutline" ->
                        createShader(new ByteArrayInputStream(roundRectOutline.getBytes()), GL_FRAGMENT_SHADER);
                case "roundedRect" ->
                        createShader(new ByteArrayInputStream(roundedRect.getBytes()), GL_FRAGMENT_SHADER);
                case "roundedRectGradient" ->
                        createShader(new ByteArrayInputStream(roundedRectGradient.getBytes()), GL_FRAGMENT_SHADER);
                case "gradient" -> createShader(new ByteArrayInputStream(gradient.getBytes()), GL_FRAGMENT_SHADER);
                case "mainmenu" -> createShader(new ByteArrayInputStream(mainmenu.getBytes()), GL_FRAGMENT_SHADER);
                case "kawaseUp" -> createShader(new ByteArrayInputStream(kawaseUp.getBytes()), GL_FRAGMENT_SHADER);
                case "kawaseDown" -> createShader(new ByteArrayInputStream(kawaseDown.getBytes()), GL_FRAGMENT_SHADER);
                case "kawaseUpBloom" ->
                        createShader(new ByteArrayInputStream(kawaseUpBloom.getBytes()), GL_FRAGMENT_SHADER);
                case "kawaseDownBloom" ->
                        createShader(new ByteArrayInputStream(kawaseDownBloom.getBytes()), GL_FRAGMENT_SHADER);
                case "gaussianBlur" ->
                        createShader(new ByteArrayInputStream(gaussianBlur.getBytes()), GL_FRAGMENT_SHADER);
                case "cape" -> createShader(new ByteArrayInputStream(cape.getBytes()), GL_FRAGMENT_SHADER);
                case "outline" -> createShader(new ByteArrayInputStream(outline.getBytes()), GL_FRAGMENT_SHADER);
                case "glow" -> createShader(new ByteArrayInputStream(glow.getBytes()), GL_FRAGMENT_SHADER);
                case "roundedBlurPass" ->
                        createShader(new ByteArrayInputStream(roundedBlurPass.getBytes()), GL_FRAGMENT_SHADER);
                case "roundedBlur" ->
                        createShader(new ByteArrayInputStream(roundedBlur.getBytes()), GL_FRAGMENT_SHADER);
                case "roundedShadow" ->
                        createShader(new ByteArrayInputStream(roundedShadow.getBytes()), GL_FRAGMENT_SHADER);
                case "liquidGlass" ->
                        createShader(new ByteArrayInputStream(liquidGlass.getBytes()), GL_FRAGMENT_SHADER);
                case "liquidGlassShape" ->
                        createShader(new ByteArrayInputStream(liquidGlassShape.getBytes()), GL_FRAGMENT_SHADER);
                default ->
                        createShader(mc.getResourceManager().getResource(new ResourceLocation(fragmentShaderLoc)).getInputStream(), GL_FRAGMENT_SHADER);
            };
            glAttachShader(program, fragmentShaderID);

            int vertexShaderID = createShader(mc.getResourceManager().getResource(new ResourceLocation(vertexShaderLoc)).getInputStream(), GL_VERTEX_SHADER);
            glAttachShader(program, vertexShaderID);
        } catch (IOException e) {
            e.printStackTrace();
        }

        glLinkProgram(program);
        int status = glGetProgrami(program, GL_LINK_STATUS);

        if (status == 0) {
            throw new IllegalStateException("Shader failed to link!");
        }
        this.programID = program;
    }

    public ShaderUtils(String fragmentShaderLoc) {
        this(fragmentShaderLoc, "tempest/shaders/vertex.vsh");
    }

    public void init() {
        glUseProgram(programID);
    }

    public void unload() {
        glUseProgram(0);
    }

    private final java.util.HashMap<String, Integer> uniformLocations = new java.util.HashMap<>();

    public int getUniform(String name) {
        Integer loc = uniformLocations.get(name);
        if (loc == null) {
            loc = glGetUniformLocation(programID, name);
            uniformLocations.put(name, loc);
        }
        return loc;
    }

    public void setUniformf(String name, float... args) {
        int loc = getUniform(name);
        switch (args.length) {
            case 1:
                glUniform1f(loc, args[0]);
                break;
            case 2:
                glUniform2f(loc, args[0], args[1]);
                break;
            case 3:
                glUniform3f(loc, args[0], args[1], args[2]);
                break;
            case 4:
                glUniform4f(loc, args[0], args[1], args[2], args[3]);
                break;
        }
    }

    public void setUniformi(String name, int... args) {
        int loc = getUniform(name);
        if (args.length > 1) glUniform2i(loc, args[0], args[1]);
        else glUniform1i(loc, args[0]);
    }

    public static void drawQuads(float x, float y, float width, float height) {
        glBegin(GL_QUADS);
        glTexCoord2f(0, 0);
        glVertex2f(x, y);
        glTexCoord2f(0, 1);
        glVertex2f(x, y + height);
        glTexCoord2f(1, 1);
        glVertex2f(x + width, y + height);
        glTexCoord2f(1, 0);
        glVertex2f(x + width, y);
        glEnd();
    }

    public static void drawQuads() {
        ScaledResolution sr = new ScaledResolution(mc);
        float width = (float) sr.getScaledWidth_double();
        float height = (float) sr.getScaledHeight_double();
        glBegin(GL_QUADS);
        glTexCoord2f(0, 1);
        glVertex2f(0, 0);
        glTexCoord2f(0, 0);
        glVertex2f(0, height);
        glTexCoord2f(1, 0);
        glVertex2f(width, height);
        glTexCoord2f(1, 1);
        glVertex2f(width, 0);
        glEnd();
    }

    public static void drawQuads(float width, float height) {
        drawQuads(0.0f, 0.0f, width, height);
    }

    public static void drawFixedQuads() {
        ScaledResolution sr = new ScaledResolution(mc);
        drawQuads((float) (mc.displayWidth / sr.getScaleFactor()), (float) mc.displayHeight / sr.getScaleFactor());
    }

    private int createShader(InputStream inputStream, int shaderType) {
        int shader = glCreateShader(shaderType);
        glShaderSource(shader, readInputStream(inputStream));
        glCompileShader(shader);

        if (glGetShaderi(shader, GL_COMPILE_STATUS) == 0) {
            System.out.println(glGetShaderInfoLog(shader, 4096));
            throw new IllegalStateException(String.format("Shader (%s) failed to compile!", shaderType));
        }

        return shader;
    }

    public static String readInputStream(InputStream inputStream) {
        StringBuilder stringBuilder = new StringBuilder();

        try {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = bufferedReader.readLine()) != null)
                stringBuilder.append(line).append('\n');
        } catch (Exception e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }

    private final String shadow = """
            #version 120

            uniform sampler2D inTexture;
            uniform sampler2D textureToCheck;
            uniform vec2 texelSize;
            uniform vec2 direction;
            uniform float radius;
            uniform float weights[256];

            void main() {
                vec2 uv = gl_TexCoord[0].st;

                if (direction.y > 0.0 && texture2D(textureToCheck, uv).a != 0.0) {
                    discard;
                }

                float alpha = texture2D(inTexture, uv).a * weights[0];
                float weightSum = weights[0];

                for (int i = 1; i <= int(radius); ++i) {
                    vec2 offset = texelSize * direction * float(i);
                    float w = weights[i];

                    alpha += texture2D(inTexture, clamp(uv + offset, vec2(0.0), vec2(1.0))).a * w;
                    alpha += texture2D(inTexture, clamp(uv - offset, vec2(0.0), vec2(1.0))).a * w;
                    weightSum += 2.0 * w;
                }

                alpha = (alpha / weightSum) * 0.8;
                gl_FragColor = vec4(0.0, 0.0, 0.0, alpha);
            }
            """;

    private final String roundRectTexture = """
            #version 120

            uniform vec2 location, rectSize;
            uniform sampler2D textureIn;
            uniform float radius, alpha;

            float roundedBoxSDF(vec2 centerPos, vec2 size, float radius) {
                return length(max(abs(centerPos) -size, 0.)) - radius;
            }

            void main() {
                float distance = roundedBoxSDF((rectSize * .5) - (gl_TexCoord[0].st * rectSize), (rectSize * .5) - radius - 1., radius);
                float smoothedAlpha =  (1.0-smoothstep(0.0, 2.0, distance)) * alpha;
                gl_FragColor = vec4(texture2D(textureIn, gl_TexCoord[0].st).rgb, smoothedAlpha);
            }""";

    private final String roundRectOutline = """
            #version 120

            uniform vec2 location, rectSize;
            uniform vec4 color, outlineColor;
            uniform float radius, outlineThickness;

            float roundedSDF(vec2 centerPos, vec2 size, float radius) {
                return length(max(abs(centerPos) - size + radius, 0.0)) - radius;
            }

            void main() {
                float distance = roundedSDF(gl_FragCoord.xy - location - (rectSize * .5), (rectSize * .5) + (outlineThickness *.5) - 1.0, radius);

                float blendAmount = smoothstep(0., 2., abs(distance) - (outlineThickness * .5));

                vec4 insideColor = (distance < 0.) ? color : vec4(outlineColor.rgb,  0.0);
                gl_FragColor = mix(outlineColor, insideColor, blendAmount);
            }""";

    private final String roundedRectGradient = """
            #version 120

            uniform vec2 location, rectSize;
            uniform vec4 color1, color2, color3, color4;
            uniform float radius;

            #define NOISE .5/255.0

            float roundSDF(vec2 p, vec2 b, float r) {
                return length(max(abs(p) - b , 0.0)) - r;
            }

            vec4 createGradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4){
                vec4 color = mix(mix(color1, color2, coords.y), mix(color3, color4, coords.y), coords.x);
                //Dithering the color
                // from https://shader-tutorial.dev/advanced/color-banding-dithering/
                color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898, 78.233))) * 43758.5453));
                return color;
            }

            void main() {
                vec2 st = gl_TexCoord[0].st;
                vec2 halfSize = rectSize * .5;
               \s
               // use the bottom leftColor as the alpha
                float smoothedAlpha =  (1.0-smoothstep(0.0, 2., roundSDF(halfSize - (gl_TexCoord[0].st * rectSize), halfSize - radius - 1., radius)));
                vec4 gradient = createGradient(st, color1, color2, color3, color4);    gl_FragColor = vec4(gradient.rgb, gradient.a * smoothedAlpha);
            }""";

    private final String roundedRect = """
            #version 120

            uniform vec2 location, rectSize;
            uniform vec4 color;
            uniform float radius;
            uniform bool blur;

            float roundSDF(vec2 p, vec2 b, float r) {
                return length(max(abs(p) - b, 0.0)) - r;
            }

            void main() {
                vec2 rectHalf = rectSize * 0.5;
                gl_FragColor = vec4(color.rgb, (1.0-smoothstep(0.0, 1.0, roundSDF(rectHalf - (gl_TexCoord[0].st * rectSize), rectHalf - radius - 1.0, radius))) * color.a);
            }""";
    private final String kawaseUpBloom = """
            #version 120

            uniform sampler2D inTexture, textureToCheck;
            uniform vec2 halfpixel, offset, iResolution;
            uniform int check;

            void main() {
              //  if(check && texture2D(textureToCheck, gl_TexCoord[0].st).a > 0.0) discard;
                vec2 uv = vec2(gl_FragCoord.xy / iResolution);

                vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);
                sum.rgb *= sum.a;
                vec4 smpl1 =  texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset);
                smpl1.rgb *= smpl1.a;
                sum += smpl1 * 2.0;
                vec4 smp2 = texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);
                smp2.rgb *= smp2.a;
                sum += smp2;
                vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset);
                smp3.rgb *= smp3.a;
                sum += smp3 * 2.0;
                vec4 smp4 = texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);
                smp4.rgb *= smp4.a;
                sum += smp4;
                vec4 smp5 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);
                smp5.rgb *= smp5.a;
                sum += smp5 * 2.0;
                vec4 smp6 = texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);
                smp6.rgb *= smp6.a;
                sum += smp6;
                vec4 smp7 = texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset);
                smp7.rgb *= smp7.a;
                sum += smp7 * 2.0;
                vec4 result = sum / 12.0;
                gl_FragColor = vec4(result.a > 0.001 ? result.rgb / result.a : vec3(0.0), mix(result.a, result.a * (1.0 - texture2D(textureToCheck, gl_TexCoord[0].st).a), check));
            }""";

    private final String kawaseDownBloom = """
            #version 120

            uniform sampler2D inTexture;
            uniform vec2 offset, halfpixel, iResolution;

            void main() {
                vec2 uv = vec2(gl_FragCoord.xy / iResolution);
                vec4 sum = texture2D(inTexture, gl_TexCoord[0].st);
                sum.rgb *= sum.a;
                sum *= 4.0;
                vec4 smp1 = texture2D(inTexture, uv - halfpixel.xy * offset);
                smp1.rgb *= smp1.a;
                sum += smp1;
                vec4 smp2 = texture2D(inTexture, uv + halfpixel.xy * offset);
                smp2.rgb *= smp2.a;
                sum += smp2;
                vec4 smp3 = texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);
                smp3.rgb *= smp3.a;
                sum += smp3;
                vec4 smp4 = texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);
                smp4.rgb *= smp4.a;
                sum += smp4;
                vec4 result = sum / 8.0;
                gl_FragColor = vec4(result.a > 0.001 ? result.rgb / result.a : vec3(0.0), result.a);
            }""";

    private final String kawaseUp = """
            #version 120

            uniform sampler2D inTexture, textureToCheck;
            uniform vec2 halfpixel, offset, iResolution;
            uniform int check;

            void main() {
                vec2 uv = vec2(gl_FragCoord.xy / iResolution);
                vec4 sum = texture2D(inTexture, uv + vec2(-halfpixel.x * 2.0, 0.0) * offset);
                sum += texture2D(inTexture, uv + vec2(-halfpixel.x, halfpixel.y) * offset) * 2.0;
                sum += texture2D(inTexture, uv + vec2(0.0, halfpixel.y * 2.0) * offset);
                sum += texture2D(inTexture, uv + vec2(halfpixel.x, halfpixel.y) * offset) * 2.0;
                sum += texture2D(inTexture, uv + vec2(halfpixel.x * 2.0, 0.0) * offset);
                sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset) * 2.0;
                sum += texture2D(inTexture, uv + vec2(0.0, -halfpixel.y * 2.0) * offset);
                sum += texture2D(inTexture, uv + vec2(-halfpixel.x, -halfpixel.y) * offset) * 2.0;

                gl_FragColor = vec4(sum.rgb /12.0, mix(1.0, texture2D(textureToCheck, gl_TexCoord[0].st).a, check));
            }
            """;

    private final String kawaseDown = """
            #version 120

            uniform sampler2D inTexture;
            uniform vec2 offset, halfpixel, iResolution;

            void main() {
                vec2 uv = vec2(gl_FragCoord.xy / iResolution);
                vec4 sum = texture2D(inTexture, gl_TexCoord[0].st) * 4.0;
                sum += texture2D(inTexture, uv - halfpixel.xy * offset);
                sum += texture2D(inTexture, uv + halfpixel.xy * offset);
                sum += texture2D(inTexture, uv + vec2(halfpixel.x, -halfpixel.y) * offset);
                sum += texture2D(inTexture, uv - vec2(halfpixel.x, -halfpixel.y) * offset);
                gl_FragColor = vec4(sum.rgb * .125, 1.0);
            }
            """;

    private final String gradient = """
            #version 120

            uniform vec2 location, rectSize;
            uniform sampler2D tex;
            uniform vec4 color1, color2, color3, color4;

            #define NOISE .5/255.0

            vec3 createGradient(vec2 coords, vec4 color1, vec4 color2, vec4 color3, vec4 color4){
                vec3 color = mix(mix(color1.rgb, color2.rgb, coords.y), mix(color3.rgb, color4.rgb, coords.y), coords.x);
                //Dithering the color from https://shader-tutorial.dev/advanced/color-banding-dithering/
                color += mix(NOISE, -NOISE, fract(sin(dot(coords.xy, vec2(12.9898,78.233))) * 43758.5453));
                return color;
            }
            void main() {
                vec2 coords = (gl_FragCoord.xy - location) / rectSize;
                float texColorAlpha = texture2D(tex, gl_TexCoord[0].st).a;
                gl_FragColor = vec4(createGradient(coords, color1, color2, color3, color4).rgb, texColorAlpha);
            }""";

    private final String mainmenu = """
            uniform float TIME;
            uniform vec2 RESOLUTION;

            #define NUM_OCTAVES 6

            mat3 rotX(float a) {
                float c = cos(a);
                float s = sin(a);
                return mat3(
                    1, 0, 0,
                    0, c, -s,
                    0, s, c
                );
            }

            mat3 rotY(float a) {
                float c = cos(a);
                float s = sin(a);
                return mat3(
                    c, 0, -s,
                    0, 1, 0,
                    s, 0, c
                );
            }

            float random(vec2 pos) {
                return fract(sin(dot(pos.xy, vec2(12.9898, 78.233))) * 43758.5453123);
            }

            float noise(vec2 pos) {
                vec2 i = floor(pos);
                vec2 f = fract(pos);
                float a = random(i + vec2(0.0, 0.0));
                float b = random(i + vec2(1.0, 0.0));
                float c = random(i + vec2(0.0, 1.0));
                float d = random(i + vec2(1.0, 1.0));
                vec2 u = f * f * (3.0 - 2.0 * f);
                return mix(a, b, u.x) + (c - a) * u.y * (1.0 - u.x) + (d - b) * u.x * u.y;
            }

            float fbm(vec2 pos) {
                float v = 0.0;
                float a = 0.5;
                vec2 shift = vec2(100.0);
                mat2 rot = mat2(cos(0.5), sin(0.5), -sin(0.5), cos(0.5));
                for (int i = 0; i < NUM_OCTAVES; i++) {
                    float dir = mod(float(i), 2.0) > 0.5 ? 1.0 : -1.0;
                    v += a * noise(pos - 0.05 * dir * TIME);

                    pos = rot * pos * 2.0 + shift;
                    a *= 0.5;
                }
                return v;
            }

            vec3 render(in vec2 fragCoord) {
                vec2 p = (fragCoord * 2.0 - RESOLUTION.xy) / min(RESOLUTION.x, RESOLUTION.y);
                p -= vec2(12.0, 0.0);

                float time2 = 1.0;
                vec2 q = vec2(0.0);
                q.x = fbm(p + 0.00 * time2);
                q.y = fbm(p + vec2(1.0));
                vec2 r = vec2(0.0);
                r.x = fbm(p + 1.0 * q + vec2(1.7, 9.2) + 0.15 * time2);
                r.y = fbm(p + 1.0 * q + vec2(8.3, 2.8) + 0.126 * time2);
                float f = fbm(p + r);

                vec3 color = mix(
                    vec3(0.3, 0.3, 0.6),
                    vec3(0.7, 0.7, 0.7),
                    clamp((f * f) * 4.0, 0.0, 1.0)
                );

                color = mix(
                    color,
                    vec3(0.7, 0.7, 0.7),
                    clamp(length(q), 0.0, 1.0)
                );

                color = mix(
                    color,
                    vec3(0.4, 0.4, 0.4),
                    clamp(length(r.x), 0.0, 1.0)
                );

                color = (f * f * f + 0.9 * f * f + 0.8 * f) * color;

                return color * 0.5;
            }

            void mainImage(out vec4 fragColor, in vec2 fragCoord) {
                vec3 color = render(fragCoord);
                fragColor = vec4(color, color.r);
            }

            void main(void) {
                mainImage(gl_FragColor, gl_FragCoord.xy);
            }
            """;

    private final String gaussianBlur = """
        #version 120
        uniform sampler2D textureIn;
        uniform vec2 texelSize, direction;
        uniform float radius;
        uniform float weights[256];
        #define offset texelSize * direction
            void main() {
                vec4 center = texture2D(textureIn, gl_TexCoord[0].st);
                vec3 blr = center.rgb * weights[0];
                float alpha = center.a * weights[0];
                for (float f = 1.0; f <= radius; f++) {
                    vec4 s1 = texture2D(textureIn, gl_TexCoord[0].st + f * offset);
                    vec4 s2 = texture2D(textureIn, gl_TexCoord[0].st - f * offset);
                    blr += s1.rgb * weights[int(abs(f))];
                    blr += s2.rgb * weights[int(abs(f))];
                    alpha += s1.a * weights[int(abs(f))];
                    alpha += s2.a * weights[int(abs(f))];
                }
                gl_FragColor = vec4(blr, alpha);
            }

        """;

    private final String cape = """
            #extension GL_OES_standard_derivatives : enable

            #ifdef GL_ES
            precision highp float;
            #endif

            uniform float time;
            uniform vec2  resolution;
            uniform float zoom;

            #define PI 3.1415926535

            mat2 rotate3d(float angle)
            {
                return mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
            }

            void main()
            {
                vec2 p = (gl_FragCoord.xy * 2.0 - resolution) / min(resolution.x, resolution.y);
                p = rotate3d((time * 2.0) * PI) * p;
                float t;
                if (sin(time) == 10.0)
                    t = 0.075 / abs(1.0 - length(p));
                else
                    t = 0.075 / abs(0.4/*sin(time)*/ - length(p));
                gl_FragColor = vec4(     ( 1. -exp( -vec3(t)  * vec3(0.13*(sin(time)+12.0), p.y*0.7, 3.0) )) , 1.0);
            }""";

    private final String glow = """
            #version 120

            uniform sampler2D textureIn, textureToCheck;
            uniform vec2 texelSize, direction;
            uniform vec3 color;
            uniform bool avoidTexture;
            uniform float exposure, radius;
            uniform float weights[256];

            #define offset direction * texelSize

            void main() {
                if (direction.y == 1 && avoidTexture) {
                    if (texture2D(textureToCheck, gl_TexCoord[0].st).a != 0.0) discard;
                }

                float innerAlpha = texture2D(textureIn, gl_TexCoord[0].st).a * weights[0];

                for (float r = 1.0; r <= radius; r ++) {
                    innerAlpha += texture2D(textureIn, gl_TexCoord[0].st + offset * r).a * weights[int(r)];
                    innerAlpha += texture2D(textureIn, gl_TexCoord[0].st - offset * r).a * weights[int(r)];
                }

                gl_FragColor = vec4(color, mix(innerAlpha, 1.0 - exp(-innerAlpha * exposure), step(0.0, direction.y)));
            }
            """;

    private final String outline = """
            #version 120

            uniform vec2 texelSize, direction;
            uniform sampler2D texture;
            uniform float radius;
            uniform vec3 color;

            #define offset direction * texelSize

            void main() {
                float centerAlpha = texture2D(texture, gl_TexCoord[0].xy).a;
                float innerAlpha = centerAlpha;
                for (float r = 1.0; r <= radius; r++) {
                    float alphaCurrent1 = texture2D(texture, gl_TexCoord[0].xy + offset * r).a;
                    float alphaCurrent2 = texture2D(texture, gl_TexCoord[0].xy - offset * r).a;

                    innerAlpha += alphaCurrent1 + alphaCurrent2;
                }

                gl_FragColor = vec4(color, innerAlpha) * step(0.0, -centerAlpha);
            }

            """;

    private final String roundedBlurPass = """
            #version 120

            uniform sampler2D textureIn;
            uniform vec2 texelSize, direction;
            uniform float radius;

            float gaussian(float x, float sigma) {
                return exp(-(x * x) / (2.0 * sigma * sigma));
            }

            void main() {
                vec2 uv = gl_TexCoord[0].st;
                vec2 lo = texelSize * 0.5;
                vec2 hi = vec2(1.0) - lo;
                float sigma = max(radius * 0.5, 0.001);

                float weightSum = gaussian(0.0, sigma);
                vec3 color = texture2D(textureIn, clamp(uv, lo, hi)).rgb * weightSum;

                for (int i = 1; i <= 24; i++) {
                    if (float(i) > radius) break;

                    float weight = gaussian(float(i), sigma);
                    vec2 offset = direction * texelSize * float(i);
                    color += texture2D(textureIn, clamp(uv + offset, lo, hi)).rgb * weight;
                    color += texture2D(textureIn, clamp(uv - offset, lo, hi)).rgb * weight;
                    weightSum += weight * 2.0;
                }

                gl_FragColor = vec4(color / weightSum, 1.0);
            }
            """;

    private final String roundedBlur = """
            #version 120

            uniform sampler2D textureIn;
            uniform vec2 screenSize, blurTexelSize, quadSize, boxHalfSize;
            uniform float radius, alpha;

            float roundedBoxSdf(vec2 p, vec2 b, float r) {
                vec2 q = abs(p) - b + vec2(r);
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            void main() {
                vec2 point = gl_TexCoord[0].st * quadSize - quadSize * 0.5;
                float mask = 1.0 - smoothstep(-1.0, 1.0, roundedBoxSdf(point, boxHalfSize, radius));
                if (mask <= 0.001) discard;

                vec2 lo = blurTexelSize * 0.5;
                vec3 color = texture2D(textureIn, clamp(gl_FragCoord.xy / screenSize, lo, vec2(1.0) - lo)).rgb;
                gl_FragColor = vec4(color, mask * alpha);
            }
            """;

    private final String roundedShadow = """
            #version 120

            uniform vec2 quadSize, boxHalfSize;
            uniform float radius, softness;
            uniform vec4 color;

            float roundedBoxSdf(vec2 p, vec2 b, float r) {
                vec2 q = abs(p) - b + vec2(r);
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            void main() {
                vec2 point = gl_TexCoord[0].st * quadSize - quadSize * 0.5;
                float falloff = 1.0 - smoothstep(-softness, softness, roundedBoxSdf(point, boxHalfSize, radius));
                if (falloff <= 0.001) discard;

                gl_FragColor = vec4(color.rgb, color.a * falloff);
            }
            """;

    private final String liquidGlass = """
            #version 120

            uniform sampler2D sharpTex, blurTex;
            uniform vec2 screenSize, quadSize, boxHalfSize;
            uniform float radius, alpha, scale, frost;
            uniform vec4 tint;

            float roundedBoxSdf(vec2 p, vec2 b, float r) {
                vec2 q = abs(p) - b + vec2(r);
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            void main() {
                vec2 p = gl_TexCoord[0].st * quadSize - quadSize * 0.5;
                float d = roundedBoxSdf(p, boxHalfSize, radius);
                float mask = 1.0 - smoothstep(-1.0, 1.0, d);
                if (mask <= 0.001) discard;

                // the outward normal, from the field's slope (box space has y down, the screen y up)
                vec2 n = vec2(roundedBoxSdf(p + vec2(1.0, 0.0), boxHalfSize, radius) - roundedBoxSdf(p - vec2(1.0, 0.0), boxHalfSize, radius),
                              roundedBoxSdf(p + vec2(0.0, 1.0), boxHalfSize, radius) - roundedBoxSdf(p - vec2(0.0, 1.0), boxHalfSize, radius));
                n = length(n) > 0.0001 ? normalize(n) : vec2(0.0);
                vec2 screenN = vec2(n.x, -n.y);

                float bevel = max(1.0, min(min(boxHalfSize.x, boxHalfSize.y), 9.0 * scale));
                float depth = clamp(-d / bevel, 0.0, 1.0);
                float bend = (1.0 - depth) * (1.0 - depth);
                vec2 off = screenN * bend * bevel * 0.85;

                vec2 lo = vec2(0.5) / screenSize, hi = vec2(1.0) - lo;
                vec2 at = gl_FragCoord.xy;
                vec3 sharp = vec3(texture2D(sharpTex, clamp((at + off * 1.15) / screenSize, lo, hi)).r,
                                  texture2D(sharpTex, clamp((at + off) / screenSize, lo, hi)).g,
                                  texture2D(sharpTex, clamp((at + off * 0.85) / screenSize, lo, hi)).b);
                vec3 soft = texture2D(blurTex, clamp((at + off) / screenSize, lo, hi)).rgb;
                vec3 color = mix(mix(sharp, soft, frost), tint.rgb, tint.a) + 0.025;

                vec2 light = normalize(vec2(-0.6, -0.8));
                float facing = dot(n, light);
                float rim = 1.0 - smoothstep(0.0, 1.6 * scale, -d);
                float spec = rim * (max(facing, 0.0) * 0.8 + max(-facing, 0.0) * 0.3);
                float glow = (1.0 - smoothstep(0.0, 4.0 * scale, -d)) * max(facing, 0.0) * 0.12;
                float v = clamp((p.y + boxHalfSize.y) / (2.0 * boxHalfSize.y), 0.0, 1.0);
                float sheen = (1.0 - smoothstep(0.0, 0.55, v)) * 0.08;
                float shade = bend * smoothstep(0.55, 1.0, v) * 0.14;
                color += vec3(spec + glow + sheen) - vec3(shade);

                gl_FragColor = vec4(clamp(color, 0.0, 1.0), mask * alpha);
            }
            """;

    private final String liquidGlassShape = """
            #version 120

            uniform sampler2D sharpTex, blurTex, shapeTex;
            uniform vec2 screenSize, shapeTexel;
            uniform float alpha, scale, frost, bevel;
            uniform vec4 tint;

            void main() {
                vec2 at = gl_FragCoord.xy;
                vec2 uv = at / screenSize;
                float h = texture2D(shapeTex, uv).r;
                float inside = smoothstep(0.45, 0.55, h);
                float lifted = texture2D(shapeTex, uv + vec2(0.0, 1.5 * scale) / screenSize).r;
                float shadow = smoothstep(0.05, 0.6, lifted) * 0.32 * (1.0 - inside);
                if (inside <= 0.001 && shadow <= 0.001) discard;

                // the slope of the height, screen space (y up); outward is downhill
                vec2 grad = vec2(texture2D(shapeTex, uv + vec2(shapeTexel.x, 0.0)).r - texture2D(shapeTex, uv - vec2(shapeTexel.x, 0.0)).r,
                                 texture2D(shapeTex, uv + vec2(0.0, shapeTexel.y)).r - texture2D(shapeTex, uv - vec2(0.0, shapeTexel.y)).r);
                vec2 outward = length(grad) > 0.00001 ? -normalize(grad) : vec2(0.0);
                float edge = 1.0 - clamp((h - 0.5) * 2.0, 0.0, 1.0);
                float bend = edge * edge;
                vec2 off = outward * bend * bevel;

                vec2 lo = vec2(0.5) / screenSize, hi = vec2(1.0) - lo;
                vec3 sharp = vec3(texture2D(sharpTex, clamp((at + off * 1.15) / screenSize, lo, hi)).r,
                                  texture2D(sharpTex, clamp((at + off) / screenSize, lo, hi)).g,
                                  texture2D(sharpTex, clamp((at + off * 0.85) / screenSize, lo, hi)).b);
                vec3 soft = texture2D(blurTex, clamp((at + off) / screenSize, lo, hi)).rgb;
                vec3 color = mix(mix(sharp, soft, frost), tint.rgb, tint.a) + 0.025;

                // light from the top left (screen y is up)
                vec2 light = normalize(vec2(-0.6, 0.8));
                float facing = dot(outward, light);
                float rim = 1.0 - smoothstep(0.0, 0.1, h - 0.5);
                float spec = rim * (max(facing, 0.0) * 0.8 + max(-facing, 0.0) * 0.3);
                float glow = bend * edge * max(facing, 0.0) * 0.14;
                float shade = bend * max(-facing, 0.0) * 0.12;
                color = clamp(color + vec3(spec + glow) - vec3(shade), 0.0, 1.0);

                float a = inside + shadow;
                gl_FragColor = vec4(color * inside / max(a, 0.0001), a * alpha);
            }
            """;
}
