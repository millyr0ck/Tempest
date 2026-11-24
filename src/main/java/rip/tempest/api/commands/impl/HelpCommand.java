package rip.tempest.api.commands.impl;

import rip.tempest.api.commands.Command;
import rip.tempest.utils.client.Logger;

public class HelpCommand extends Command {
    public HelpCommand() {
        super("help", "Shows all available commands", ".help", "h");
    }

    @Override
    public void execute(String[] args) {
        line("§6§lTempest §f--- §6§lHelp");
        line("");
        line("§6.bind §7- Lets you bind modules to certain keys");
        line("§6.clientname §7- Lets you change the Clientname to anything on the watermark");
        line("§6.binds §7- Shows you the binds");
        line("§6.toggle §7- Lets you toggle modules");
        line("§6.config §7- Lets you load / save / delete configs");
        line("§6.disguise §7- Locally makes a player appear as another (.disguise <player> <as>)");
        line("§6.help §7- Shows all available commands");
        line("");
        line("§7Use §6.command §7or §6.c §7to execute commands");
    }

    private void line(String message) {
        Logger.chatPrint(false, message);
    }
}
