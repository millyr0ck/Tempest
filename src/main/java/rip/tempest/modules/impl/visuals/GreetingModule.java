package rip.tempest.modules.impl.visuals;

import rip.tempest.api.events.impl.render.Render2DEvent;
import rip.tempest.api.font.CustomFontRenderer;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.FontProcess;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.client.gui.ScaledResolution;

import java.awt.*;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Greeting", category = ModuleCategory.VISUALS)
public class GreetingModule extends Module {

    @EventLink
    public Listener<Render2DEvent> render2DEventListener = e -> {
        CustomFontRenderer fr = FontProcess.getCurrentFont();
        ScaledResolution sr = new ScaledResolution(mc);

        String user = NickHiderModule.displayName();

        String text = "Hello §b" + user + "!";

        if(user.equalsIgnoreCase("x0lumie")) {
            text = "Hello §bDeveloper!";
        } else if(user.equalsIgnoreCase("TheAdamMC")) {
            text = "Hello §bDeveloper!";
        }

        float textWidth = fr.getStringWidth(text);
        float x = sr.getScaledWidth() - textWidth - 2;
        float y = sr.getScaledHeight() - fr.FONT_HEIGHT - 2;

        fr.drawStringWithShadow(text, x, y, Color.WHITE.getRGB());
    };
}