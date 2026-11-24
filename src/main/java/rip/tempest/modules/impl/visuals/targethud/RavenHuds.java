package rip.tempest.modules.impl.visuals.targethud;

import rip.tempest.modules.impl.visuals.TargetInterfaceModule;
import rip.tempest.processes.ColorProcess;
import rip.tempest.utils.render.Glyphs;
import rip.tempest.utils.render.RenderUtils;
import rip.tempest.utils.kit.render.RoundedUtil;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import java.awt.Color;

import static rip.tempest.utils.Util.mc;

/**
 * Raven B4's two target HUDs: one line with the name, their health, how far ahead you are and
 * whether you'd win, over a health bar, in a box. Legacy is a dark box outlined in the client
 * colours with a quick bar. Modern is a blurred, glowing box (through Post Processing's blur and
 * bloom) whose bar eases and leaves a fading trail as health drops. After the fight it stays up a
 * moment, then fades out.
 *
 * <p>From OpenB4 (a leaked Raven B4, no licence). Its themes are the client colours here, and it
 * sits at the Target HUD's drag position instead of an offset from the middle of the screen.
 */
public final class RavenHuds {
    public enum Style { Modern, Legacy }

    private static final int TEXT = new Color(220, 220, 220).getRGB();

    private static final long HOLD = 250L, FADE = 400L;

    private static final float PAD = 8, BAR_Y = 24, BAR_HEIGHT = 5, HEIGHT = 37;

    private String name = "";
    private float health;
    private double ratio = 1.0;
    private long lastSeen = -1L;

    private double shown, target = -1.0, from = -1.0;
    private long changedAt = -1L;

    public void update(EntityLivingBase current) {
        if (current == null) return;
        name = current instanceof EntityPlayer ? TargetInterfaceModule.disguiseName(current)
                : current.getDisplayName().getUnformattedText();
        health = current.getHealth() + current.getAbsorptionAmount();
        ratio = Math.min(health / Math.max(1f, current.getMaxHealth()), 1.0f);
        lastSeen = System.currentTimeMillis();
    }

    public int alpha() {
        if (lastSeen < 0) return 0;
        long age = System.currentTimeMillis() - lastSeen;
        if (age < HOLD) return 255;
        if (age >= HOLD + FADE) return 0;
        float t = (age - HOLD) / (float) FADE;
        float eased = t < 0.5f ? 4f * t * t * t : (t - 1f) * (2f * t - 2f) * (2f * t - 2f) + 1f;
        return 255 - Math.round(255 * eased);
    }

    public String text(boolean showHealth, boolean showDifference, boolean showWinLoss) {
        StringBuilder sb = new StringBuilder(name);
        float own = mc.thePlayer != null ? mc.thePlayer.getHealth() + mc.thePlayer.getAbsorptionAmount() : 0f;
        if (showHealth) {
            String color = ratio < 0.3 ? "\247c" : ratio < 0.5 ? "\2476" : ratio < 0.7 ? "\247e" : "\247a";
            sb.append(' ').append(color).append(number(health));
        }
        if (showDifference) {
            float lead = own - health;
            String color = lead >= 8f ? "\2472" : lead > 2f ? "\247a" : lead > -2f ? "\247e" : lead > -6f ? "\2476" : "\247c";
            sb.append(' ').append(color).append(lead >= 0 ? "+" : "").append(number(lead));
        }
        if (showWinLoss && mc.thePlayer != null) {
            boolean win = ratio <= own / mc.thePlayer.getMaxHealth();
            sb.append(' ').append(win ? "\247aW" : "\247cL");
        }
        String s = sb.toString();
        if (s.length() < 14) {
            String pad = "          ".substring(0, (16 - s.length() + 1) / 2);
            s = pad + s + pad;
        }
        return s;
    }

    public float[] size(String text) {
        return new float[]{mc.fontRendererObj.getStringWidth(text) + PAD * 2, HEIGHT};
    }

