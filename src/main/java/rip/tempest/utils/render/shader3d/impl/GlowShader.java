package rip.tempest.utils.render.shader3d.impl;

import rip.tempest.utils.render.shader3d.FramebufferShader;
import org.lwjgl.opengl.GL20;

import static rip.tempest.utils.Util.mc;

/**
 * Adapted from Yuri's GlowShader (AGPL-3.0; originally LiquidBounce's, GPL-3.0). Yuri took a few
 * widely spaced samples, which left the halo blocky; here {@code radius} is the number of
 * samples each way and {@code quality} their spacing in screen pixels, with the weights
 * rebalanced so a fully surrounded pixel still saturates.
 */
public final class GlowShader extends FramebufferShader {
    public static final GlowShader GLOW_SHADER = new GlowShader();

    public GlowShader() {
        super("glow.frag");
    }

    @Override
    public void setupUniforms() {
        setupUniform("texture");
        setupUniform("texelSize");
        setupUniform("color");
        setupUniform("divider");
        setupUniform("radius");
        setupUniform("maxSample");
    }

    @Override
    public void updateUniforms() {
        GL20.glUniform1i(getUniform("texture"), 0);
        GL20.glUniform2f(getUniform("texelSize"), 1F / mc.displayWidth * quality, 1F / mc.displayHeight * quality);
        GL20.glUniform3f(getUniform("color"), red, green, blue);
        GL20.glUniform1f(getUniform("divider"), 210F);
        GL20.glUniform1f(getUniform("radius"), radius);
        GL20.glUniform1f(getUniform("maxSample"), radius + 3F);
    }
}
