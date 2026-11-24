package rip.tempest.api.commands.impl;

import rip.tempest.api.commands.Command;
import rip.tempest.utils.misc.DisguiseManager;

public class DisguiseCommand extends Command {
    public DisguiseCommand() {
        super("disguise",
                "Locally makes a player appear as another (skin, arm model + nametag). Only you see it.",
                ".disguise <player> <disguiseAs>  |  .disguise off [player]",
                "dg");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            usage();
            return;
        }

        if (args[0].equalsIgnoreCase("off") || args[0].equalsIgnoreCase("clear") || args[0].equalsIgnoreCase("reset")) {
            if (args.length >= 2) {
                DisguiseManager.remove(args[1]);
                sendChatWithInfo("Removed disguise on " + args[1] + ".");
            } else {
                DisguiseManager.clear();
                sendChatWithInfo("Cleared all disguises.");
            }
            return;
        }

        if (args.length < 2) {
            usage();
            return;
        }

        final String target = args[0];
        final String disguiseAs = args[1];
        DisguiseManager.disguise(target, disguiseAs);
        sendChatWithInfo("Disguising " + target + " as " + disguiseAs + " \2477(fetching skin...)");
    }
}
