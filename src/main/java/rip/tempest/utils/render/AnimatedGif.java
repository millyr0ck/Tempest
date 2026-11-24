package rip.tempest.utils.render;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.lwjgl.opengl.GL11;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;

public final class AnimatedGif {
    private static final int MAX_SIDE = 160;
    private static final int MAX_FRAMES = 512;
    private static final int MAX_CANVAS = 4096;

    private static final int DEFAULT_DELAY = 100;

    private final String source;
    private volatile List<BufferedImage> decoded;
    private int[] delays = new int[0];
    private volatile boolean failed;
    private DynamicTexture[] textures;
    private int width, height;

    private AnimatedGif(String source) {
        this.source = source;
    }

    public static AnimatedGif load(String source, Callable<InputStream> opener) {
        AnimatedGif gif = new AnimatedGif(source);
        Thread thread = new Thread(() -> {
            try (InputStream input = opener.call()) {
                if (input == null) throw new IOException("Not found: " + source);
                List<Integer> delays = new ArrayList<>();
                List<BufferedImage> frames = decode(input, delays);
                gif.delays = delays.stream().mapToInt(Integer::intValue).toArray();
                gif.decoded = frames;
            } catch (Exception | OutOfMemoryError e) {
                gif.failed = true;
            }
        }, "gif-decoder");
        thread.setDaemon(true);
        thread.start();
        return gif;
    }

    public String source() {
        return source;
    }

    public boolean failed() {
        return failed;
    }

    public boolean ready() {
        if (textures != null) return true;
        List<BufferedImage> frames = decoded;
        if (frames == null) return false;

        textures = new DynamicTexture[frames.size()];
        for (int i = 0; i < textures.length; i++) {
            textures[i] = new DynamicTexture(frames.get(i));
        }
        width = frames.get(0).getWidth();
        height = frames.get(0).getHeight();
        decoded = null;
        return true;
    }

    public int frameCount() {
        return textures != null ? textures.length : 0;
    }

    public int width() {
        return width;
    }

    public int delay(int frame) {
        int[] delays = this.delays;
        return delays.length == 0 ? DEFAULT_DELAY : delays[Math.floorMod(frame, delays.length)];
    }

    public int height() {
        return height;
    }

    public void draw(int frame, float x, float y, float boxWidth, float boxHeight) {
        if (!ready() || textures.length == 0) return;

        GlStateManager.bindTexture(textures[Math.floorMod(frame, textures.length)].getGlTextureId());
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1, 1, 1, 1);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0f, 0f, boxWidth, boxHeight, boxWidth, boxHeight);
    }

    public void delete() {
        if (textures != null) {
            for (DynamicTexture texture : textures) texture.deleteGlTexture();
            textures = null;
        }
        decoded = null;
    }

    private static List<BufferedImage> decode(InputStream input, List<Integer> delays) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) throw new IOException("No GIF reader");
        ImageReader reader = readers.next();

        try (ImageInputStream stream = ImageIO.createImageInputStream(input)) {
            reader.setInput(stream, false);

            int canvasWidth = 0, canvasHeight = 0;
            IIOMetadata streamMeta = reader.getStreamMetadata();
            if (streamMeta != null) {
                Node screen = child(streamMeta.getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
                if (screen != null) {
                    canvasWidth = intAttribute(screen, "logicalScreenWidth", 0);
                    canvasHeight = intAttribute(screen, "logicalScreenHeight", 0);
                }
            }

            List<BufferedImage> frames = new ArrayList<>();
            BufferedImage canvas = null;
            for (int i = 0; i < MAX_FRAMES; i++) {
                BufferedImage frame;
                try {
                    frame = reader.read(i);
                } catch (IndexOutOfBoundsException end) {
                    break;
                }

                Node root = reader.getImageMetadata(i).getAsTree("javax_imageio_gif_image_1.0");
                Node descriptor = child(root, "ImageDescriptor");
                Node control = child(root, "GraphicControlExtension");
                int left = descriptor != null ? intAttribute(descriptor, "imageLeftPosition", 0) : 0;
                int top = descriptor != null ? intAttribute(descriptor, "imageTopPosition", 0) : 0;
                String disposal = control != null ? attribute(control, "disposalMethod") : "none";
                int hundredths = control != null ? intAttribute(control, "delayTime", 0) : 0;
                delays.add(hundredths <= 1 ? DEFAULT_DELAY : hundredths * 10);

                if (canvas == null) {
                    if (canvasWidth <= 0 || canvasHeight <= 0) {
                        canvasWidth = left + frame.getWidth();
                        canvasHeight = top + frame.getHeight();
                    }
                    if (canvasWidth > MAX_CANVAS || canvasHeight > MAX_CANVAS) throw new IOException("GIF too large");
                    canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
                }

                BufferedImage before = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
                Graphics2D g = canvas.createGraphics();
                g.drawImage(frame, left, top, null);
                g.dispose();

                frames.add(shrink(canvas));

                if ("restoreToBackgroundColor".equals(disposal)) {
                    Graphics2D clear = canvas.createGraphics();
                    clear.setComposite(AlphaComposite.Clear);
                    clear.fillRect(left, top, frame.getWidth(), frame.getHeight());
                    clear.dispose();
                } else if (before != null) {
                    canvas = before;
                }
            }

            if (frames.isEmpty()) throw new IOException("No frames");
            return frames;
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage shrink(BufferedImage image) {
        BufferedImage current = copy(image);
        while (Math.max(current.getWidth(), current.getHeight()) > MAX_SIDE) {
            double factor = Math.max(0.5, MAX_SIDE / (double) Math.max(current.getWidth(), current.getHeight()));
            int w = Math.max(1, (int) Math.round(current.getWidth() * factor));
            int h = Math.max(1, (int) Math.round(current.getHeight() * factor));
            BufferedImage smaller = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = smaller.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(current, 0, 0, w, h, null);
            g.dispose();
            current = smaller;
        }
        return current;
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.setComposite(AlphaComposite.Src);
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static Node child(Node parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) return node;
        }
        return null;
    }

    private static String attribute(Node node, String name) {
        NamedNodeMap attributes = node.getAttributes();
        Node value = attributes != null ? attributes.getNamedItem(name) : null;
        return value != null ? value.getNodeValue() : null;
    }

    private static int intAttribute(Node node, String name, int fallback) {
        try {
            String value = attribute(node, name);
            return value != null ? Integer.parseInt(value) : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
