package rip.tempest.api.font;

import rip.tempest.interfaces.click.exhibition.util.TTFFontRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class CustomFontRenderer extends CustomFont {
    protected CustomFont.CharData[] boldChars = new CustomFont.CharData[256];
    protected CustomFont.CharData[] italicChars = new CustomFont.CharData[256];
    protected CustomFont.CharData[] boldItalicChars = new CustomFont.CharData[256];

    public int FONT_HEIGHT = 9;

    private final int[] colorCode = new int[32];
    private static final int SHADOW_MASK = new Color(20, 20, 20, 200).getRGB();

    private boolean useMCustomFont = false;

    private java.util.function.Supplier<net.minecraft.client.gui.FontRenderer> bitmapFont;

    private TTFFontRenderer exhibitionRenderer;

    public CustomFontRenderer(Font font, boolean antiAlias, boolean fractionalMetrics) {
        super(font, antiAlias, fractionalMetrics);
        setupMinecraftColorcodes();
    }

    String nameFontTTF;

    public CustomFontRenderer(String NameFontTTF, float size, int fonttype, boolean antiAlias, boolean fractionalMetrics) {
        super(getFontFromTTF(new ResourceLocation("tempest/fonts/" + NameFontTTF+".ttf"), size,fonttype), antiAlias, fractionalMetrics);
        this.nameFontTTF = NameFontTTF;
        this.useMCustomFont = NameFontTTF.equalsIgnoreCase("mc");
        setupMinecraftColorcodes();
    }

    public String getNameFontTTF() {
        return this.nameFontTTF;
    }

    public static CustomFontRenderer bitmap(String name, java.util.function.Supplier<net.minecraft.client.gui.FontRenderer> renderer) {
        CustomFontRenderer font = new CustomFontRenderer("mc", 18, Font.PLAIN, true, false);
        font.nameFontTTF = name;
        font.bitmapFont = renderer;
        return font;
    }

    public static CustomFontRenderer file(String name, String file, float size, boolean fractionalMetrics) {
        Font font = getFontFromTTF(new ResourceLocation("tempest/fonts/" + file + ".ttf"), size, Font.TRUETYPE_FONT);
        CustomFontRenderer renderer = new CustomFontRenderer(font, true, fractionalMetrics);
        renderer.nameFontTTF = name;
        return renderer;
    }

    public static CustomFontRenderer exhibition(String name, String file, float size) {
        Font font = getFontFromTTF(new ResourceLocation("tempest/fonts/" + file + ".ttf"), size, Font.TRUETYPE_FONT);
        CustomFontRenderer renderer = new CustomFontRenderer(font, true, true);
        renderer.nameFontTTF = name;
        renderer.exhibitionRenderer = new TTFFontRenderer(font, true);
        return renderer;
    }

    private float drawExhibition(String text, double x, double y, int color, boolean withShadow) {
        if (text == null) return 0.0F;

        if (color == 553648127) color = 16777215;
        if ((color & 0xFC000000) == 0) color |= -16777216;

        String drawn = text.endsWith("\u00a7") ? text.substring(0, text.length() - 1) : text;

        GlStateManager.enableTexture2D();
        if (withShadow) {
            exhibitionRenderer.drawStringWithShadow(drawn, x - 0.5, y - 0.5, color);
        } else {
            exhibitionRenderer.drawString(drawn, x - 0.5, y - 0.5, color);
        }
        leaveExhibition();
        return (float) (x + getStringWidth(text));
    }

    private float drawExhibitionShadow(String text, double x, double y, int color) {
        if (color == 553648127) color = 16777215;
        if ((color & 0xFC000000) == 0) color |= -16777216;

        GlStateManager.enableTexture2D();
        exhibitionRenderer.drawShadow(text.endsWith("\u00a7") ? text.substring(0, text.length() - 1) : text, x - 1.5, y - 1.5, color);
        leaveExhibition();
        return (float) (x + getStringWidth(text));
    }

    private static void leaveExhibition() {
        GlStateManager.resetColor();
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
    }

    private net.minecraft.client.gui.FontRenderer mcFont() {
        return bitmapFont != null ? bitmapFont.get() : Minecraft.getMinecraft().fontRendererObj;
    }

    public float drawString(String text, float x, float y, int color) {
        if (useMCustomFont) {
            return mcFont().drawString(text, x, y, color, false);
        }
        return drawString(text, x, y, color, false);
    }

    public float drawString(String text, double x, double y, int color) {
        if (useMCustomFont) {
            return mcFont().drawString(text, (float) x, (float) y, color, false);
        }
        return drawString(text, x, y, color, false);
    }

    public float drawStringWithShadow(String text, float x, float y, int color) {
        if (useMCustomFont) {
            return mcFont().drawString(text, x, y, color, true);
        }
        if (exhibitionRenderer != null) {
            return drawExhibition(text, x, y, color, true);
        }
        float shadowWidth = drawString(text, x + 1.0f, y + 1.0f, color, true);
        return Math.max(shadowWidth, drawString(text, x, y, color, false));
    }

    public interface LetterColors {
        int at(float letterX, float letterWidth);
    }

    public boolean canDrawLetters() {
        return !useMCustomFont && exhibitionRenderer == null && bitmapFont == null && tex != null;
    }

    public float drawLettersWithShadow(String text, float x, float y, LetterColors colors) {
        float letterX = x;
        int size = text.length();

        GL11.glPushMatrix();
        GlStateManager.scale(0.5D, 0.5D, 0.5D);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(tex.getGlTextureId());
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex.getGlTextureId());
        boolean batch = false;

        try {
            for (int i = 0; i < size; i++) {
                char character = text.charAt(i);
                boolean drawn = character != '§' && character < charData.length;

                int width = drawn ? (charData[character].width - 8 + charOffset) / 2 : 0;
                int color = colors.at(letterX, width);

                if (drawn) {
                    if (color == 553648127) color = 16777215;
                    if ((color & 0xFC000000) == 0) color |= -16777216;
                    int shadowColor = (color & 0xFCFCFC) >> 2 | color & SHADOW_MASK;

                    if (!batch) {
                        GL11.glBegin(GL11.GL_TRIANGLES);
                        batch = true;
                    }

                    double shadowX = ((double) (letterX + 1.0f) - 1.0D) * 2.0D;
                    double shadowY = ((double) (y + 1.0f) - 2.0D) * 2.0D;
                    double mainX = ((double) letterX - 1.0D) * 2.0D;
                    double mainY = ((double) y - 2.0D) * 2.0D;
                    GlStateManager.color((shadowColor >> 16 & 0xFF) / 255.0F, (shadowColor >> 8 & 0xFF) / 255.0F, (shadowColor & 0xFF) / 255.0F, (shadowColor >> 24 & 0xFF) / 255.0F);
                    drawChar(charData, character, (float) shadowX, (float) shadowY);
                    GlStateManager.color((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, (color >> 24 & 0xFF) / 255.0F);
                    drawChar(charData, character, (float) mainX, (float) mainY);
                }

                letterX += width;
            }
        } finally {
            if (batch) GL11.glEnd();
        }

        GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_DONT_CARE);
        GL11.glPopMatrix();
        return letterX;
    }

    public float drawStringWithShadow(String text, double x, double y, int color) {
        if (useMCustomFont) {
            return mcFont().drawString(text, (float) x, (float) y, color, true);
        }
        if (exhibitionRenderer != null) {
            return drawExhibition(text, x, y, color, true);
        }
        float shadowWidth = drawString(text, x + 1.0f, y + 1.0f, color, true);
        return Math.max(shadowWidth, drawString(text, x, y, color, false));
    }

    public float drawCenteredString(String text, float x, float y, int color) {
        if (useMCustomFont) {
            int width = mcFont().getStringWidth(text);
            return mcFont().drawString(text, x - width / 2f, y, color, false);
        }
        return drawString(text, x - getStringWidth(text) / 2, y, color);
    }

    public float drawCenteredString(String text, double x, double y, int color) {
        if (useMCustomFont) {
            int width = mcFont().getStringWidth(text);
            return mcFont().drawString(text, (float) (x - width / 2f), (float) y, color, false);
        }
        return drawString(text, x - getStringWidth(text) / 2, y, color);
    }

    public float drawCenteredStringWithShadow(String text, float x, float y, int color) {
        if (useMCustomFont) {
            int width = mcFont().getStringWidth(text);
            return mcFont().drawString(text, x - width / 2f, y, color, true);
        }
        if (exhibitionRenderer != null) {
            return drawExhibition(text, x - getStringWidth(text) / 2, y, color, true);
        }
        float shadowWidth = drawString(text, x - getStringWidth(text) / 2 + 0.45D, y + 0.5D, color, true);
        return drawString(text, x - getStringWidth(text) / 2, y, color);
    }

    public void drawStringWithOutline(String text, double x, double y, int color) {
        drawString(text, x - .5, y, 0x000000);

        drawString(text, x + .5, y, 0x000000);

        drawString(text, x, y - .5, 0x000000);

        drawString(text, x, y + .5, 0x000000);

        drawString(text, x, y, color);
    }

    public void drawCenteredStringWithOutline(String text, double x, double y, int color) {
        drawCenteredString(text, x - .5, y, 0x000000);

        drawCenteredString(text, x + .5, y, 0x000000);

        drawCenteredString(text, x, y - .5, 0x000000);

        drawCenteredString(text, x, y + .5, 0x000000);

        drawCenteredString(text, x, y, color);
    }

    public float drawCenteredStringWithShadow(String text, double x, double y, int color) {
        if (exhibitionRenderer != null) {
            return drawExhibition(text, x - getStringWidth(text) / 2, y, color, true);
        }
        float shadowWidth = drawString(text, x - getStringWidth(text) / 2 + 0.45D, y + 0.5D, color, true);
        return drawString(text, x - getStringWidth(text) / 2, y, color);
    }

    public float drawString(String text, double x, double y, int color, boolean shadow) {
        if (bitmapFont != null && text != null) {
            int c = shadow ? (color & 0xFCFCFC) >> 2 | color & 0xFF000000 : color;
            return mcFont().drawString(text, (float) x, (float) y, c, false);
        }
        if (exhibitionRenderer != null && text != null) {
            return shadow ? drawExhibitionShadow(text, x, y, color) : drawExhibition(text, x, y, color, false);
        }
        Minecraft mc = Minecraft.getMinecraft();
        x -= 1;

        if (text == null) {
            return 0.0F;
        }

        if (color == 553648127) {
            color = 16777215;
        }

        if ((color & 0xFC000000) == 0) {
            color |= -16777216;
        }

        if (shadow) {
            color = (color & 0xFCFCFC) >> 2 | color & SHADOW_MASK;
        }

        CustomFont.CharData[] currentData = this.charData;
        float alpha = (color >> 24 & 0xFF) / 255.0F;
        boolean randomCase = false;
        boolean bold = false;
        boolean italic = false;
        boolean strikethrough = false;
        boolean underline = false;
        boolean render = true;
        x *= 2.0D;
        y = (y - 2.0D) * 2.0D;

        if (render) {
            GL11.glPushMatrix();
            GlStateManager.scale(0.5D, 0.5D, 0.5D);
            GlStateManager.enableBlend();
            GlStateManager.blendFunc(770, 771);
            GlStateManager.color((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, alpha);
            int size = text.length();
            GlStateManager.enableTexture2D();
            GlStateManager.bindTexture(tex.getGlTextureId());

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex.getGlTextureId());

            boolean batch = false;

            try {
            for (int i = 0; i < size; i++) {
                char character = text.charAt(i);
                if (character == '\u00a7') {
                    int colorIndex = i + 1 < size ? "0123456789abcdefklmnor".indexOf(text.charAt(i + 1)) : 21;

                    if (colorIndex < 16) {
                        bold = false;
                        italic = false;
                        randomCase = false;
                        underline = false;
                        strikethrough = false;
                        if (batch) {
                            GL11.glEnd();
                            batch = false;
                        }
                        GlStateManager.bindTexture(tex.getGlTextureId());
                        currentData = this.charData;

                        if ((colorIndex < 0) || (colorIndex > 15)) {
                            colorIndex = 15;
                        }

                        if (shadow) {
                            colorIndex += 16;
                        }

                        int colorcode = this.colorCode[colorIndex];
                        GlStateManager.color((colorcode >> 16 & 0xFF) / 255.0F, (colorcode >> 8 & 0xFF) / 255.0F, (colorcode & 0xFF) / 255.0F, alpha);
                    } else if (colorIndex == 16) {
                        randomCase = true;
                    } else if (colorIndex == 17) {
                        bold = true;

                        if (italic) {
                            currentData = this.charData;
                        } else {
                            currentData = this.charData;
                        }
                    } else if (colorIndex == 18) {
                        strikethrough = true;
                    } else if (colorIndex == 19) {
                        underline = true;
                    } else if (colorIndex == 20) {
                        italic = true;

                        if (bold) {
                            currentData = this.charData;
                        } else {
                            currentData = this.charData;
                        }
                    } else if (colorIndex == 21) {
                        bold = false;
                        italic = false;
                        randomCase = false;
                        underline = false;
                        strikethrough = false;
                        GlStateManager.color((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, alpha);
                        if (batch) {
                            GL11.glEnd();
                            batch = false;
                        }
                        GlStateManager.bindTexture(tex.getGlTextureId());
                        currentData = this.charData;
                    }

                    i++;
                } else if ((character < currentData.length) && (character >= 0)) {
                    if (!batch) {
                        GL11.glBegin(GL11.GL_TRIANGLES);
                        batch = true;
                    }
                    drawChar(currentData, character, (float) x, (float) y);

                    if ((strikethrough || underline) && batch) {
                        GL11.glEnd();
                        batch = false;
                    }

                    if (strikethrough) {
                        drawLine(x, y + currentData[character].height / 2, x + currentData[character].width - 8.0D, y + currentData[character].height / 2, 1.0F);
                    }

                    if (underline) {
                        drawLine(x, y + currentData[character].height - 2.0D, x + currentData[character].width - 8.0D, y + currentData[character].height - 2.0D, 1.0F);
                    }

                    x += currentData[character].width - 8 + this.charOffset;
                }
            }
            } finally {
                if (batch) GL11.glEnd();
            }

            GL11.glHint(GL11.GL_POLYGON_SMOOTH_HINT, GL11.GL_DONT_CARE);
            GL11.glPopMatrix();
        }

        return (float) x / 2.0F;
    }

    public int getStringWidth(String text) {
        if (text == null) {
            return 0;
        }
        if (useMCustomFont) {
            return mcFont().getStringWidth(text);
        }
        if (exhibitionRenderer != null) {
            try {
                return Math.round(exhibitionRenderer.getWidth(text) - 2);
            } catch (RuntimeException e) {
            }
        }
        int width = 0;
        CustomFont.CharData[] currentData = this.charData;
        boolean bold = false;
        boolean italic = false;
        int size = text.length();

        for (int i = 0; i < size; i++) {
            char character = text.charAt(i);

            if (character == '\u00a7') {
                int colorIndex = "0123456789abcdefklmnor".indexOf(character);

                if (colorIndex < 16) {
                    bold = false;
                    italic = false;
                } else if (colorIndex == 17) {
                    bold = true;

                    if (italic) {
                        currentData = this.boldItalicChars;
                    } else {
                        currentData = this.boldChars;
                    }
                } else if (colorIndex == 20) {
                    italic = true;

                    if (bold) {
                        currentData = this.boldItalicChars;
                    } else {
                        currentData = this.italicChars;
                    }
                } else if (colorIndex == 21) {
                    bold = false;
                    italic = false;
                    currentData = this.charData;
                }

                i++;
            } else if ((character < currentData.length) && (character >= 0)) {
                width += currentData[character].width - 8 + this.charOffset;
            }
        }

        return width / 2;
    }

    public int getStringWidthCust(String text) {
        if (text == null) {
            return 0;
        }

        int width = 0;
        CustomFont.CharData[] currentData = this.charData;
        boolean bold = false;
        boolean italic = false;
        int size = text.length();

        for (int i = 0; i < size; i++) {
            char character = text.charAt(i);

            if ((String.valueOf(character).equals("�")) && (i < size)) {
                int colorIndex = "0123456789abcdefklmnor".indexOf(character);

                if (colorIndex < 16) {
                    bold = false;
                    italic = false;
                } else if (colorIndex == 17) {
                    bold = true;

                    if (italic) {
                        currentData = this.boldItalicChars;
                    } else {
                        currentData = this.boldChars;
                    }
                } else if (colorIndex == 20) {
                    italic = true;

                    if (bold) {
                        currentData = this.boldItalicChars;
                    } else {
                        currentData = this.italicChars;
                    }
                } else if (colorIndex == 21) {
                    bold = false;
                    italic = false;
                    currentData = this.charData;
                }

                i++;
            } else if ((character < currentData.length) && (character >= 0)) {
                width += currentData[character].width - 8 + this.charOffset;
            }
        }

        return (width - this.charOffset) / 2;
    }

    public void setFont(Font font) {
        super.setFont(font);
    }

    public void setAntiAlias(boolean antiAlias) {
        super.setAntiAlias(antiAlias);
    }

    public void setFractionalMetrics(boolean fractionalMetrics) {
        super.setFractionalMetrics(fractionalMetrics);
    }

    protected DynamicTexture texBold;
    protected DynamicTexture texItalic;
    protected DynamicTexture texItalicBold;

    private void drawLine(double x, double y, double x1, double y1, float width) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glLineWidth(width);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(x, y);
        GL11.glVertex2d(x1, y1);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    public List<String> wrapWords(String text, double width) {
        List finalWords = new ArrayList();

        if (getStringWidth(text) > width) {
            String[] words = text.split(" ");
            String currentWord = "";
            char lastColorCode = 65535;

            for (String word : words) {
                for (int i = 0; i < word.toCharArray().length; i++) {
                    char c = word.toCharArray()[i];

                    if ((String.valueOf(c).equals("�")) && (i < word.toCharArray().length - 1)) {
                        lastColorCode = word.toCharArray()[(i + 1)];
                    }
                }

                if (getStringWidth(currentWord + word + " ") < width) {
                    currentWord = currentWord + word + " ";
                } else {
                    finalWords.add(currentWord);
                    currentWord = "" + lastColorCode + word + " ";
                }
            }

            if (currentWord.length() > 0) if (getStringWidth(currentWord) < width) {
                finalWords.add("" + lastColorCode + currentWord + " ");
                currentWord = "";
            } else {
                for (String s : formatString(currentWord, width)) {
                    finalWords.add(s);
                }
            }
        } else {
            finalWords.add(text);
        }

        return finalWords;
    }

    public List<String> formatString(String string, double width) {
        List finalWords = new ArrayList();
        String currentWord = "";
        char lastColorCode = 65535;
        char[] chars = string.toCharArray();

        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];

            if ((String.valueOf(c).equals("�")) && (i < chars.length - 1))
            {
                lastColorCode = chars[(i + 1)];
            }

            if (getStringWidth(currentWord + c) < width) {
                currentWord = currentWord + c;
            } else {
                finalWords.add(currentWord);
                currentWord = "" + lastColorCode + String.valueOf(c);
            }
        }

        if (currentWord.length() > 0) {
            finalWords.add(currentWord);
        }

        return finalWords;
    }

    private void setupMinecraftColorcodes() {
        for (int index = 0; index < 32; index++) {
            int noClue = (index >> 3 & 0x1) * 85;
            int red = (index >> 2 & 0x1) * 170 + noClue;
            int green = (index >> 1 & 0x1) * 170 + noClue;
            int blue = (index >> 0 & 0x1) * 170 + noClue;

            if (index == 6) {
                red += 85;
            }

            if (index >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }

            this.colorCode[index] = ((red & 0xFF) << 16 | (green & 0xFF) << 8 | blue & 0xFF);
        }
    }

    public static Font getFontFromTTF(ResourceLocation fontLocation, float fontSize, int fontType) {
        Font output = null;
        try {
            output = Font.createFont(fontType, Minecraft.getMinecraft().getResourceManager().getResource(fontLocation).getInputStream());
            output = output.deriveFont(fontSize);
        } catch (Exception e) {
            e.printStackTrace();
            return new Font("Default", fontType, (int) fontSize);
        }
        return output;
    }

    public float getMiddleOfBox(float height) {
        return height / 2f - getHeight() / 2f;
    }
}
