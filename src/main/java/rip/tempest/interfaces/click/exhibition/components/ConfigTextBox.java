package rip.tempest.interfaces.click.exhibition.components;

import rip.tempest.interfaces.click.exhibition.ui.SkeetMenu;
import rip.tempest.interfaces.click.exhibition.util.Colors;
import rip.tempest.interfaces.click.exhibition.util.Depth;
import rip.tempest.interfaces.click.exhibition.util.ExhibitionFonts;
import rip.tempest.interfaces.click.exhibition.util.Opacity;
import rip.tempest.interfaces.click.exhibition.util.RenderingUtil;
import rip.tempest.interfaces.click.exhibition.util.TTFFontRenderer;
import net.minecraft.util.ChatAllowedCharacters;
import org.lwjgl.input.Keyboard;

public class ConfigTextBox {
    public String textString;
    public float x;
    public float y;
    public CategoryPanel panel;
    public boolean isFocused;
    public boolean isTyping;
    public Opacity opacity = new Opacity(255);
    public boolean backwards;
    public int selectedChar;
    public float offset;

    public ConfigTextBox(float x, float y, CategoryPanel panel) {
        this.x = x;
        this.y = y;
        this.panel = panel;
        this.textString = "";
        this.selectedChar = textString.length();
    }

    public void draw(final float x, final float y) {
        float xOff = panel.categoryButton.panel.dragX;
        float yOff = panel.categoryButton.panel.dragY;
        if (this.selectedChar > this.textString.length()) {
            this.selectedChar = this.textString.length();
        } else if (this.selectedChar < 0) {
            this.selectedChar = 0;
        }
        int selectedChar = this.selectedChar;
        boolean hovering = (x >= xOff + this.x) && (y >= yOff + this.y)
                && (x <= xOff + this.x + 84) && (y <= yOff + this.y + 9);

        RenderingUtil.rectangle(this.x + xOff - 0.3, this.y + yOff - 0.3, this.x + xOff + 84 + 1.5F, this.y + yOff + 7.5F + 0.3, Colors.getColor(10, (int) SkeetMenu.opacity.getOpacity()));
        RenderingUtil.drawGradient(this.x + xOff, this.y + yOff, this.x + xOff + 85, this.y + yOff + 7.5F, Colors.getColor(31, (int) SkeetMenu.opacity.getOpacity()), Colors.getColor(36, (int) SkeetMenu.opacity.getOpacity()));
        if (hovering || this.isFocused) {
            RenderingUtil.rectangleBordered(this.x + xOff, this.y + yOff, this.x + xOff + 85, this.y + yOff + 7.5F, 0.3, Colors.getColor(0, 0), this.isFocused ? Colors.getColor(130, (int) SkeetMenu.opacity.getOpacity()) : Colors.getColor(90, (int) SkeetMenu.opacity.getOpacity()));
        }
        String xd = "New Config Name";
        ExhibitionFonts.fss.drawStringWithShadow(xd, (this.x + xOff + 1), (this.y - 6 + yOff), Colors.getColor(220, (int) SkeetMenu.opacity.getOpacity()));

        Depth.pre();
        Depth.mask();
        RenderingUtil.rectangle(this.x + xOff + 2, this.y + yOff, this.x + xOff + 82, this.y + yOff + 7.5F, Colors.getColor(90, (int) SkeetMenu.opacity.getOpacity()));
        Depth.render();
        ExhibitionFonts.fss.drawString(this.textString, (this.x + 1.5F + xOff) - this.offset, (this.y + 2 + yOff), Colors.getColor(151, (int) SkeetMenu.opacity.getOpacity()));
        Depth.post();

        if (this.opacity.getOpacity() >= 255) {
            this.backwards = true;
        } else if (this.opacity.getOpacity() <= 40) {
            this.backwards = false;
        }
        this.opacity.interp(this.backwards ? 40 : 255, 7);

        if (this.isFocused) {
            float width = ExhibitionFonts.fss.getWidth(this.textString.substring(0, selectedChar));
            float posX = this.x + xOff + width - this.offset;
            RenderingUtil.rectangle(posX - 0.5, this.y + yOff + 1.5, posX, this.y + yOff + 6, Colors.getColor(220, (int) this.opacity.getOpacity()));
        } else {
            this.opacity.setOpacity(255);
        }
    }

