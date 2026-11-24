package rip.tempest.api.commands.impl;

import rip.tempest.api.commands.Command;
import rip.tempest.modules.impl.client.MCFModule;

public class FriendCommand extends Command {

    public FriendCommand() {
        super("friend", "Excludes a player from being targeted (no name lists your friends)", ".friend [ign]");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            if (MCFModule.friendNames().isEmpty()) {
                sendChatWithPrefix("You have no friends added. Use .friend [ign]");
            } else {
                sendChatWithPrefix("Friends (" + MCFModule.friendNames().size() + "): " + String.join(", ", MCFModule.friendNames()));
            }
            return;
        }
        if (args.length != 1) {
            usage();
            return;
        }

        if (MCFModule.toggleFriend(args[0])) {
            sendChatWithPrefix("Added " + args[0] + " as a friend!");
        } else {
            sendChatWithPrefix("Removed " + args[0] + " from friends!");
        }
    }
}
