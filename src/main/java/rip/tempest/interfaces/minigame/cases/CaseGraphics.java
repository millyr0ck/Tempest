package rip.tempest.interfaces.minigame.cases;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

final class CaseGraphics {
    private static final String VERTEX = "#version 120\n"
            + "varying vec2 vUv;\nvarying vec4 vColor;\n"
            + "void main() { gl_Position = ftransform(); vUv = gl_MultiTexCoord0.xy; vColor = gl_Color; }\n";
    private static final String FRAGMENT = "#version 120\n"
            + "uniform sampler2D uTex;\nuniform float uUseTex;\nuniform vec4 uCircle;\nuniform vec4 uFade;\nuniform float uBright;\nuniform float uLod;\nuniform float uBlur;\n"
            + "varying vec2 vUv;\nvarying vec4 vColor;\n"
            + "void main() {\n"
            + "  vec4 c = vec4(vColor.rgb * vColor.a, vColor.a);\n"
            + "  if (uUseTex > 0.5) {\n"
            + "    vec4 t;\n"
            + "    if (uBlur > 0.0) {\n"
            + "      t = vec4(0.0);\n"
            + "      for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) t += texture2D(uTex, vUv + vec2(float(x), float(y)) * uBlur, uLod);\n"
            + "      t /= 9.0;\n"
            + "    } else t = texture2D(uTex, vUv, uLod);\n"
            + "    c = t * vec4(vColor.rgb, 1.0) * vColor.a;\n"
            + "  }\n"
            + "  float a = 1.0;\n"
            + "  if (uCircle.w > 0.5) {\n"
            + "    float e = smoothstep(uCircle.z - 1.2, uCircle.z + 1.2, distance(gl_FragCoord.xy, uCircle.xy));\n"
            + "    a *= uCircle.w < 1.5 ? 1.0 - e : e;\n"
            + "  }\n"
            + "  if (uFade.w > 0.5) a *= clamp((gl_FragCoord.x - uFade.x) / uFade.z, 0.0, 1.0) * clamp((uFade.y - gl_FragCoord.x) / uFade.z, 0.0, 1.0);\n"
            + "  gl_FragColor = vec4(c.rgb * uBright, c.a) * a;\n"
            + "}\n";

    private static final int TEXT_CACHE = 500, UPLOADS_PER_FRAME = 4;

    private static final class Decoded {
        final String key;
        final int width, height;
        final ByteBuffer pixels;

        Decoded(final String key, final int width, final int height, final ByteBuffer pixels) {
            this.key = key;
            this.width = width;
            this.height = height;
            this.pixels = pixels;
        }
    }

    static final class Text {
        final int texture, width, height, baseline;

        Text(final int texture, final int width, final int height, final int baseline) {
            this.texture = texture;
            this.width = width;
            this.height = height;
            this.baseline = baseline;
        }
    }

    private final Font regular, bold;
    private final Font fallback = new Font(Font.DIALOG, Font.PLAIN, 12);
    private final BufferedImage measureImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    private final Graphics2D measure = measureImage.createGraphics();

