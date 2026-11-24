package rip.tempest.jenny.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.IntBuffer;

public final class PlayerSkin {
    private static final Color BASE_SKIN = new Color(245, 199, 165);
    private static final Color BLUSH = new Color(245, 157, 169);

    private static ResourceLocation source;
    private static ResourceLocation tinted;
    private static int retryFrames;

    private PlayerSkin() {
    }

    public static void bind() {
        Minecraft mc = Minecraft.getMinecraft();
        AbstractClientPlayer player = mc.thePlayer;
        ResourceLocation skin = player != null ? player.getLocationSkin() : null;
        if (skin != null && (!skin.equals(source) || (tinted == null && --retryFrames <= 0))) {
            rebuild(skin);
        }
        if (tinted != null) {
            mc.getTextureManager().bindTexture(tinted);
        } else {
            TextureLoader.bind("/assets/jenny/textures/player/steve.png");
        }
    }

    public static boolean isSlim() {
        AbstractClientPlayer player = Minecraft.getMinecraft().thePlayer;
        return player != null && "slim".equals(player.getSkinType());
    }

    private static void rebuild(ResourceLocation skin) {
        Minecraft mc = Minecraft.getMinecraft();
        source = skin;
        BufferedImage image = readTexture(skin);
        if (image == null) image = readFallback();
        if (image == null) {
            retryFrames = 40;
            return;
        }

        Graphics g = image.getGraphics();
        g.setColor(BASE_SKIN);
        g.fillRect(0, 0, 4, 3);
        g.setColor(BLUSH);
        g.fillRect(4, 0, 3, 3);
        g.dispose();

        if (tinted != null) mc.getTextureManager().deleteTexture(tinted);
        tinted = mc.getTextureManager().getDynamicTextureLocation("jenny_player_skin", new DynamicTexture(image));
    }

    private static BufferedImage readTexture(ResourceLocation skin) {
        try {
            Minecraft.getMinecraft().getTextureManager().bindTexture(skin);
            int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            if (w <= 0 || h <= 0) return null;
            IntBuffer buf = BufferUtils.createIntBuffer(w * h);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, buf);
            int[] pixels = new int[w * h];
            buf.get(pixels);

            BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            BufferedImage read = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            read.setRGB(0, 0, w, h, pixels, 0, w);
            Graphics g = image.getGraphics();
            g.drawImage(read, 0, 0, 64, h * 64 / w, null);
            g.dispose();
            return image;
        } catch (Exception e) {
            return null;
        }
    }

    private static BufferedImage readFallback() {
        try (InputStream in = PlayerSkin.class.getResourceAsStream("/assets/jenny/textures/player/steve.png")) {
            if (in == null) return null;
            BufferedImage raw = ImageIO.read(in);
            BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
            Graphics g = image.getGraphics();
            g.drawImage(raw, 0, 0, null);
            g.dispose();
            return image;
        } catch (Exception e) {
            return null;
        }
    }
}
