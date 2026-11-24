"""
Builds Tempest/cases for the Case Opening module from files already downloaded:

    <input>/data/{crates,skins,keys}.json   github.com/ByMykel/CSGO-API (MIT)
    <input>/images_full/<hash>.png          item and case icons, Steam image CDN
    <input>/keys/<hash>.png                 key icons, Steam image CDN
    <input>/rare/<name>.png                 gold tiles, github.com/ByMykel/counter-strike-image-tracker
    <input>/sounds/<name>.wav               github.com/sourcesounds/csgo

usage: python -I tools/convert_cases.py <input> Tempest/cases

Everything here is Valve's art and sound: it stays in the game folder, out of the client jar.
The sound table and the names below come from CS2's own files (game_sounds_ui.vsndevts, csgo_english.txt).
"""
import hashlib
import json
import os
import re
import shutil
import sys

# Rarity as InventoryAPI.GetItemRarity numbers it; popup_capability_decodable.js picks tile weights and reveal sounds by it.
RARITY = {"rarity_rare_weapon": 3, "rarity_mythical_weapon": 4, "rarity_legendary_weapon": 5, "rarity_ancient_weapon": 6, "rarity_ancient": 6}

# items_game.txt, image_unusual_item (community_34 reuses 33's) and csgo_english.txt, crate_community_N_unusual_itemname.
RARE_IMAGE = {3: 3, 4: 4, 8: 8, 9: 9, 11: 11, 24: 24, 33: 33, 34: 33, 35: 35}
RARE_NAME = {3: "★ Huntsman Knife ★", 4: "★ Butterfly Knife ★", 8: "★ Falchion Knife ★", 9: "★ Shadow Daggers ★",
             11: "★ Bowie Knife ★", 24: "★ Classic Knife ★", 33: "★ Kukri Knife ★", 34: "★ Kukri Knife ★"}

# Event -> (volume, file), from game_sounds_ui.vsndevts.
SOUNDS = {
    "container_weapon_fall": (.5, "case_drop_01"),
    "container_weapon_open": (.5, "case_unlock_01"),
    "container_weapon_ticker": (.2, "csgo_ui_crate_item_scroll"),
    "reveal_3": (.3, "case_reveal_rare_01"),
    "reveal_4": (.2, "case_reveal_mythical_01"),
    "reveal_5": (.3, "case_reveal_legendary_01"),
    "reveal_6": (.3, "case_reveal_ancient_01"),
    "button_press": (.3, "generic_press_01"),
}


def icon_name(url):
    return hashlib.sha1(url.encode()).hexdigest()[:16] + ".png"


def clean_description(text):
    """(body, flavour line) from a description with escaped newlines and an <i> line."""
    if not text:
        return "", ""
    text = text.replace("\\n", "\n")
    flavour = ""
    found = re.search(r"<i>(.*?)</i>", text, re.S)
    if found:
        flavour = re.sub(r"<[^>]+>", "", found.group(1)).strip()
        text = text[:found.start()] + text[found.end():]
    return re.sub(r"<[^>]+>", "", text).strip(), flavour


def main(src, out):
    crates = [c for c in json.load(open(os.path.join(src, "data", "crates.json"), encoding="utf-8")) if c.get("type") == "Case"]
    skins = {s["id"]: s for s in json.load(open(os.path.join(src, "data", "skins.json"), encoding="utf-8"))}
    keys = {}
    for key in json.load(open(os.path.join(src, "data", "keys.json"), encoding="utf-8")):
        for crate in key.get("crates") or []:
            keys[crate["id"]] = key

    for folder in ("icons", "keys", "rare", "sounds"):
        os.makedirs(os.path.join(out, folder), exist_ok=True)

    def copy_icon(url):
        name = icon_name(url)
        target = os.path.join(out, "icons", name)
        if not os.path.exists(target):
            shutil.copyfile(os.path.join(src, "images_full", name), target)
        return name

    def item(entry):
        skin = skins[entry["id"]]
        body, flavour = clean_description(skin.get("description"))
        name = entry["name"]
        weapon, _, paint = name.partition(" | ")
        return dict(name=name, weapon=weapon, paint=paint, rarity=RARITY[entry["rarity"]["id"]], color=entry["rarity"]["color"], icon=copy_icon(entry["image"]),
                    minFloat=skin.get("min_float") or 0.0, maxFloat=1.0 if skin.get("max_float") is None else skin["max_float"], hasWear=entry.get("paint_index") is not None,
                    stattrak=bool(skin.get("stattrak")), description=body, flavour=flavour, gloves=skin["category"]["id"] == "sfui_invpanel_filter_gloves",
                    knife=name.startswith("★") and skin["category"]["id"] != "sfui_invpanel_filter_gloves")

    output = []
    # Newest first, as an inventory sorted by recent would be close enough to.
    for crate in sorted(crates, key=lambda c: int(c["def_index"]), reverse=True):
        found = re.search(r"crate_community_(\d+)$", crate["original"]["image_inventory"])
        community = int(found.group(1)) if found else -1
        rare_png = "crate_community_%d_rare_item.png" % RARE_IMAGE[community] if community in RARE_IMAGE else "default_rare_item.png"
        shutil.copyfile(os.path.join(src, "rare", rare_png), os.path.join(out, "rare", rare_png))
        rare = [item(e) for e in crate["contains_rare"]]
        rare_name = RARE_NAME.get(community)
        if rare_name is None:
            rare_name = "★ Gloves ★" if rare and all(r["gloves"] for r in rare) else "★ Rare Special Item ★"
        key = keys[crate["id"]]
        key_png = icon_name(key["image"])
        shutil.copyfile(os.path.join(src, "keys", key_png), os.path.join(out, "keys", key_png))
        output.append(dict(name=crate["name"], icon=copy_icon(crate["image"]), keyName=key["name"], keyIcon=key_png, rareName=rare_name, rareIcon=rare_png,
                           items=[item(e) for e in crate["contains"]], rare=rare))

    with open(os.path.join(out, "cases.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(output, f, ensure_ascii=False, separators=(",", ":"))
    with open(os.path.join(out, "sounds", "sounds.txt"), "w", encoding="utf-8", newline="\n") as f:
        for event, (volume, file) in SOUNDS.items():
            f.write("%s %g %g 100 100 %s\n" % (event, volume, volume, file))
            shutil.copyfile(os.path.join(src, "sounds", file + ".wav"), os.path.join(out, "sounds", file + ".wav"))
    print("cases", len(output), "icons", len(os.listdir(os.path.join(out, "icons"))))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
