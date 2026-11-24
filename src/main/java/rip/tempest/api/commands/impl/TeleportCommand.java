package rip.tempest.api.commands.impl;

import rip.tempest.api.commands.Command;

public class TeleportCommand extends Command {
    public TeleportCommand() {
        super("tp", "Teleports you to coordinates", ".tp <x> <y> <z>", "teleport");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 3) {
            usage();
            return;
        }

        try {
            double x = Double.parseDouble(args[0]), y = Double.parseDouble(args[1]), z = Double.parseDouble(args[2]);
            if (Double.isNaN(x + y + z) || Double.isInfinite(x + y + z)) {
                usage();
                return;
            }
            mc.thePlayer.setPosition(x, y, z);
            sendChatWithPrefix("Teleported to " + x + ", " + y + ", " + z + ".");
        } catch (NumberFormatException e) {
            usage();
        }
    }
}
