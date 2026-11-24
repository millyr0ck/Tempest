package rip.tempest.api.commands.impl;

import rip.tempest.api.commands.Command;
import rip.tempest.processes.RotationLearnerProcess;

import java.util.List;

/** Records, loads and manages the rotation presets Kill Aura's ML rotations use. From Yuri (AGPL-3.0). */
public class RotationCommand extends Command {
    private static final String USAGE = ".rotation <record|stop|load|unload|list|delete|status> [name]";

    public RotationCommand() {
        super("rotation", "Records and loads rotation presets for Kill Aura's ML rotations", USAGE, "rot");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            usage();
            return;
        }

        switch (args[0].toLowerCase()) {
            case "record" -> {
                if (args.length < 2) {
                    sendChatError("Usage: .rotation record <name>");
                } else if (!RotationLearnerProcess.startRecording(args[1])) {
                    sendChatError("Preset names can only use letters, numbers, - and _ (up to 32).");
                } else {
                    sendChatWithPrefix("Recording how nearby players turn when they hit into '" + args[1] + "'.");
                }
            }
            case "stop" -> {
                RotationLearnerProcess.stopRecording();
                sendChatWithPrefix("Stopped recording.");
            }
            case "load" -> {
                if (args.length < 2) {
                    sendChatError("Usage: .rotation load <name>");
                } else if (RotationLearnerProcess.loadPreset(args[1])) {
                    sendChatWithPrefix("Loaded preset '" + args[1] + "'.");
                } else {
                    sendChatError("Preset '" + args[1] + "' not found or empty.");
                }
            }
            case "unload" -> {
                RotationLearnerProcess.unloadPreset();
                sendChatWithPrefix("Unloaded the active preset.");
            }
            case "list" -> {
                List<String> presets = RotationLearnerProcess.listPresets();
                sendChatWithPrefix(presets.isEmpty() ? "No presets saved." : "Presets: " + String.join(", ", presets));
            }
            case "delete" -> {
                if (args.length < 2) {
                    sendChatError("Usage: .rotation delete <name>");
                } else if (RotationLearnerProcess.deletePreset(args[1])) {
                    sendChatWithPrefix("Deleted preset '" + args[1] + "'.");
                } else {
                    sendChatError("Preset '" + args[1] + "' not found (bundled presets can't be deleted).");
                }
            }
            case "status" -> {
                String status = RotationLearnerProcess.isRecording()
                        ? "Recording to '" + RotationLearnerProcess.getActivePresetName() + "'."
                        : "Not recording.";
                status += RotationLearnerProcess.hasModelLoaded()
                        ? " Loaded: '" + RotationLearnerProcess.getLoadedModelName() + "'."
                        : " No preset loaded.";
                sendChatWithPrefix(status);
            }
            default -> usage();
        }
    }
}
