package rip.tempest.interfaces.click.bankroll;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class BankrollFont {
    public static final class Glyph {
        public final int x, y, width, height, left, top, advance;

        Glyph(int x, int y, int width, int height, int left, int top, int advance) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.left = left;
            this.top = top;
            this.advance = advance;
        }
    }

    public final BufferedImage atlas;
    private final Glyph[] glyphs = new Glyph[256];
    private final Glyph fallback;

    private BankrollFont(BufferedImage atlas, Glyph[] table) {
        this.atlas = atlas;
        System.arraycopy(table, 0, glyphs, 0, glyphs.length);
        this.fallback = glyphs['?'];
    }

    public static BankrollFont load(InputStream png, InputStream table) throws IOException {
        BufferedImage grey = ImageIO.read(png);
        if (grey == null) throw new IOException("bad atlas image");
        BufferedImage atlas = new BufferedImage(grey.getWidth(), grey.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < grey.getHeight(); y++) {
            for (int x = 0; x < grey.getWidth(); x++) {
                int coverage = grey.getRaster().getSample(x, y, 0);
                atlas.setRGB(x, y, coverage << 24 | 0xFFFFFF);
            }
        }

        Glyph[] glyphs = new Glyph[256];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(table, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\s+");
                int code = Integer.parseInt(p[0]);
                if (code < 0 || code > 255) continue;
                glyphs[code] = new Glyph(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]),
                        Integer.parseInt(p[4]), Integer.parseInt(p[5]), Integer.parseInt(p[6]), Integer.parseInt(p[7]));
            }
        }
        if (glyphs['?'] == null) throw new IOException("glyph table has no '?'");
        return new BankrollFont(atlas, glyphs);
    }

    public Glyph glyph(char c) {
        Glyph glyph = c < 256 ? glyphs[c] : null;
        return glyph != null ? glyph : fallback;
    }

    public int width(String text) {
        return width(text, 0, text.length());
    }

    public int width(String text, int start, int end) {
        int width = 0;
        for (int i = start; i < end; i++) width += glyph(text.charAt(i)).advance;
        return width;
    }
}