    private final ExecutorService loader = Executors.newSingleThreadExecutor(runnable -> {
        final Thread thread = new Thread(runnable, "Case Textures");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Integer> textures = new HashMap<>();
    private final ConcurrentLinkedQueue<Decoded> decoded = new ConcurrentLinkedQueue<>();
    private final AtomicInteger pending = new AtomicInteger();

    private final Map<String, Text> texts = new LinkedHashMap<>(64, .75f, true);

    private int program = -1, previousProgram;
    private int uTex, uUseTex, uCircle, uFade, uBright, uLod, uBlur;
    private int backdrop, backdropW, backdropH;
    private int radial;
    private boolean disposed;

    CaseGraphics(final Font regular, final Font bold) {
        this.regular = regular;
        this.bold = bold;
        measure.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        measure.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    void begin(final int pixelW, final int pixelH, final float width) {
        if (program < 0) build();
        previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glOrtho(0, width, 1080, 0, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPushMatrix();
        GL11.glLoadIdentity();
        GL11.glViewport(0, 0, pixelW, pixelH);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_FOG);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL20.glUseProgram(program);
        GL20.glUniform1i(uTex, 0);
        clearEffects();
        for (int i = 0; i < UPLOADS_PER_FRAME; i++) {
            final Decoded next = decoded.poll();
            if (next == null) break;
            upload(next);
        }
    }

    void end() {
        GL20.glUseProgram(previousProgram);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
        GL11.glPopAttrib();
    }

    void clearEffects() {
        GL20.glUniform4f(uCircle, 0, 0, 0, 0);
        GL20.glUniform4f(uFade, 0, 0, 1, 0);
        GL20.glUniform1f(uBright, 1);
        GL20.glUniform1f(uLod, 0);
        GL20.glUniform1f(uBlur, 0);
    }

    void circle(final float x, final float y, final float radius, final int mode) {
        GL20.glUniform4f(uCircle, x, y, radius, mode);
    }

    void fade(final float left, final float right, final float feather) {
        GL20.glUniform4f(uFade, left, right, feather, 1);
    }

    void noFade() {
        GL20.glUniform4f(uFade, 0, 0, 1, 0);
    }

    void brightness(final float value) {
        GL20.glUniform1f(uBright, value);
    }

    void blur(final float lod, final float spread) {
        GL20.glUniform1f(uLod, lod);
        GL20.glUniform1f(uBlur, spread);
    }

    void rect(final float x, final float y, final float w, final float h, final int color) {
        gradient(x, y, w, h, color, color);
    }

    void gradient(final float x, final float y, final float w, final float h, final int top, final int bottom) {
        GL20.glUniform1f(uUseTex, 0);
        GL11.glBegin(GL11.GL_QUADS);
        color(top);
        GL11.glVertex2f(x, y);
        color(bottom);
        GL11.glVertex2f(x, y + h);
        GL11.glVertex2f(x + w, y + h);
        color(top);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
    }

    void image(final int texture, final float x, final float y, final float w, final float h, final int tint) {
        if (texture == 0) return;
        GL20.glUniform1f(uUseTex, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        color(tint);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(x, y);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(x, y + h);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(x + w, y + h);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(x + w, y);
        GL11.glEnd();
    }

    void imageTurned(final int texture, final float centerX, final float centerY, final float w, final float h, final float yaw, final float roll, final int tint) {
        if (texture == 0) return;
        final float distance = Math.max(w, h) * 2.2f, cos = (float) Math.cos(yaw), sin = (float) Math.sin(yaw);
        GL20.glUniform1f(uUseTex, 1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        color(tint);
        GL11.glPushMatrix();
        GL11.glTranslatef(centerX, centerY, 0);
        GL11.glRotatef(roll, 0, 0, 1);
        GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        final int strips = 40;
        for (int i = 0; i <= strips; i++) {
            final float u = i / (float) strips, local = (u - .5f) * w;
            final float scale = distance / (distance + local * sin);
            final float x = local * cos * scale;
            GL11.glTexCoord2f(u, 0);
            GL11.glVertex2f(x, -h / 2 * scale);
            GL11.glTexCoord2f(u, 1);
            GL11.glVertex2f(x, h / 2 * scale);
        }
        GL11.glEnd();
        GL11.glPopMatrix();
    }

    int radial() {
        if (radial == 0) {
            final int size = 128;
            final ByteBuffer pixels = ByteBuffer.allocateDirect(size * size * 4);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    final double d = Math.hypot(x + .5 - size / 2.0, y + .5 - size / 2.0) / (size / 2.0);
                    final int a = (int) (255 * Math.max(0, 1 - d));
                    pixels.put((byte) a).put((byte) a).put((byte) a).put((byte) a);
                }
            }
            pixels.flip();
            radial = makeTexture(size, size, pixels, false);
        }
        return radial;
    }

    void blurBackdrop(final int pixelW, final int pixelH, final float width, final float lod, final int tint) {
        if (backdrop == 0) backdrop = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, backdrop);
        if (backdropW != pixelW || backdropH != pixelH) {
            GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB8, 0, 0, pixelW, pixelH, 0);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            backdropW = pixelW;
            backdropH = pixelH;
        } else {
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, pixelW, pixelH);
        }
        GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        blur(lod, lod <= 0 ? 0 : (float) (Math.pow(2, lod) * .75f / pixelW));
        GL20.glUniform1f(uUseTex, 1);
        color(tint);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 1);
        GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(0, 0);
        GL11.glVertex2f(0, 1080);
        GL11.glTexCoord2f(1, 0);
        GL11.glVertex2f(width, 1080);
        GL11.glTexCoord2f(1, 1);
        GL11.glVertex2f(width, 0);
        GL11.glEnd();
        blur(0, 0);
    }

    private static void color(final int argb) {
        GL11.glColor4f((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
    }

    float text(final String string, final float x, final float y, final float size, final boolean strong, final int color, final float scale) {
        if (string.isEmpty()) return 0;
        final Text text = makeText(string, size * scale, strong);
        final float w = text.width / scale, h = text.height / scale;
        image(text.texture, x, y, w, h, color);
        return w;
    }

    float textWidth(final String string, final float size, final boolean strong, final float scale) {
        return string.isEmpty() ? 0 : makeText(string, size * scale, strong).width / scale;
    }

    List<String> wrap(final String string, final float width, final float size, final float scale) {
        final List<String> lines = new ArrayList<>();
        for (final String paragraph : string.split("\n")) {
            final StringBuilder line = new StringBuilder();
            for (final String word : paragraph.split(" ")) {
                final String candidate = line.length() == 0 ? word : line + " " + word;
                if (line.length() > 0 && measureWidth(candidate, size * scale) / scale > width) {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    line.setLength(0);
                    line.append(candidate);
                }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private float measureWidth(final String string, final float pixels) {
        final Font font = regular.deriveFont(pixels);
        return measure.getFontMetrics(font).stringWidth(string);
    }

    private Text makeText(final String string, final float pixels, final boolean strong) {
        final int size = Math.max(6, Math.round(pixels));
        final String key = size + (strong ? "b" : "r") + string;
        Text text = texts.get(key);
        if (text != null) return text;

        final Font main = (strong ? bold : regular).deriveFont((float) size);
        final Font other = fallback.deriveFont((float) size);
        final AttributedString attributed = new AttributedString(string);
        attributed.addAttribute(TextAttribute.FONT, main);

        for (int i = 0; i < string.length(); i++) {
            if (main.canDisplay(string.charAt(i))) continue;
            attributed.addAttribute(TextAttribute.FONT, other, i, i + 1);
        }
        final FontRenderContext context = measure.getFontRenderContext();
        final TextLayout layout = new TextLayout(attributed.getIterator(), context);
        final FontMetrics metrics = measure.getFontMetrics(main);
        final int width = (int) Math.ceil(layout.getAdvance()) + 4, height = metrics.getHeight() + 2;
        final BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        final Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics.setColor(Color.WHITE);
        layout.draw(graphics, 1, metrics.getAscent());
        graphics.dispose();

        text = new Text(makeTexture(width, height, premultiplied(image), false), width, height, metrics.getAscent());
        texts.put(key, text);
        if (texts.size() > TEXT_CACHE) {
            final Iterator<Map.Entry<String, Text>> oldest = texts.entrySet().iterator();
            GL11.glDeleteTextures(oldest.next().getValue().texture);
            oldest.remove();
        }
        return text;
    }

    int texture(final File file) {
        final String key = file.getPath();
        final Integer known = textures.get(key);
        if (known != null) return known;
        textures.put(key, 0);
        pending.incrementAndGet();
        loader.execute(() -> {
            try {
                final BufferedImage image = ImageIO.read(file);
                if (image != null) decoded.add(new Decoded(key, image.getWidth(), image.getHeight(), premultiplied(image)));
                else System.err.println("Case Opening: could not read " + file);
            } catch (final Exception exception) {
                System.err.println("Case Opening: could not read " + file + ": " + exception);
            } finally {
                pending.decrementAndGet();
            }
        });
        return 0;
    }

    void finishLoading() throws InterruptedException {
        while (pending.get() > 0) Thread.sleep(5);
        Decoded next;
        while ((next = decoded.poll()) != null) upload(next);
    }

    private void upload(final Decoded image) {
        if (disposed) return;
        textures.put(image.key, makeTexture(image.width, image.height, image.pixels, true));
    }

    private static ByteBuffer premultiplied(final BufferedImage image) {
        final int w = image.getWidth(), h = image.getHeight();
        final int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        final ByteBuffer pixels = ByteBuffer.allocateDirect(w * h * 4);
        for (final int value : argb) {
            final int a = value >>> 24;
            pixels.put((byte) ((value >> 16 & 255) * a / 255));
            pixels.put((byte) ((value >> 8 & 255) * a / 255));
            pixels.put((byte) ((value & 255) * a / 255));
            pixels.put((byte) a);
        }
        pixels.flip();
        return pixels;
    }

    private static int makeTexture(final int w, final int h, final ByteBuffer pixels, final boolean mipmaps) {
        final int id = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
        final int rowLength = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH), skipRows = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS), skipPixels = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, rowLength);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipRows);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipPixels);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mipmaps ? GL11.GL_LINEAR_MIPMAP_LINEAR : GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        if (mipmaps) GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        return id;
    }

    private void build() {
        final int vertex = compile(GL20.GL_VERTEX_SHADER, VERTEX), fragment = compile(GL20.GL_FRAGMENT_SHADER, FRAGMENT);
        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) throw new IllegalStateException(GL20.glGetProgramInfoLog(program, 2000));
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        uTex = GL20.glGetUniformLocation(program, "uTex");
        uUseTex = GL20.glGetUniformLocation(program, "uUseTex");
        uCircle = GL20.glGetUniformLocation(program, "uCircle");
        uFade = GL20.glGetUniformLocation(program, "uFade");
        uBright = GL20.glGetUniformLocation(program, "uBright");
        uLod = GL20.glGetUniformLocation(program, "uLod");
        uBlur = GL20.glGetUniformLocation(program, "uBlur");
    }

    private static int compile(final int type, final String source) {
        final int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) throw new IllegalStateException(GL20.glGetShaderInfoLog(shader, 2000));
        return shader;
    }

    void dispose() {
        disposed = true;
        loader.shutdownNow();
        for (final int id : textures.values()) if (id != 0) GL11.glDeleteTextures(id);
        for (final Text text : texts.values()) GL11.glDeleteTextures(text.texture);
        if (backdrop != 0) GL11.glDeleteTextures(backdrop);
        if (radial != 0) GL11.glDeleteTextures(radial);
        if (program >= 0) GL20.glDeleteProgram(program);
        textures.clear();
        texts.clear();
        decoded.clear();
        program = -1;
    }
}