    public void mouseClicked(final int x, final int y, final int mouseID) {
        float xOff = panel.categoryButton.panel.dragX;
        float yOff = panel.categoryButton.panel.dragY;
        boolean hovering = (x >= xOff + this.x) && (y >= yOff + this.y) && (x <= xOff + this.x + 84) && (y <= yOff + this.y + 9);

        if (hovering && mouseID == 0 && !this.isFocused) {
            this.isFocused = true;
            Keyboard.enableRepeatEvents(true);
        } else if (!hovering) {
            this.isFocused = false;
            this.isTyping = false;
        }
    }

    public void keyPressed(char letter, int key) {
        if (panel.visible) {
            if (letter == '\r' || key == Keyboard.KEY_RETURN) {
                this.isFocused = false;
                this.isTyping = false;
                Keyboard.enableRepeatEvents(false);
                return;
            }

            if (this.isFocused) {
                if (key == Keyboard.KEY_LEFT) {
                    if (this.selectedChar > 0)
                        this.selectedChar--;
                    float width = ExhibitionFonts.fss.getWidth(this.textString.substring(0, this.selectedChar));
                    float barOffset = (width - this.offset);
                    barOffset -= 2;
                    if (barOffset < 0) {
                        this.offset += barOffset;
                    }
                } else if (key == Keyboard.KEY_RIGHT) {
                    if (this.selectedChar < this.textString.length())
                        this.selectedChar++;
                    float width = ExhibitionFonts.fss.getWidth(this.textString.substring(0, this.selectedChar));
                    float barOffset = (width - this.offset);
                    if (barOffset > 82) {
                        this.offset += (barOffset - 82);
                    }
                } else if (key == Keyboard.KEY_DOWN) {
                    this.selectedChar = this.textString.length();
                    float width = ExhibitionFonts.fss.getWidth(this.textString.substring(0, this.selectedChar));
                    float barOffset = (width - this.offset);
                    if (barOffset > 82) {
                        this.offset += (barOffset - 82);
                    }
                } else if (key == Keyboard.KEY_UP) {
                    this.selectedChar = 0;
                    this.offset = 0;
                } else if (key == Keyboard.KEY_BACK && this.textString.length() > 0 && this.selectedChar > 0) {
                    String oldString = this.textString;

                    StringBuilder stringBuilder = new StringBuilder(oldString);
                    stringBuilder.deleteCharAt(this.selectedChar - 1);
                    this.textString = ChatAllowedCharacters.filterAllowedCharacters(stringBuilder.toString());
                    this.selectedChar--;
                    if (ExhibitionFonts.fss.getWidth(oldString) > 82 && this.offset > 0) {
                        float newTextWidth = ExhibitionFonts.fss.getWidth(this.textString);
                        float oldTextWidth = ExhibitionFonts.fss.getWidth(oldString);
                        float charWidth = newTextWidth - oldTextWidth;
                        if (newTextWidth <= 82 && oldTextWidth - 82 > charWidth) {
                            charWidth = 82 - oldTextWidth;
                        }

                        this.offset += charWidth;
                    }
                    if (this.selectedChar > this.textString.length()) {
                        this.selectedChar = this.textString.length();
                    }
                }
            }

            if (this.isFocused && ChatAllowedCharacters.isAllowedCharacter(letter)) {
                Keyboard.enableRepeatEvents(true);

                if (!this.isTyping)
                    this.isTyping = true;

                String oldString = this.textString;

                StringBuilder stringBuilder = new StringBuilder(oldString);
                stringBuilder.insert(this.selectedChar, letter);

                this.textString = ChatAllowedCharacters.filterAllowedCharacters(stringBuilder.toString());

                if (this.selectedChar > this.textString.length()) {
                    this.selectedChar = this.textString.length();
                } else if (this.selectedChar == oldString.length() && this.textString.startsWith(oldString)) {
                    this.selectedChar += this.textString.length() - oldString.length();
                } else {
                    this.selectedChar++;
                    float width = ExhibitionFonts.fss.getWidth(this.textString.substring(0, this.selectedChar));
                    float barOffset = (width - this.offset);
                    if (barOffset > 82) {
                        this.offset += (barOffset - 82);
                    }
                }
                TTFFontRenderer f = ExhibitionFonts.fss;
                float newTextWidth = f.getWidth(this.textString);
                float oldTextWidth = f.getWidth(oldString);
                if (newTextWidth > 82) {
                    if (oldTextWidth < 82) {
                        oldTextWidth = 82;
                    }
                    float charWidth = (newTextWidth - oldTextWidth);
                    if (this.selectedChar == this.textString.length())
                        this.offset += charWidth;
                }
            }
        }
    }
}
