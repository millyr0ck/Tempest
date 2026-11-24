package rip.tempest.modules.impl.visuals.targethud;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;

/**
 * One target HUD style drawn by {@link TargetHuds}. Adapted from Yuri's TargetHudMode
 * (AGPL-3.0); Lombok's getter is written out.
 */
public abstract class TargetHudMode {
    protected final Minecraft mc = Minecraft.getMinecraft();
    private final String name;

    public TargetHudMode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public abstract int getMinWidth();

    public abstract int getHudHeight();

    public abstract int getLabelHeight();

    public abstract void draw(EntityLivingBase targetEntity, TargetHuds.TargetState state,
                              double x, double y, long now, float delta);
}