    public void animate(Style style) {
        double d = ratio;
        long now = System.currentTimeMillis();
        if (target == -1.0) {
            target = d;
            shown = d;
            return;
        }
        long age = now - changedAt;

        if (style == Style.Legacy) {
            long duration = 125L;
            if (d != target) {
                from = age > duration ? target : shown;
                target = d;
                changedAt = now;
            } else if (age <= duration) {
                shown = from + (target - from) * Math.min(1.0, (double) age / duration);
            } else {
                shown = d;
            }
            return;
        }

        boolean falling = target < from;
        long duration = falling ? 800L : 500L;
        if (d == target) {
            if (age > duration) {
                shown = d;
            } else {
                double t = Math.min(1.0, (double) age / duration);
                shown = from + (target - from) * (1.0 - Math.pow(1.0 - t, 5.0));
            }
        } else {
            from = age <= duration ? shown : target;
            target = d;
            changedAt = now;
        }
    }

    public void render(Style style, String text, float x, float y, boolean traditional, boolean shapeOnly) {
        int a = alpha();
        if (a <= 0) return;

        float width = size(text)[0];
        int bgA = Math.min(a, 100), barA = Math.min(a, 210);
        Color first = ColorProcess.colors.getFirst(), second = ColorProcess.colors.getSecond();

        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, 0);

        if (shapeOnly) {
            if (style == Style.Modern) RenderUtils.drawRoundedRect(0, 0, width, HEIGHT, 5, alpha(Color.BLACK, barA));
            GlStateManager.popMatrix();
            return;
        }

        float barX = PAD - 2, barWidth = width - (PAD - 2) * 2;
        double d = shown;

        if (style == Style.Legacy) {
            RenderUtils.drawRoundedRect(0, 0, width, HEIGHT, 5, alpha(Color.BLACK, bgA));
            Glyphs.gradientOutline(0, 0, width, HEIGHT, 5, 1f, alpha(first, a), alpha(second, a), false);
            if (d < 1.0) RenderUtils.drawRoundedRect(barX, BAR_Y, barWidth, BAR_HEIGHT, 2, alpha(Color.BLACK, bgA));
            Color c1 = traditional ? healthColor(d) : first;
            Color c2 = traditional ? healthColor(d) : blend(first, second, 1.0 - d);
            bar(barX, (float) (barWidth * d), alpha(c1, barA), alpha(c2, barA));
        } else {
            RenderUtils.drawRoundedRect(0, 0, width, HEIGHT, 5, alpha(Color.BLACK, Math.min(a, 70)));
            boolean falling = target < from;
            if (d < 1.0) RenderUtils.drawRoundedRect(barX, BAR_Y, barWidth, BAR_HEIGHT, 2, alpha(Color.BLACK, bgA));
            double front = falling ? target : d;
            Color c1 = traditional ? healthColor(d) : first;
            Color c2 = traditional ? healthColor(d) : blend(first, second, 1.0 - front);
            if (!falling) {
                bar(barX, (float) (barWidth * d), alpha(c1, barA), alpha(c2, barA));
            } else {
                if (target < 1.0) {
                    Color t2 = traditional ? healthColor(d) : blend(first, second, 1.0 - d);
                    bar(barX, (float) (barWidth * d), alpha(c1, bgA), alpha(t2, bgA));
                }
                bar(barX, (float) (barWidth * target), alpha(c1, barA), alpha(c2, barA));
            }
        }

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        mc.fontRendererObj.drawString(text, PAD, PAD, (Math.max(5, a) << 24) | (TEXT & 0xFFFFFF), true);
        GlStateManager.popMatrix();
    }

    private static void bar(float x, float width, Color left, Color right) {
        if (width <= 0f) return;
        RoundedUtil.drawGradientHorizontal(x, BAR_Y, width, BAR_HEIGHT, Math.min(2f, width / 2f), left, right);
    }

    private static String number(double d) {
        double rounded = Math.round(d * 10.0) / 10.0;
        return rounded == Math.floor(rounded) ? Long.toString((long) rounded) : Double.toString(rounded);
    }

    private static Color healthColor(double d) {
        return new Color(d < 0.3 ? 0xFF2626 : d < 0.5 ? 0xFFD026 : d < 0.7 ? 0xFFFF26 : 0x26FF26);
    }

    private static Color blend(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color(
                (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }
}
