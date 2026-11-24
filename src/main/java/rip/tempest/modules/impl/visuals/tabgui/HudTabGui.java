package rip.tempest.modules.impl.visuals.tabgui;

public interface HudTabGui {
    void render();

    float width();

    float height();

    void keyPress(int key);
}
