package rip.tempest.api.commands.impl;

import rip.tempest.api.commands.Command;
import rip.tempest.modules.impl.visuals.InfoModule;

public class ClientNameCommand extends Command {
    public ClientNameCommand() {
        super("clientname", "Changes the client name in watermarks", ".clientname [name]", "cn");
    }

    @Override
    public void execute(String[] args) {
        if (args.length < 1) {
            usage();
        } else {
            StringBuilder nameBuilder = new StringBuilder();
            for (int i = 0; i < args.length; i++) {
                nameBuilder.append(args[i]);
                if (i < args.length - 1) nameBuilder.append(" ");
            }
            String newName = nameBuilder.toString();

            InfoModule.name.setValue(newName);
            InfoModule.customName = newName;
            sendChatWithPrefix("Set client name to: " + newName);
        }
    }
}
