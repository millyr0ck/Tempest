package rip.tempest.utils.render.shader3d.impl;

import rip.tempest.utils.render.shader3d.FramebufferShader;
import org.lwjgl.opengl.GL20;

import static rip.tempest.utils.Util.mc;

/**
 * Adapted from Yuri's OutlineShader (AGPL-3.0; originally LiquidBounce's, GPL-3.0). Tempest's
 * outline.frag anti-aliases the outline, so {@code radius} is its width in screen pixels and
 * the texel size is exactly one pixel.
 */
public final class OutlineShader extends FramebufferShader {
    public static final OutlineShader OUTLINE_SHADER = new OutlineShader();

    public OutlineShader() {
        super("outline.frag");
    }

    @Override
    public void setupUniforms() {
        setupUniform("texture");
        setupUniform("texelSize");
        setupUniform("color");
        setupUniform("radius");
    }

    @Override
    public void updateUniforms() {
        GL20.glUniform1i(getUniform("texture"), 0);
        GL20.glUniform2f(getUniform("texelSize"), 1F / mc.displayWidth, 1F / mc.displayHeight);
        GL20.glUniform4f(getUniform("color"), red, green, blue, alpha);
        GL20.glUniform1f(getUniform("radius"), radius);
    }
}
