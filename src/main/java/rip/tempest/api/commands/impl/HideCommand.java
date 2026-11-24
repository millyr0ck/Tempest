package rip.tempest.api.commands.impl;

import rip.tempest.Tempest;
import rip.tempest.api.commands.Command;
import rip.tempest.modules.Module;

public class HideCommand extends Command {

    public HideCommand() {
        super("hide", "Hides a module", ".h or .hide [module]", "h");
    }

    @Override
    public void execute(String[] args) {
        if (args.length != 1) {
            usage();
        } else {
            try {
                String moduleName = String.join(" ", args);
                Module module = Tempest.INSTANCE.getModuleManager().getModule(moduleName);
                if (moduleName.equalsIgnoreCase("all")) {
                    if (Tempest.INSTANCE.getModuleManager().getModules().stream().anyMatch(Module::isHidden)) {
                        Tempest.INSTANCE.getModuleManager().getModules().forEach(m -> m.setHidden(false));
                        sendChatWithPrefix("Unhid all modules!");
                    } else {
                        Tempest.INSTANCE.getModuleManager().getModules().forEach(m -> m.setHidden(true));
                        sendChatWithPrefix("Hid all modules!");
                    }
                } else {
                    if (module != null) {
                        module.setHidden(!module.isHidden());
                        if (module.isHidden()) {
                            sendChatWithPrefix("Hid " + module.getLabel() + "!");
                        } else {
                            sendChatWithPrefix("Unhid " + module.getLabel() + "!");
                        }
                    } else {
                        sendChatWithPrefix("Cannot find module \"" + moduleName + "\"");
                    }
                }
          
            } catch (Exception e) {
                usage();
            }
        }
    }

}