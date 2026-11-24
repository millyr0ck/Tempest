package rip.tempest.jenny.render;

import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

public class TextureLoader {
    private static final Map<String, Integer> cache = new HashMap<>();

    public static void bind(String classpathPath) {
        int id = cache.computeIfAbsent(classpathPath, TextureLoader::upload);
        if (id > 0) GlStateManager.bindTexture(id);
    }

    private static int upload(String path) {
        try {
            InputStream is = TextureLoader.class.getResourceAsStream(path);
            if (is == null) {
                System.err.println("[Jenny] Texture not found: " + path);
                return 0;
            }
            BufferedImage img = ImageIO.read(is);
            is.close();

            int w = img.getWidth(), h = img.getHeight();
            int[] pixels = new int[w * h];
            img.getRGB(0, 0, w, h, pixels, 0, w);

            ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
            for (int px : pixels) {
                buf.put((byte) ((px >> 16) & 0xFF));
                buf.put((byte) ((px >> 8) & 0xFF));
                buf.put((byte) (px & 0xFF));
                buf.put((byte) ((px >> 24) & 0xFF));
            }
            buf.flip();

            int id = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, id);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buf);
            return id;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }
}
