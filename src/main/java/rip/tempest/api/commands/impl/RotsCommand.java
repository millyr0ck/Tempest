package rip.tempest.api.commands.impl;

import rip.tempest.api.commands.Command;
import rip.tempest.processes.PlayerRecorder;

public class RotsCommand extends Command {
    public RotsCommand() {
        super("rots", "Records another player's rotations and movement to Tempest/rots", ".rots <player> | .rots stop");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            if (PlayerRecorder.isRecording()) {
                sendChatWithPrefix("Recording " + PlayerRecorder.targetName() + ". Use .rots stop to save.");
            } else {
                usage();
            }
            return;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            if (!PlayerRecorder.isRecording()) {
                sendChatError("Not recording anyone.");
                return;
            }
            for (String line : PlayerRecorder.stop()) sendChatWithPrefix(line);
            return;
        }

        String name = args[0];
        if (PlayerRecorder.isRecording()) {
            for (String line : PlayerRecorder.stop()) sendChatWithPrefix(line);
        }
        if (PlayerRecorder.start(name)) {
            sendChatWithPrefix("Recording " + name + " (starts once they're in view). Use .rots stop to save.");
        } else {
            sendChatError("Couldn't create the recording file in Tempest/rots.");
        }
    }
}
