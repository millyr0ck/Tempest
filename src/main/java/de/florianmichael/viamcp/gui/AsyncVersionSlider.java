/*
 * This file is part of ViaMCP - https://github.com/FlorianMichael/ViaMCP
 * Copyright (C) 2020-2024 FlorianMichael/EnZaXD <florian.michael07@gmail.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.florianmichael.viamcp.gui;

import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.processes.FontProcess;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import de.florianmichael.vialoadingbase.ViaLoadingBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.MathHelper;

import java.awt.Color;
import java.util.Collections;
import java.util.List;

public class AsyncVersionSlider extends GuiButton {
    private static final float RADIUS = 5.0F;

    private float dragValue = (float) 0;

    private final List<ProtocolVersion> values;
    private float sliderValue;
    public boolean dragging;

    public AsyncVersionSlider(int buttonId, int x, int y , int widthIn, int heightIn)
    {
        super(buttonId, x, y, Math.max(widthIn, 110), heightIn, "");
        this.values = ViaLoadingBase.PROTOCOLS;
        Collections.reverse(values);
        this.sliderValue = dragValue;
        this.displayString = values.get((int) Math.ceil(this.sliderValue * (values.size() - 1))).getName();
    }

    /**
     * Tempest: drawn in the style of the client's own menus (a rounded track that fills up to a
     * handle) instead of with the vanilla button texture. The behaviour is unchanged.
     */
    public void drawButton(Minecraft mc, int mouseX, int mouseY)
    {
        if (!this.visible)
        {
            return;
        }

        this.hovered = mouseX >= this.xPosition && mouseY >= this.yPosition && mouseX < this.xPosition + this.width && mouseY < this.yPosition + this.height;
        this.mouseDragged(mc, mouseX, mouseY);

        boolean active = this.hovered || this.dragging;
        // The client colours, as on the rest of the menus.
        Color ACCENT = rip.tempest.interfaces.menu.MenuTheme.accent();
        float handleX = this.xPosition + 4 + this.sliderValue * (this.width - 8);
        float filled = handleX - this.xPosition;

        if (rip.tempest.interfaces.menu.MenuTheme.astolfo())
        {
            // Astolfo's button look: see-through dark, a one pixel outline lit in the client colour
            Glyphs.bar(this.xPosition, this.yPosition, this.width, this.height, active ? new Color(20, 20, 20, 0x99) : new Color(18, 18, 18, 0x88));
            Glyphs.bar(this.xPosition, this.yPosition, filled, this.height, new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 40));
            rip.tempest.interfaces.menu.MenuTheme.frame(this.xPosition, this.yPosition, this.width, this.height,
                    active ? ACCENT : new Color(51, 51, 51, 128));
            Glyphs.bar(handleX - 1.0F, this.yPosition + 1.0F, 2.0F, this.height - 2.0F, ACCENT);
            mc.fontRendererObj.drawString(this.displayString, this.xPosition + (this.width - mc.fontRendererObj.getStringWidth(this.displayString)) / 2.0F,
                    this.yPosition + (this.height - 8) / 2.0F, active ? ACCENT.getRGB() : 0xFFE0E0E0, false);
            return;
        }

        RenderUtils.drawRoundedRect(this.xPosition, this.yPosition, this.width, this.height, RADIUS, new Color(255, 255, 255, active ? 22 : 12));

        if (filled > RADIUS * 2)
        {
            RenderUtils.drawRoundedRect(this.xPosition, this.yPosition, filled, this.height, RADIUS, new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 46));
        }

        RenderUtils.drawRoundOutline(this.xPosition, this.yPosition, this.width, this.height, RADIUS, 1.0F, new Color(0, 0, 0, 0),
                active ? new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 190) : new Color(255, 255, 255, 28));
        Glyphs.stroke(handleX, this.yPosition + 5.5F, handleX, this.yPosition + this.height - 5.5F, 2.5F, new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), active ? 255 : 200));

        CustomFontRenderer font = FontProcess.getFont("tempest");
        font.drawCenteredString(this.displayString, this.xPosition + this.width / 2.0F, this.yPosition + (this.height - font.getHeight()) / 2.0F, 0xFFE6E6EE);
    }

    /**
     * Returns 0 if the button is disabled, 1 if the mouse is NOT hovering over this button and 2 if it IS hovering over
     * this button.
     */
    protected int getHoverState(boolean mouseOver)
    {
        return 0;
    }

    /**
     * Fired when the mouse button is dragged. Equivalent of MouseListener.mouseDragged(MouseEvent e).
     */
    protected void mouseDragged(Minecraft mc, int mouseX, int mouseY)
    {
        if (this.visible)
        {
            if (this.dragging)
            {
                this.sliderValue = (float)(mouseX - (this.xPosition + 4)) / (float)(this.width - 8);
                this.sliderValue = MathHelper.clamp_float(this.sliderValue, 0.0F, 1.0F);
                this.dragValue = sliderValue;

                // Ceil index to show correctly display string (26.999998 => 27)
                int selectedProtocolIndex = (int) Math.ceil(this.sliderValue * (values.size() - 1));
                this.displayString = values.get(selectedProtocolIndex).getName();
                ViaLoadingBase.getInstance().reload(values.get(selectedProtocolIndex));
            }

        }
    }

    /**
     * Returns true if the mouse has been pressed on this control. Equivalent of MouseListener.mousePressed(MouseEvent
     * e).
     */
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY)
    {
        if (super.mousePressed(mc, mouseX, mouseY))
        {
            this.sliderValue = (float)(mouseX - (this.xPosition + 4)) / (float)(this.width - 8);
            this.sliderValue = MathHelper.clamp_float(this.sliderValue, 0.0F, 1.0F);
            this.dragValue = sliderValue;

            int selectedProtocolIndex = (int) Math.ceil(this.sliderValue * (values.size() - 1));
            this.displayString = values.get(selectedProtocolIndex).getName();
            ViaLoadingBase.getInstance().reload(values.get(selectedProtocolIndex));
            this.dragging = true;
            return true;
        }
        else
        {
            return false;
        }
    }

    /**
     * Fired when the mouse button is released. Equivalent of MouseListener.mouseReleased(MouseEvent e).
     */
    public void mouseReleased(int mouseX, int mouseY)
    {
        this.dragging = false;
    }

    public void setVersion(int protocol)
    {
        this.dragValue = (float) ViaLoadingBase.PROTOCOLS.indexOf(ProtocolVersion.getProtocol(protocol)) / (ViaLoadingBase.PROTOCOLS.size() - 1);
        this.sliderValue = this.dragValue;

        int selectedProtocolIndex = (int) Math.ceil(this.sliderValue * (values.size() - 1));
        this.displayString = values.get(selectedProtocolIndex).getName();
    }
}
