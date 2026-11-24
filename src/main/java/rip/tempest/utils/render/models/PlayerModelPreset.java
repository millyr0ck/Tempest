package rip.tempest.utils.render.models;

public enum PlayerModelPreset {
    POMNI("Tempest/models/pomni/model.gltf"),
    CHARIZARD("Tempest/models/charizard/model.gltf"),
    GIRL("Tempest/models/girl/model.gltf"),
    CAT("Tempest/models/cat/model.gltf"),
    AMONGUS("Tempest/models/amongus/model.gltf"),
    TUNG("Tempest/models/tung/model.gltf"),
    // KayKit Adventurers 2.0 by Kay Lousberg, CC0 (license.txt in each folder)
    KNIGHT("Tempest/models/knight/model.glb"),
    BARBARIAN("Tempest/models/barbarian/model.glb"),
    MAGE("Tempest/models/mage/model.glb"),
    RANGER("Tempest/models/ranger/model.glb"),
    ROGUE("Tempest/models/rogue/model.glb"),
    ROGUE_HOODED("Tempest/models/rogue_hooded/model.glb"),
    // Universal Base Characters (Standard) by Quaternius, CC0 (license.txt in each folder)
    SUPERHERO_MALE("Tempest/models/superhero_male/model.gltf"),
    SUPERHERO_FEMALE("Tempest/models/superhero_female/model.gltf");

    private final String path;

    PlayerModelPreset(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }
}
