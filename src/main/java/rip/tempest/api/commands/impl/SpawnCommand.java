package rip.tempest.api.commands.impl;

import rip.tempest.api.commands.Command;
import rip.tempest.jenny.NpcManager;
import rip.tempest.jenny.entity.CharacterType;
import rip.tempest.utils.client.Logger;

public class SpawnCommand extends Command {
    public SpawnCommand() {
        super("spawn", "Spawn or despawn Jenny NPCs", ".spawn <name|list|clear>");
    }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) {
            Logger.chatPrint("§cUsage: .spawn <name|list|clear>");
            return;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("list")) {
            StringBuilder sb = new StringBuilder("§eCharacters: ");
            for (CharacterType t : CharacterType.values()) {
                sb.append("§f").append(t.id).append(" §7| ");
            }
            Logger.chatPrint(sb.toString());
            return;
        }

        if (sub.equals("clear")) {
            NpcManager.despawnAll();
            Logger.chatPrint("§aDespawned all NPCs.");
            return;
        }

        CharacterType type = CharacterType.fromName(sub);
        if (type == null) {
            Logger.chatPrint("§cUnknown character. Use §f.spawn list§c to see available characters.");
            return;
        }

        boolean removed = false;
        for (rip.tempest.jenny.entity.NpcEntity npc : NpcManager.getAll()) {
            if (npc.getCharacterType() == type) {
                NpcManager.despawn(type);
                Logger.chatPrint("§eDespawned §f" + type.id + "§e.");
                removed = true;
                break;
            }
        }
        if (!removed) {
            NpcManager.spawn(type);
            Logger.chatPrint("§aSpawned §f" + type.id + "§a. Right-click to interact.");
        }
    }
}
