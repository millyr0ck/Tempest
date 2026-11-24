package rip.tempest.utils.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class CapeTexture {
    private static final String FOLDER = "tempest/images/";

    private static final int USED_WIDTH = 22, USED_HEIGHT = 17;

    private CapeTexture() {
    }

    public static ResourceLocation load(String file) {
        ResourceLocation source = new ResourceLocation(FOLDER + file);
        Minecraft mc = Minecraft.getMinecraft();

        try (InputStream in = mc.getResourceManager().getResource(source).getInputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) return source;

            int width = image.getWidth(), height = image.getHeight();
            if (width * USED_HEIGHT != height * USED_WIDTH) return source;

            int scale = width / USED_WIDTH;
            BufferedImage sheet = new BufferedImage(64 * scale, 32 * scale, BufferedImage.TYPE_INT_ARGB);
            Graphics graphics = sheet.getGraphics();
            graphics.drawImage(image, 0, 0, null);
            graphics.dispose();

            ResourceLocation padded = new ResourceLocation("tempest/capes/" + file.toLowerCase(Locale.ROOT));
            mc.getTextureManager().loadTexture(padded, new DynamicTexture(sheet));
            return padded;
        } catch (IOException e) {
            return source;
        }
    }
}
