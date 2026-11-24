package rip.tempest.api.commands.impl;

import rip.tempest.api.commands.Command;

public class VClipCommand extends Command {
    private static final double NEAREST_MIN = 2.0D, NEAREST_MAX = 10.0D, NEAREST_STEP = 0.5D;

    public VClipCommand() {
        super("vclip", "Teleports you up or down", ".vclip <distance|up|down>", "vc");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            usage();
            return;
        }

        if (args[0].equalsIgnoreCase("up")) {
            nearest(1);
        } else if (args[0].equalsIgnoreCase("down")) {
            nearest(-1);
        } else {
            try {
                double distance = Double.parseDouble(args[0]);
                if (Double.isNaN(distance) || Double.isInfinite(distance)) {
                    usage();
                    return;
                }
                clip(distance);
            } catch (NumberFormatException e) {
                usage();
            }
        }
    }

    private void nearest(int direction) {
        for (double i = NEAREST_MIN; i <= NEAREST_MAX; i += NEAREST_STEP) {
            double distance = i * direction;
            if (mc.theWorld.getCollidingBoundingBoxes(mc.thePlayer, mc.thePlayer.getEntityBoundingBox().offset(0.0D, distance, 0.0D)).isEmpty()) {
                clip(distance);
                return;
            }
        }
        sendChatError("No free spot within 10 blocks.");
    }

    private void clip(double distance) {
        mc.thePlayer.setPosition(mc.thePlayer.posX, mc.thePlayer.posY + distance, mc.thePlayer.posZ);
        sendChatWithPrefix("Clipped " + distance + " blocks.");
    }
}
