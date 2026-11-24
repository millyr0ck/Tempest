package rip.tempest.utils.render.knife;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class KnifeRenderer {
    private static final float FLASH_SIZE = 0.17f;

    private static final String VERTEX = "#version 120\n"
            + "varying vec3 vN; varying vec3 vP; varying vec2 vUv;\n"
            + "void main() {\n"
            + "  vec4 p = gl_ModelViewMatrix * gl_Vertex;\n"
            + "  vP = p.xyz; vN = gl_NormalMatrix * gl_Normal; vUv = gl_MultiTexCoord0.xy;\n"
            + "  gl_Position = gl_ProjectionMatrix * p;\n"
            + "}\n";

    private static final String FRAGMENT = "#version 120\n"
            + "varying vec3 vN; varying vec3 vP; varying vec2 vUv;\n"
            + "uniform sampler2D uTex; uniform sampler2D uExp; uniform sampler2D uMask;\n"
            + "uniform float uPhong, uHasExp, uBoost, uAlbedo, uExponent, uBright, uPaint, uFlash;\n"
            + "uniform vec3 uFresnel, uEnv, uLight, uUp, uPaintHilt, uPaintTip;\n"
            + "float spec(vec3 N, vec3 V, vec3 L, float e) { return pow(max(dot(reflect(-L, N), V), 0.0), e); }\n"
            + "void main() {\n"

            + "  if (uFlash > 0.0) {\n"
            + "    float r = length(vUv);\n"
            + "    float rays = pow(abs(cos(atan(vUv.y, vUv.x) * 3.0)), 8.0);\n"
            + "    float glow = pow(max(1.0 - r, 0.0), 2.2) + rays * pow(max(1.0 - r, 0.0), 1.1) * 0.8;\n"
            + "    gl_FragColor = vec4(vec3(1.0, 0.72, 0.36) * glow * uFlash * 1.05, 1.0);\n"
            + "    return;\n"
            + "  }\n"
            + "  vec4 c = texture2D(uTex, vUv);\n"
            + "  vec3 envTint = uEnv;\n"
            + "  if (uPaint > 0.5 && vUv.x < 6.0) {\n"

            + "    vec4 mask = texture2D(uMask, vUv);\n"
            + "    vec3 paint = mix(uPaintHilt, uPaintTip, mask.g);\n"
            + "    float shade = clamp(0.34 + dot(c.rgb, vec3(0.3, 0.59, 0.11)) * 0.62, 0.0, 0.72);\n"
            + "    c.rgb = mix(c.rgb, paint * shade, mask.r);\n"
            + "    envTint = mix(uEnv, uEnv * paint, mask.r);\n"
            + "  }\n"

            + "  if (vUv.x > 6.0) c = vec4(0.025, 0.04, 0.035, 1.0);\n"
            + "  vec3 V = normalize(-vP);\n"
            + "  vec3 N = normalize(vN); if (dot(N, V) < 0.0) N = -N;\n"
            + "  vec4 m = uHasExp > 0.5 ? texture2D(uExp, vUv) : vec4(uExponent / 149.0, 0.0, 0.0, 1.0);\n"
            + "  float sky = dot(N, uUp) * 0.5 + 0.5;\n"
            + "  vec3 ambient = mix(vec3(0.34, 0.32, 0.30), vec3(0.62, 0.64, 0.68), sky);\n"
            + "  vec3 fill = normalize(vec3(-0.25, 0.3, 1.0));\n"
            + "  vec3 diffuse = ambient + vec3(1.0, 0.97, 0.92) * 0.62 * max(dot(N, uLight), 0.0) + vec3(0.14) * max(dot(N, fill), 0.0);\n"
            + "  vec3 color = c.rgb * diffuse;\n"
            + "  if (uPhong > 0.5) {\n"
            + "    float e = max(m.r * 149.0 + 1.0, 8.0);\n"
            + "    float f = clamp(1.0 - dot(N, V), 0.0, 1.0); f *= f;\n"
            + "    float fres = f > 0.5 ? mix(uFresnel.y, uFresnel.z, 2.0 * f - 1.0) : mix(uFresnel.x, uFresnel.y, 2.0 * f);\n"
            + "    float peak = max(max(c.r, c.g), max(c.b, 0.001));\n"
            + "    vec3 tint = mix(vec3(1.0), c.rgb / peak, 0.75);\n"
            + "    float s = spec(N, V, uLight, e) * 0.75 + spec(N, V, fill, e) * 0.35;\n"
            + "    color += tint * c.a * min(uBoost, 9.0) * 0.2 * fres * s;\n"
            + "    vec3 R = reflect(-V, N);\n"
            + "    float t = dot(R, uUp);\n"
            + "    vec3 env = mix(vec3(0.16, 0.14, 0.12), mix(vec3(0.78, 0.74, 0.68), vec3(0.42, 0.56, 0.78), clamp(t * 2.0, 0.0, 1.0)), smoothstep(-0.08, 0.04, t));\n"
            + "    color += env * (envTint + c.a * 0.18 * tint) * mix(0.35, 1.0, f);\n"
            + "  }\n"
            + "  gl_FragColor = vec4(color * uBright, 1.0);\n"
            + "}\n";

    private int program = -1;
    private int uTex, uExp, uMask, uPhong, uHasExp, uBoost, uAlbedo, uExponent, uBright, uFresnel, uEnv, uLight, uUp, uPaint, uPaintHilt, uPaintTip, uFlash;

    private KnifeModel textureModel;
    private String textureSkin;
    private int diffuseTexture, exponentTexture;
    private KnifeModel maskModel;
    private int maskTexture;

    public void render(final KnifeModel model, final KnifeModel.Skin skin, final float[] pose, final float aspect, final float fov, final float[] offset,
                       final float brightness, final float[] light, final float[] up, final int armSkin, final boolean slim, final float[] paint,
                       final float flash, final float flashAngle) throws IOException {
        final int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int previousVbo = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        final int previousEbo = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        try {
            if (program == -1) compile();
            useTextures(model, skin);
            if (paint != null) useMask(model);
            model.build(pose, offset, armSkin != 0, slim);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            final double near = 0.05, far = 200.0;

            final double top = near * Math.tan(Math.toRadians(fov) * 0.5) * 0.75;
            GL11.glFrustum(-top * aspect, top * aspect, -top, top, near, far);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);

            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(true);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glColor4f(1f, 1f, 1f, 1f);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
            GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
            GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);

            GL20.glUseProgram(program);
            GL20.glUniform1i(uTex, 0);
            GL20.glUniform1i(uExp, 1);
            GL20.glUniform1i(uMask, 2);
            GL20.glUniform1f(uBright, brightness);
            GL20.glUniform3f(uLight, light[0], light[1], light[2]);
            GL20.glUniform3f(uUp, up[0], up[1], up[2]);
            GL20.glUniform1f(uFlash, 0f);
            GL20.glUniform1f(uPaint, paint != null ? 1f : 0f);
            if (paint != null) {
                GL20.glUniform3f(uPaintHilt, paint[0], paint[1], paint[2]);
                GL20.glUniform3f(uPaintTip, paint[3], paint[4], paint[5]);
                GL13.glActiveTexture(GL13.GL_TEXTURE2);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, maskTexture);
            }

            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, exponentTexture);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, diffuseTexture);
            GL20.glUniform1f(uPhong, 1f);
            GL20.glUniform1f(uHasExp, exponentTexture != 0 ? 1f : 0f);
            GL20.glUniform1f(uBoost, skin.boost);
            GL20.glUniform1f(uAlbedo, skin.albedoBoost);
            GL20.glUniform1f(uExponent, skin.exponent);
            GL20.glUniform3f(uFresnel, skin.fresnel[0], skin.fresnel[1], skin.fresnel[2]);
            GL20.glUniform3f(uEnv, skin.envTint[0], skin.envTint[1], skin.envTint[2]);
            pointers(model.knifeVerts);
            model.indices.position(0);
            GL11.glDrawElements(GL11.GL_TRIANGLES, model.indices);

            if (armSkin != 0 && model.armVertCount > 0) {
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, armSkin);
                GL20.glUniform1f(uPhong, 0f);
                GL20.glUniform1f(uPaint, 0f);
                pointers(model.armVerts);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, model.armVertCount);
            }

            final float[] muzzle = flash > 0f ? model.muzzleView(offset) : null;
            if (muzzle != null) drawFlash(muzzle, flash, flashAngle);

            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPopMatrix();
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        } finally {
            GL11.glPopClientAttrib();
            GL11.glPopAttrib();
            GL20.glUseProgram(previousProgram);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, previousVbo);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, previousEbo);
        }
    }

    private void drawFlash(final float[] at, final float flash, final float angle) {
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
        GL20.glUniform1f(uFlash, flash);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        GL11.glDepthMask(false);
        final float cos = (float) Math.cos(angle) * FLASH_SIZE, sin = (float) Math.sin(angle) * FLASH_SIZE;
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glNormal3f(0f, 0f, 1f);
        final int[][] corners = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
        for (final int[] corner : corners) {
            GL11.glTexCoord2f(corner[0], corner[1]);
            GL11.glVertex3f(at[0] + corner[0] * cos - corner[1] * sin, at[1] + corner[0] * sin + corner[1] * cos, at[2]);
        }
        GL11.glEnd();
    }

    private static void pointers(final ByteBuffer vertices) {
        vertices.position(0);
        GL11.glVertexPointer(3, GL11.GL_FLOAT, KnifeModel.STRIDE, vertices);
        vertices.position(12);
        GL11.glNormalPointer(GL11.GL_FLOAT, KnifeModel.STRIDE, vertices);
        vertices.position(24);
        GL11.glTexCoordPointer(2, GL11.GL_FLOAT, KnifeModel.STRIDE, vertices);
        vertices.position(0);
    }

    private void compile() throws IOException {
        final int vertex = shader(GL20.GL_VERTEX_SHADER, VERTEX), fragment = shader(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        final int linked = GL20.glCreateProgram();
        GL20.glAttachShader(linked, vertex);
        GL20.glAttachShader(linked, fragment);
        GL20.glLinkProgram(linked);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(linked, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IOException("Knife shader link: " + GL20.glGetProgramInfoLog(linked));
        }
        uTex = GL20.glGetUniformLocation(linked, "uTex");
        uExp = GL20.glGetUniformLocation(linked, "uExp");
        uMask = GL20.glGetUniformLocation(linked, "uMask");
        uPhong = GL20.glGetUniformLocation(linked, "uPhong");
        uHasExp = GL20.glGetUniformLocation(linked, "uHasExp");
        uBoost = GL20.glGetUniformLocation(linked, "uBoost");
        uAlbedo = GL20.glGetUniformLocation(linked, "uAlbedo");
        uExponent = GL20.glGetUniformLocation(linked, "uExponent");
        uBright = GL20.glGetUniformLocation(linked, "uBright");
        uFresnel = GL20.glGetUniformLocation(linked, "uFresnel");
        uEnv = GL20.glGetUniformLocation(linked, "uEnv");
        uLight = GL20.glGetUniformLocation(linked, "uLight");
        uUp = GL20.glGetUniformLocation(linked, "uUp");
        uPaint = GL20.glGetUniformLocation(linked, "uPaint");
        uPaintHilt = GL20.glGetUniformLocation(linked, "uPaintHilt");
        uPaintTip = GL20.glGetUniformLocation(linked, "uPaintTip");
        uFlash = GL20.glGetUniformLocation(linked, "uFlash");
        program = linked;
    }

    private static int shader(final int type, final String source) throws IOException {
        final int id = GL20.glCreateShader(type);
        GL20.glShaderSource(id, source);
        GL20.glCompileShader(id);
        if (GL20.glGetShaderi(id, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new IOException("Knife shader compile: " + GL20.glGetShaderInfoLog(id));
        }
        return id;
    }

    private void useTextures(final KnifeModel model, final KnifeModel.Skin skin) throws IOException {
        if (model == textureModel && skin.id.equals(textureSkin)) return;
        if (diffuseTexture != 0) GL11.glDeleteTextures(diffuseTexture);
        if (exponentTexture != 0) GL11.glDeleteTextures(exponentTexture);
        diffuseTexture = exponentTexture = 0;
        textureModel = null;
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        diffuseTexture = upload(new File(model.folder, skin.diffuse));
        if (!skin.exponentMap.isEmpty()) exponentTexture = upload(new File(model.folder, skin.exponentMap));
        textureModel = model;
        textureSkin = skin.id;
    }

    private void useMask(final KnifeModel model) throws IOException {
        if (model == maskModel) return;
        if (maskTexture != 0) GL11.glDeleteTextures(maskTexture);
        maskTexture = 0;
        maskModel = null;
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        maskTexture = upload(new File(model.folder, "mask.png"));
        maskModel = model;
    }

    private static int upload(final File file) throws IOException {
        final BufferedImage image = ImageIO.read(file);
        if (image == null) throw new IOException("Unreadable texture " + file);
        final int w = image.getWidth(), h = image.getHeight();
        final int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        final ByteBuffer pixels = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        for (final int p : argb) {
            pixels.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
        }
        pixels.flip();
        final int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        return id;
    }
}
