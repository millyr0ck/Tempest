package rip.tempest.api.commands.impl;

import rip.tempest.api.commands.Command;

public class HClipCommand extends Command {
    public HClipCommand() {
        super("hclip", "Teleports you forwards or backwards", ".hclip <distance>", "hc");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            usage();
            return;
        }

        try {
            double distance = Double.parseDouble(args[0]);
            if (Double.isNaN(distance) || Double.isInfinite(distance)) {
                usage();
                return;
            }
            double yaw = Math.toRadians(mc.thePlayer.rotationYaw);
            mc.thePlayer.setPosition(mc.thePlayer.posX - Math.sin(yaw) * distance, mc.thePlayer.posY, mc.thePlayer.posZ + Math.cos(yaw) * distance);
            sendChatWithPrefix("Clipped " + distance + " blocks.");
        } catch (NumberFormatException e) {
            usage();
        }
    }
}
