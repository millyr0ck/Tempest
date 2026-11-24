package rip.tempest.api.commands.impl;

import rip.tempest.Tempest;
import rip.tempest.api.commands.Command;
import rip.tempest.api.config.Config;
import rip.tempest.utils.client.Logger;

public class ConfigCommand extends Command {

    public ConfigCommand() {
        super("config", "Saves or Loads Configs.", ".config save/load/remove [config] or .config list or .config binds save/load", "c");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            usage();
            return;
        }

        String command = Tempest.requireNonNull(args[0]);

        if (command.equalsIgnoreCase("binds")) {
            if (args.length != 2) {
                usage();
                return;
            }
            String subCommand = Tempest.requireNonNull(args[1]);
            if (subCommand.equalsIgnoreCase("save")) {
                Tempest.INSTANCE.getBindsConfig().saveToFile();
                Logger.chatPrint("Binds have been saved.");
            } else if (subCommand.equalsIgnoreCase("load")) {
                Tempest.INSTANCE.getBindsConfig().loadFromFile();
                Logger.chatPrint("Binds have been loaded.");
            } else {
                usage();
            }
        }

        if (command.equalsIgnoreCase("save")) {
            if (args.length != 2) {
                usage();
                return;
            }
            String name = Tempest.requireNonNull(args[1]);
            Tempest.INSTANCE.getConfigManager().saveConfig(name);
            Logger.chatPrint("Config '" + name + "' has been saved.");
        } else if (command.equalsIgnoreCase("load")) {
            if (args.length != 2) {
                usage();
                return;
            }
            String name = Tempest.requireNonNull(args[1]);
            Tempest.INSTANCE.getConfigManager().loadConfig(name);
            Logger.chatPrint("Config '" + name + "' has been loaded.");
        } else if (command.equalsIgnoreCase("remove")) {
            if (args.length != 2) {
                usage();
                return;
            }
            String name = Tempest.requireNonNull(args[1]);
            Tempest.INSTANCE.getConfigManager().deleteConfig(name);
            Logger.chatPrint("Config '" + name + "' has been removed.");
        } else if (command.equalsIgnoreCase("list")) {
            if (args.length != 1) {
                usage();
                return;
            }
            Logger.chatPrint("Available Configs:");
            for (Config config : Tempest.INSTANCE.getConfigManager().getElements())
                Logger.chatPrint(config.getName());
        } else {
            usage();
        }
    }
}