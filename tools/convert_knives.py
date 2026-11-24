"""Converts unpacked Source engine v48 knife and gun viewmodels into Tempest/knives/, with their sounds and colour masks.

Usage: python -I convert_knives.py <unpacked knife addon> <output folder> <gun addons folder> [<skin packs folder>]
The skin packs are the Workshop's "[ARC9] CS:GO <gun> Skin Pack" series, as SteamCMD leaves them (steamapps/workshop/content/4000). Their Lua is
only ever read as text, for each skin's name and texture.
The knife addon needs models/, materials/ and sound/csgo_knife/. The gun folder holds one <name>/unpacked per addon: "pack"
(Workshop 239687689, CS:GO SWEP Pack) and the skins in GUNS. Lua is never needed: SOUNDS and GUN_SOUNDS below are their sound tables.
"""
import gzip, io, lzma, math, os, re, shutil, struct, sys, tempfile
from PIL import Image, ImageFilter

# Output name -> v_csgo_* model name.
KNIVES = {"karambit": "karambit", "butterfly": "butterfly", "bayonet": "bayonet", "bowie": "bowie", "daggers": "push",
          "falchion": "falchion", "flip": "flip", "gut": "gut", "huntsman": "tactical", "m9": "m9"}
# Texture name fragments -> skin id. Order matters: "marble" before "fade".
SKIN_WORDS = [("marble", "marblefade"), ("fade", "fade"), ("web", "crimsonweb"), ("dam", "dam"), ("tiger", "tiger"),
              ("slaughter", "slaughter"), ("gamma", "gamma_doppler"), ("freehand", "freehand"), ("lore", "lore"),
              ("autotronic", "autotronic"), ("black_lam", "black_laminate")]
ARM_BONE = re.compile(r"^(v_weapon|ValveBiped)\.Bip01_R_(Forearm|Hand|Finger\d+)$")
LEFT_BONE = re.compile(r"^(v_weapon|ValveBiped)\.Bip01_L_(Forearm|Hand|Finger\d+)$")
TWO_HANDED = {"daggers"}

# Output name -> model, base texture, and skins as id -> (addon folder, path of its texture under materials/models/weapons/v_models).
GUNS = {
    "ak47": ("c_csgo_ak47", "ak47", {"redline": ("ak_redline", "rif_ak47/ak47"), "vulcan": ("ak_vulcan", "ak47vul1/ak47")}),
    "m4a4": ("c_csgo_m4a1", "rif_m4a1", {"asiimov": ("m4a4_asiimov", "m4a4asim/rif_m4a1")}),
    "m4a1s": ("c_csgo_m4a1_s", "rif_m4a1_s", {}),
    "awp": ("c_csgo_awp", "awp", {"dragonlore": ("awp_dragonlore", "snip_awp/awp")}),
    "deagle": ("c_csgo_deagle", "pist_deagle", {}),
    "glock": ("c_csgo_glock", "pist_glock18", {}),
    "usps": ("c_csgo_usp", "pist_223", {}),
    "aug": ("c_csgo_aug", "rif_aug", {}),
    "bizon": ("c_csgo_bizon", "Bizon", {}),
    "cz75": ("c_csgo_cz75", "pist_cz_75", {}),
    "famas": ("c_csgo_famas", "rif_famas", {}),
    "fiveseven": ("c_csgo_fn", "fiveseven", {}),
    "g3sg1": ("c_csgo_g3sg1", "snip_g3sg1", {}),
    "galil": ("c_csgo_galil", "rif_galilar", {}),
    "mag7": ("c_csgo_mag7", "shot_mag7", {}),
    "mp7": ("c_csgo_mp7", "smg_mp7", {}),
    "mp9": ("c_csgo_mp9", "smg_mp9", {}),
    "nova": ("c_csgo_nova", "shot_nova", {}),
    "p2000": ("c_csgo_p2000", "pist_HKP2000", {}),
    "p250": ("c_csgo_p250", "P250", {}),
    "scar20": ("c_csgo_scar", "snip_scar20", {}),
    "sg553": ("c_csgo_sg556", "rif_sg556", {}),
    "ssg08": ("c_csgo_ssg08", "snip_ssg08", {}),
    "tec9": ("c_csgo_tec9", "pist_tec9", {}),
    "ump45": ("c_csgo_ump45", "smg_ump45", {}),
    "xm1014": ("c_csgo_xm1014", "shot_xm1014", {}),
    "zeus": ("c_csgo_taser", "taser", {}),
}
# As SOUNDS, for the guns: files are in the pack's sound/csgo/<folder>/. The .fire ones are this module's, at a level that won't hurt.
GUN_SOUNDS = {
    "weapon_cak47.clipout": ((.25, .25), (100, 100), "ak47", ["ak47_clipout"]),
    "weapon_cak47.clipin": ((.25, .25), (100, 100), "ak47", ["ak47_clipin"]),
    "weapon_cak47.boltpull": ((.25, .25), (100, 100), "ak47", ["ak47_boltpull"]),
    "weapon_cak47.draw": ((.25, .25), (100, 100), "ak47", ["ak47_draw"]),
    "gun.ak47.fire": ((.6, .6), (100, 100), "ak47", ["ak47-1"]),
    "weapon_cawp.draw": ((.25, .25), (100, 100), "awp", ["awp_draw"]),
    "weapon_cawp.boltback": ((.25, .25), (100, 100), "awp", ["awp_boltback"]),
    "weapon_cawp.boltforward": ((.25, .25), (100, 100), "awp", ["awp_boltforward"]),
    "weapon_cawp.clipout": ((.25, .25), (100, 100), "awp", ["awp_clipout"]),
    "weapon_cawp.clipin": ((.25, .25), (100, 100), "awp", ["awp_clipin"]),
    "weapon_cawp.cliphit": ((.25, .25), (100, 100), "awp", ["awp_cliphit"]),
    "gun.awp.fire": ((.6, .6), (100, 100), "awp", ["awp1"]),
    "gun.awp.zoom": ((.5, .5), (100, 100), "awp", ["zoom"]),
    "weapon_cm4a1.clipout": ((.25, .25), (100, 100), "m4a1", ["m4a1_clipout"]),
    "weapon_cm4a1.clipin": ((.25, .25), (100, 100), "m4a1", ["m4a1_clipin"]),
    "weapon_cm4a1.boltforward": ((.25, .25), (100, 100), "m4a1", ["m4a1_boltforward"]),
    "weapon_cm4a1.boltback": ((.25, .25), (100, 100), "m4a1", ["m4a1_boltback"]),
    "weapon_cm4a1.draw": ((.25, .25), (100, 100), "m4a1", ["m4a1_draw"]),
    "gun.m4a4.fire": ((.6, .6), (100, 100), "m4a1", ["m4a1_unsil-1"]),
    "gun.m4a1s.fire": ((.6, .6), (100, 100), "m4a1", ["m4a1-1"]),
    "weapon_cdeagle.draw": ((.25, .25), (100, 100), "deagle", ["de_draw"]),
    "weapon_cdeagle.clipout": ((.25, .25), (100, 100), "deagle", ["de_clipout"]),
    "weapon_cdeagle.clipin": ((.25, .25), (100, 100), "deagle", ["de_clipin"]),
    "weapon_cdeagle.slideback": ((.25, .25), (100, 100), "deagle", ["de_slideback"]),
    "weapon_cdeagle.slideforward": ((.25, .25), (100, 100), "deagle", ["de_slideforward"]),
    "gun.deagle.fire": ((.6, .6), (100, 100), "deagle", ["deagle-1"]),
    "weapon_cglock.draw": ((.25, .25), (100, 100), "glock18", ["glock_draw"]),
    "weapon_cglock.clipout": ((.25, .25), (100, 100), "glock18", ["glock_clipout"]),
    "weapon_cglock.clipin": ((.25, .25), (100, 100), "glock18", ["glock_clipin"]),
    "weapon_cglock.slideback": ((.25, .25), (100, 100), "glock18", ["glock_slideback"]),
    "weapon_cglock.sliderelease": ((.25, .25), (100, 100), "glock18", ["glock_sliderelease"]),
    "gun.glock.fire": ((.6, .6), (100, 100), "glock18", ["glock18-1"]),
    "weapon_cusp.draw": ((.25, .25), (100, 100), "usp", ["usp_draw"]),
    "weapon_cusp.clipout": ((.25, .25), (100, 100), "usp", ["usp_clipout"]),
    "weapon_cusp.clipin": ((.25, .25), (100, 100), "usp", ["usp_clipin"]),
    "weapon_cusp.slideback": ((.25, .25), (100, 100), "usp", ["usp_slideback"]),
    "weapon_cusp.sliderelease": ((.25, .25), (100, 100), "usp", ["usp_sliderelease"]),
    "gun.usps.fire": ((.6, .6), (100, 100), "usp", ["usp1"]),
}


def _gun_sounds(event, group, prefix, names):
    """Mechanical sounds, at the pack's level, named as the models' events ask for them. A pair is (event suffix, file suffix)."""
    for name in names:
        suffix, file = name if isinstance(name, tuple) else (name, name)
        GUN_SOUNDS["%s.%s" % (event, suffix)] = ((.25, .25), (100, 100), group, ["%s_%s" % (prefix, file)])


_CLIP = ["clipin", "clipout", "draw"]
_gun_sounds("weapon_caug", "aug", "aug", _CLIP + ["boltpull", "boltrelease", "cliphit"])
_gun_sounds("weapon_cbizon", "bizon", "bizon", _CLIP + ["boltback", "boltforward"])
_gun_sounds("weapon_cfamas", "famas", "famas", _CLIP + ["boltback", "boltforward", "cliphit"])
_gun_sounds("weapon_cfn", "fiveseven", "fiveseven", _CLIP + ["slideback", "sliderelease"])
_gun_sounds("weapon_cg3sg1", "g3sg1", "g3sg1", _CLIP + ["slideback", "slideforward"])
_gun_sounds("weapon_cgalil", "galilar", "galil", _CLIP + ["boltback", "boltforward"])
_gun_sounds("weapon_cmag7", "mag7", "mag7", _CLIP + [("pumpback", "pump_back"), ("pumpforward", "pump_forward")])
_gun_sounds("weapon_cmp7", "mp7", "mp7", _CLIP + ["slideback", "slideforward"])
_gun_sounds("weapon_cmp9", "mp9", "mp9", _CLIP + [("slideback", "boltback"), ("slideforward", "boltforward")])
_gun_sounds("weapon_cnova", "nova", "nova", ["draw", "insertshell", "pump", ("pumpduringshot", "pump")])
_gun_sounds("weapon_cp2000", "hkp2000", "hkp2000", _CLIP + ["slideback", "sliderelease"])
_gun_sounds("weapon_cp250", "p250", "p250", _CLIP + ["slideback", "sliderelease"])
_gun_sounds("weapon_cscar", "scar20", "scar20", _CLIP + ["boltback", "boltforward"])
_gun_sounds("weapon_csg556", "sg556", "sg556", _CLIP + ["boltback", "boltforward"])
_gun_sounds("weapon_cssg08", "ssg08", "ssg08", _CLIP + ["boltback", "boltforward", "cliphit"])
_gun_sounds("weapon_ctaser", "taser", "taser", ["draw"])
_gun_sounds("weapon_ctec9", "tec9", "tec9", _CLIP + ["boltpull", "boltrelease"])
_gun_sounds("weapon_cump45", "ump45", "ump45", _CLIP + ["boltback", "boltforward"])
_gun_sounds("weapon_cxm1014", "xm1014", "xm1014", ["draw", "insertshell"])
for _name, _group, _file in (("aug", "aug", "aug-1"), ("bizon", "bizon", "bizon-1"), ("cz75", "cz75a", "cz75a-1"), ("famas", "famas", "famas-1"),
                             ("fiveseven", "fiveseven", "fiveseven-1"), ("g3sg1", "g3sg1", "g3sg1-1"), ("galil", "galilar", "galil-1"), ("mp7", "mp7", "mp7-1"),
                             ("mp9", "mp9", "mp9-1"), ("nova", "nova", "nova-1"), ("p2000", "hkp2000", "hkp2000-1"), ("p250", "p250", "p250-1"),
                             ("scar20", "scar20", "scar20_unsil-1"), ("sg553", "sg556", "sg556-1"), ("ssg08", "ssg08", "ssg08-1"), ("tec9", "tec9", "tec9-1"),
                             ("ump45", "ump45", "ump45-1"), ("xm1014", "xm1014", "xm1014-1"), ("zeus", "taser", "taser_shoot")):
    GUN_SOUNDS["gun.%s.fire" % _name] = ((.6, .6), (100, 100), _group, [_file])
# The MAG-7's model fires its own shot sound, on its first frame.
GUN_SOUNDS["weapon_cmag7.shoot"] = ((.6, .6), (100, 100), "mag7", ["mag7-1"])
GUN_SOUNDS["gun.aug.zoom"] = ((.5, .5), (100, 100), "aug", ["aug_zoom_in"])
GUN_SOUNDS["gun.sg553.zoom"] = ((.5, .5), (100, 100), "sg556", ["sg556_zoom_in"])
# The pack has no inspect animations, so guns get this one: the whole view turns as one piece, as (seconds, yaw, pitch, roll, x, y, z).
GUN_INSPECT = [(0, 0, 0, 0, 0, 0, 0), (.6, 24, -6, -38, -2, 1, 1), (1.9, 28, -4, -46, -2, 1, 1.5), (2.7, 16, -5, 30, -2, 0, 1), (3.8, 12, -3, 38, -2, 0, 1.5), (4.5, 0, 0, 0, 0, 0, 0)]
GUN_INSPECT_FPS = 30

# Soundscript -> (volume range, pitch range in percent, WAVs), from the addon's csgo_knife_snd_init.lua.
SOUNDS = {
    "csgo_knife.deploy": ((.4, .4), (100, 100), ["knife_deploy1"]),
    "csgo_knife.hit": ((1, 1), (100, 100), ["knife_hit1", "knife_hit2", "knife_hit3", "knife_hit4"]),
    "csgo_knife.hitwall": ((1, 1), (100, 100), ["knife_hit_01", "knife_hit_02", "knife_hit_03", "knife_hit_04", "knife_hit_05"]),
    "csgo_knife.slash": ((.5, 1), (97, 105), ["knife_slash1", "knife_slash2"]),
    "csgo_knife.stab": ((1, 1), (100, 100), ["knife_stab"]),
    "csgo_butterflyknife.backstab01": ((.4, .4), (100, 100), ["bknife_backstab01"]),
    "csgo_butterflyknife.backstab02": ((.4, .4), (100, 100), ["bknife_backstab02"]),
    "csgo_butterflyknife.draw01": ((.6, .6), (100, 100), ["bknife_draw01"]),
    "csgo_butterflyknife.draw02": ((.6, .6), (100, 100), ["bknife_draw02"]),
    "csgo_butterflyknife.look01_a": ((.4, .4), (100, 100), ["bknife_look01_a"]),
    "csgo_butterflyknife.look01_b": ((.4, .4), (100, 100), ["bknife_look01_b"]),
    "csgo_butterflyknife.look02_a": ((.4, .4), (100, 100), ["bknife_look02_a"]),
    "csgo_butterflyknife.look02_b": ((.4, .4), (100, 100), ["bknife_look02_b"]),
    "csgo_butterflyknife.look03_a": ((.4, .4), (100, 100), ["bknife_look03_a"]),
    "csgo_butterflyknife.look03_b": ((.4, .4), (100, 100), ["bknife_look03_b"]),
    "csgo_knifefalchion.inspect": ((1, 1), (100, 100), ["knife_falchion_inspect"]),
    "csgo_knifefalchion.draw": ((.4, .9), (97, 105), ["knife_falchion_draw"]),
    "csgo_knifefalchion.catch": ((.3, .7), (97, 105), ["knife_falchion_catch"]),
    "csgo_weapon.weaponmove1": ((.15, .15), (100, 100), ["movement1"]),
    "csgo_weapon.weaponmove2": ((.15, .15), (100, 100), ["movement2"]),
    "csgo_weapon.weaponmove3": ((.15, .15), (100, 100), ["movement3"]),
    "csgo_knifepush.attack1heavy": ((.1, .2), (98, 105), ["knife_push_attack1_heavy_01", "knife_push_attack1_heavy_02", "knife_push_attack1_heavy_03", "knife_push_attack1_heavy_04"]),
    "csgo_knifepush.lookatstart": ((.2, .2), (100, 100), ["knife_push_lookat_start"]),
    "csgo_knifepush.lookatend": ((.2, .2), (100, 100), ["knife_push_lookat_end"]),
    "csgo_knifepush.draw": ((.3, .3), (100, 100), ["knife_push_draw"]),
    "knifebowie.draw": ((.7, .8), (99, 100), ["knife_bowie_draw"]),
    "knifebowie.lookatstart": ((.2, .2), (99, 100), ["knife_bowie_inspect_start"]),
    "knifebowie.lookatend": ((.2, .3), (99, 101), ["knife_bowie_inspect_end"]),
}
# Studiomdl's event number for "play this soundscript".
EVENT_SOUND = 5004
EVENT_MUZZLE = 5001

# Which mesh islands are the blade: indexes into islands(), biggest first. Picked by eye from a UV sheet of each knife.
BLADE_ISLANDS = {"karambit": [2], "butterfly": [0], "bayonet": [3], "bowie": [1], "falchion": [2], "flip": [1], "gut": [2], "huntsman": [1], "m9": [0]}
# Shadow Daggers are all one island: the blade is whatever is past the crossbar, as (axis, position), on the negative side.
BLADE_CUTS = {"daggers": (1, -.5)}
MASK_SIZE = 1024


def i32(d, o): return struct.unpack_from("<i", d, o)[0]
def i16(d, o): return struct.unpack_from("<h", d, o)[0]
def u16(d, o): return struct.unpack_from("<H", d, o)[0]
def f32(d, o): return struct.unpack_from("<f", d, o)[0]
def vec(d, o, n=3): return struct.unpack_from("<%df" % n, d, o)
def cstr(d, o): return d[o:d.index(b"\0", o)].decode("latin1")


def half(v):
    s = -1.0 if v & 0x8000 else 1.0
    e = (v >> 10) & 0x1f
    m = v & 0x3ff
    if e == 0: return s * m * 2.0 ** -24
    if e == 31: return float("inf")
    return s * (1 + m / 1024.0) * 2.0 ** (e - 15)


def quat48(d, o):
    a, b, c = struct.unpack_from("<HHH", d, o)
    x = (a - 32768) / 32768.0
    y = (b - 32768) / 32768.0
    z = ((c & 0x7fff) - 16384) / 16384.0
    w = math.sqrt(max(0.0, 1 - x * x - y * y - z * z))
    return (x, y, z, -w if c & 0x8000 else w)


def quat64(d, o):
    v = struct.unpack_from("<Q", d, o)[0]
    x = ((v & 0x1fffff) - 1048576) / 1048576.5
    y = (((v >> 21) & 0x1fffff) - 1048576) / 1048576.5
    z = (((v >> 42) & 0x1fffff) - 1048576) / 1048576.5
    w = math.sqrt(max(0.0, 1 - x * x - y * y - z * z))
    return (x, y, z, -w if v >> 63 else w)


def euler_quat(a):
    sr, cr = math.sin(a[0] * .5), math.cos(a[0] * .5)
    sp, cp = math.sin(a[1] * .5), math.cos(a[1] * .5)
    sy, cy = math.sin(a[2] * .5), math.cos(a[2] * .5)
    return (sr * cp * cy - cr * sp * sy,
            cr * sp * cy + sr * cp * sy,
            cr * cp * sy - sr * sp * cy,
            cr * cp * cy + sr * sp * sy)


def anim_value(d, o, frame, scale):
    k = frame
    while True:
        valid, total = d[o], d[o + 1]
        if total == 0:
            raise ValueError("bad anim value run")
        if total > k:
            break
        k -= total
        o += (valid + 1) * 2
    return i16(d, o + (k + 1) * 2 if valid > k else o + valid * 2) * scale


def read_bones(d):
    bones = []
    n, off = i32(d, 156), i32(d, 160)
    for k in range(n):
        o = off + k * 216
        bones.append(dict(name=cstr(d, o + i32(d, o)), parent=i32(d, o + 4), pos=vec(d, o + 32),
                          quat=vec(d, o + 44, 4), rot=vec(d, o + 60), posscale=vec(d, o + 72),
                          rotscale=vec(d, o + 84), pose=vec(d, o + 96, 12)))
    return bones


def read_records(d, p):
    """Maps bone -> (record offset, flags) for one anim section."""
    out = {}
    while True:
        bone, flags, nxt = d[p], d[p + 1], i16(d, p + 2)
        if bone != 255:
            out[bone] = (p, flags)
        if nxt == 0 or bone == 255:
            return out
        p += nxt


def bake_anim(d, off, bones):
    num_frames, flags = i32(d, off + 16), i32(d, off + 12)
    if i32(d, off + 52) != 0:
        raise ValueError("external animblocks are not supported")
    anim_index, section_index, section_frames = i32(d, off + 56), i32(d, off + 80), i32(d, off + 84)
    frames = []
    for frame in range(num_frames):
        p, local = off + anim_index, frame
        if section_frames:
            if num_frames > section_frames and frame == num_frames - 1:
                section, local = num_frames // section_frames + 1, 0
            else:
                section = frame // section_frames
                local = frame - section * section_frames
            p = off + i32(d, off + section_index + section * 8 + 4)
        records = read_records(d, p) if p != off else {}
        pose = []
        for b, bone in enumerate(bones):
            rec = records.get(b)
            if rec is None:
                pose.append(bone["pos"] + bone["quat"])
                continue
            r, fl = rec
            data = r + 4
            if fl & 0x10:
                raise ValueError("delta bone in a viewmodel sequence")
            if fl & 0x02:
                q = quat48(d, data)
            elif fl & 0x20:
                q = quat64(d, data)
            elif fl & 0x08:
                ang = []
                for j in range(3):
                    v = u16(d, data + j * 2)
                    ang.append((anim_value(d, data + v, local, bone["rotscale"][j]) if v else 0.0) + bone["rot"][j])
                q = euler_quat(ang)
            else:
                q = bone["quat"]
            if fl & 0x01:
                skip = (6 if fl & 0x02 else 0) + (8 if fl & 0x20 else 0)
                t = tuple(half(u16(d, data + skip + j * 2)) for j in range(3))
            elif fl & 0x04:
                pv = data + (6 if fl & 0x08 else 0)
                t = []
                for j in range(3):
                    v = u16(d, pv + j * 2)
                    t.append((anim_value(d, pv + v, local, bone["posscale"][j]) if v else 0.0) + bone["pos"][j])
                t = tuple(t)
            else:
                t = bone["pos"]
            pose.append(tuple(t) + tuple(q))
        frames.append(pose)
    return frames


def read_sequences(d, bones):
    anims_off = i32(d, 184)
    seqs = []
    for k in range(i32(d, 188)):
        o = i32(d, 192) + k * 212
        name = cstr(d, o + i32(d, o + 4))
        anim = i16(d, o + i32(d, o + 60))
        off = anims_off + anim * 100
        events = []
        for j in range(i32(d, o + 24)):
            e = o + i32(d, o + 28) + j * 80
            if i32(d, e + 4) == EVENT_SOUND:
                sound = cstr(d, e + 12).lower()
                if sound not in SOUNDS and sound not in GUN_SOUNDS:
                    raise ValueError("%s uses unknown sound %s" % (name, sound))
                events.append((f32(d, e), sound))
            elif i32(d, e + 4) == EVENT_MUZZLE:
                events.append((f32(d, e), "@muzzle"))
        seqs.append(dict(name=name, fps=f32(d, off + 8), frames=bake_anim(d, off, bones), events=events))
    return seqs


def read_mesh(mdl, vvd, vtx, lens_materials=False):
    """With lens_materials, vertices of any mesh past material 0 get the UV (8, 8), which the shader draws as dark glass."""
    if i32(vvd, 12) < 1:
        raise ValueError("no vvd lods")
    fixups, fix_off, data_off = i32(vvd, 48), i32(vvd, 52), i32(vvd, 56)
    count = i32(vvd, 16)
    raw = []
    if fixups:
        for f in range(fixups):
            o = fix_off + f * 12
            start, n = i32(vvd, o + 4), i32(vvd, o + 8)
            raw.extend(range(start, start + n))
    else:
        raw = list(range(count))

    def vert(index):
        o = data_off + raw[index] * 48
        return dict(w=vec(vvd, o, 3), b=(vvd[o + 12], vvd[o + 13], vvd[o + 14]), num=vvd[o + 15],
                    p=vec(vvd, o + 16), n=vec(vvd, o + 28), uv=vec(vvd, o + 40, 2))

    verts, remap, tris = [], {}, []
    bp_off = i32(mdl, 236)
    vtx_bp = i32(vtx, 32)
    for bp in range(i32(mdl, 232)):
        o = bp_off + bp * 16
        vb = vtx_bp + bp * 8
        models = o + i32(mdl, o + 12)
        vmodels = vb + i32(vtx, vb + 4)
        for m in range(i32(mdl, o + 4)):
            mo = models + m * 148
            vm = vmodels + m * 8
            lod = vm + i32(vtx, vm + 4)
            meshes = mo + i32(mdl, mo + 76)
            vmeshes = lod + i32(vtx, lod + 4)
            model_base = i32(mdl, mo + 84) // 48
            for me in range(i32(mdl, mo + 72)):
                mesh_base = i32(mdl, meshes + me * 116 + 12)
                lens = lens_materials and i32(mdl, meshes + me * 116) > 0
                vme = vmeshes + me * 9
                groups = vme + i32(vtx, vme + 4)
                for g in range(i32(vtx, vme)):
                    sg = groups + g * 25
                    nverts, voff, nidx, ioff = i32(vtx, sg), i32(vtx, sg + 4), i32(vtx, sg + 8), i32(vtx, sg + 12)
                    strips, soff = i32(vtx, sg + 16), i32(vtx, sg + 20)
                    def resolve(ix):
                        orig = u16(vtx, sg + voff + ix * 9 + 4)
                        key = model_base + mesh_base + orig
                        if key not in remap:
                            remap[key] = len(verts)
                            verts.append(vert(key))
                            if lens:
                                verts[-1]["uv"] = (8.0, 8.0)
                        return remap[key]
                    for s in range(strips):
                        st = sg + soff + s * 27
                        sn, si, flags = i32(vtx, st), i32(vtx, st + 4), vtx[st + 18]
                        idx = [u16(vtx, sg + ioff + (si + q) * 2) for q in range(sn)]
                        if flags & 2:
                            for q in range(len(idx) - 2):
                                t = (idx[q], idx[q + 1], idx[q + 2])
                                tris.extend(resolve(i) for i in (t if q % 2 == 0 else (t[1], t[0], t[2])))
                        else:
                            tris.extend(resolve(i) for i in idx)
    return verts, tris


def vtf_image(path):
    d = open(path, "rb").read()
    if d[:4] != b"VTF\0":
        raise ValueError(path)
    w, h, fmt = u16(d, 16), u16(d, 18), i32(d, 52)
    # Uncompressed: the largest mip is last in the file.
    raw = {0: ("RGBA", "RGBA", 4), 1: ("RGBA", "ABGR", 4), 2: ("RGB", "RGB", 3), 3: ("RGB", "BGR", 3), 12: ("RGBA", "BGRA", 4)}.get(fmt)
    if raw is not None:
        return Image.frombytes(raw[0], (w, h), d[-w * h * raw[2]:], "raw", raw[1])
    fourcc = {13: b"DXT1", 15: b"DXT5"}.get(fmt)
    if fourcc is None:
        raise ValueError("%s: unsupported format %d" % (path, fmt))
    size = max(1, (w + 3) // 4) * max(1, (h + 3) // 4) * (8 if fmt == 13 else 16)
    top = d[-size:]
    dds = bytearray(128)
    struct.pack_into("<4sIIIII", dds, 0, b"DDS ", 124, 0x81007, h, w, size)
    struct.pack_into("<II4s", dds, 76, 32, 4, fourcc)
    struct.pack_into("<I", dds, 108, 0x1000)
    return Image.open(io.BytesIO(bytes(dds) + top)).convert("RGBA")


def vmt_value(text, key):
    m = re.search(r'^\s*"\$%s"\s+"?([^"\n]+)"?' % key, text, re.I | re.M)
    return m.group(1).strip() if m else None


def numbers(value, fallback):
    if value is None:
        return fallback
    found = [float(x) for x in re.findall(r"-?\d*\.?\d+", value)]
    return found if len(found) == len(fallback) else fallback


def utf(s):
    b = s.encode()
    return struct.pack("<H", len(b)) + b


def skin_id(name):
    for word, skin in SKIN_WORDS:
        if word in name:
            return skin
    return "vanilla"


def islands(verts, tris):
    """Groups of triangle offsets that touch, biggest first. UV seams split vertices, so they're welded by position too."""
    parent = list(range(len(verts)))
    def find(a):
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a
    seen = {}
    for k, v in enumerate(verts):
        key = tuple(round(c, 3) for c in v["p"])
        if key in seen:
            parent[find(k)] = find(seen[key])
        else:
            seen[key] = k
    for t in range(0, len(tris), 3):
        a = find(tris[t])
        parent[find(tris[t + 1])] = a
        parent[find(tris[t + 2])] = a
    groups = {}
    for t in range(0, len(tris), 3):
        groups.setdefault(find(tris[t]), []).append(t)
    return sorted(groups.values(), key=lambda g: -len(g))


def blade_mask(verts, tris, knife):
    """UV-space mask: red is 255 on the blade, green is how far along it (0 at the hilt, 255 at the tip)."""
    if knife in GUNS:
        chosen = set(range(0, len(tris), 3))
        is_blade = None
        blade_points = [v["p"] for v in verts]
        other = []
    elif knife in BLADE_CUTS:
        cut_axis, cut = BLADE_CUTS[knife]
        chosen = None
        is_blade = lambda p: p[cut_axis] < cut
        blade_points = [v["p"] for v in verts if is_blade(v["p"])]
        other = [v["p"] for v in verts if not is_blade(v["p"])]
    else:
        groups = islands(verts, tris)
        chosen = {t for n in BLADE_ISLANDS[knife] for t in groups[n]}
        is_blade = None
        blade_points = [verts[tris[t + k]]["p"] for t in chosen for k in range(3)]
        other = [verts[tris[t + k]]["p"] for t in range(0, len(tris), 3) if t not in chosen for k in range(3)]
    low = [min(p[a] for p in blade_points) for a in range(3)]
    high = [max(p[a] for p in blade_points) for a in range(3)]
    axis = max(range(3), key=lambda a: high[a] - low[a])
    middle = sum(p[axis] for p in other) / len(other) if other else low[axis]
    start, end = (low[axis], high[axis]) if abs(low[axis] - middle) < abs(high[axis] - middle) else (high[axis], low[axis])

    size = MASK_SIZE
    red, green, covered = bytearray(size * size), bytearray(size * size), bytearray(size * size)
    for t in range(0, len(tris), 3):
        a, b, c = verts[tris[t]], verts[tris[t + 1]], verts[tris[t + 2]]
        ax, ay = a["uv"][0] * size, a["uv"][1] * size
        bx, by = b["uv"][0] * size, b["uv"][1] * size
        cx, cy = c["uv"][0] * size, c["uv"][1] * size
        area = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
        if abs(area) < 1e-6:
            continue
        if a["uv"][0] > 6.0:
            continue
        in_island = chosen is None or t in chosen
        for y in range(int(min(ay, by, cy)) - 1, int(max(ay, by, cy)) + 2):
            for x in range(int(min(ax, bx, cx)) - 1, int(max(ax, bx, cx)) + 2):
                px, py = x + .5, y + .5
                w1 = ((px - ax) * (cy - ay) - (cx - ax) * (py - ay)) / area
                w2 = ((bx - ax) * (py - ay) - (px - ax) * (by - ay)) / area
                w0 = 1 - w1 - w2
                if w0 < -.02 or w1 < -.02 or w2 < -.02:
                    continue
                at = (y % size) * size + x % size
                covered[at] = 1
                if not in_island:
                    continue
                p = tuple(a["p"][k] * w0 + b["p"][k] * w1 + c["p"][k] * w2 for k in range(3))
                if chosen is None and not is_blade(p):
                    continue
                red[at] = 255
                green[at] = max(0, min(255, int(255 * (p[axis] - start) / (end - start))))

    # Grow past the triangle edges so texture filtering can't darken them, but never onto another part of the knife.
    grown_r = Image.frombytes("L", (size, size), bytes(red)).filter(ImageFilter.MaxFilter(5))
    grown_g = Image.frombytes("L", (size, size), bytes(green)).filter(ImageFilter.MaxFilter(5))
    keep = Image.frombytes("L", (size, size), bytes(255 if red[k] or not covered[k] else 0 for k in range(size * size)))
    black = Image.new("L", (size, size), 0)
    return Image.merge("RGB", (Image.composite(grown_r, black, keep), Image.composite(grown_g, black, keep), black))


def write_sounds(src, guns, out):
    folder = os.path.join(out, "sounds")
    os.makedirs(folder, exist_ok=True)
    with open(os.path.join(folder, "sounds.txt"), "w", encoding="utf-8", newline="\n") as f:
        for name, (volume, pitch, files) in SOUNDS.items():
            f.write("%s %g %g %g %g %s\n" % (name, volume[0], volume[1], pitch[0], pitch[1], " ".join(files)))
            for file in files:
                shutil.copyfile(os.path.join(src, "sound", "csgo_knife", file + ".wav"), os.path.join(folder, file + ".wav"))
        for name, (volume, pitch, group, files) in GUN_SOUNDS.items():
            f.write("%s %g %g %g %g %s\n" % (name, volume[0], volume[1], pitch[0], pitch[1], " ".join(files)))
            for file in files:
                shutil.copyfile(os.path.join(guns, "pack", "unpacked", "sound", "csgo", group, file + ".wav"), os.path.join(folder, file + ".wav"))


def quat_mul(a, b):
    return (a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2])


def quat_rotate(q, v):
    u = q[:3]
    t = (2 * (u[1] * v[2] - u[2] * v[1]), 2 * (u[2] * v[0] - u[0] * v[2]), 2 * (u[0] * v[1] - u[1] * v[0]))
    return (v[0] + q[3] * t[0] + u[1] * t[2] - u[2] * t[1],
            v[1] + q[3] * t[1] + u[2] * t[0] - u[0] * t[2],
            v[2] + q[3] * t[2] + u[0] * t[1] - u[1] * t[0])


def gun_inspect(bones, rest):
    """A made-up inspect: every root bone of the first idle frame turns about the gun, so the arms and gun move as one."""
    pivot = next(rest[i][:3] for i, b in enumerate(bones) if b["parent"] < 0 and b["name"].lower() == "frame")
    frames = []
    for frame in range(int(GUN_INSPECT[-1][0] * GUN_INSPECT_FPS) + 1):
        t = frame / GUN_INSPECT_FPS
        k = next(n for n in range(len(GUN_INSPECT) - 1) if t <= GUN_INSPECT[n + 1][0] or n == len(GUN_INSPECT) - 2)
        a, c = GUN_INSPECT[k], GUN_INSPECT[k + 1]
        u = min(1.0, (t - a[0]) / (c[0] - a[0]))
        u = u * u * (3 - 2 * u)
        yaw, pitch, roll, dx, dy, dz = (a[n] + (c[n] - a[n]) * u for n in range(1, 7))
        half = lambda deg: (math.sin(math.radians(deg) / 2), math.cos(math.radians(deg) / 2))
        sy, cy = half(yaw)
        sp, cp = half(pitch)
        sr, cr = half(roll)
        q = quat_mul(quat_mul((0, 0, sy, cy), (0, sp, 0, cp)), (sr, 0, 0, cr))
        pose = []
        for i, b in enumerate(bones):
            if b["parent"] >= 0:
                pose.append(rest[i])
                continue
            r = quat_rotate(q, tuple(rest[i][n] - pivot[n] for n in range(3)))
            pose.append((r[0] + pivot[0] + dx, r[1] + pivot[1] + dy, r[2] + pivot[2] + dz) + quat_mul(q, rest[i][3:]))
        frames.append(pose)
    return dict(name="lookat01", fps=float(GUN_INSPECT_FPS), frames=frames, events=[(0.02, "csgo_weapon.weaponmove1"), (.82, "csgo_weapon.weaponmove3")])


def read_model(base, knife):
    """Starts the output with the skeleton, mesh and animations."""
    mdl = open(base + ".mdl", "rb").read()
    vvd = open(base + ".vvd", "rb").read()
    vtx = open(base + ".dx90.vtx", "rb").read()
    if mdl[:4] != b"IDST" or i32(mdl, 4) != 48:
        raise ValueError("expected a v48 model")
    bones = read_bones(mdl)
    verts, tris = read_mesh(mdl, vvd, vtx, knife in GUNS)
    used = {b for v in verts for b, w in zip(v["b"], v["w"]) if w > 0}
    two_handed = knife in TWO_HANDED or knife in GUNS
    keep = set(used) | {i for i, b in enumerate(bones) if ARM_BONE.match(b["name"]) or (two_handed and LEFT_BONE.match(b["name"]))}
    for i in list(keep):
        while bones[i]["parent"] >= 0:
            i = bones[i]["parent"]
            keep.add(i)
    order = sorted(keep)
    index = {b: n for n, b in enumerate(order)}
    print(knife, "bones", len(bones), "->", len(order), "mesh bones",
          [bones[b]["name"] for b in sorted(used)], "verts", len(verts), "tris", len(tris) // 3)

    w = io.BytesIO()
    w.write(b"TKNF" + struct.pack("<i", 4))
    w.write(struct.pack("<i", len(order)))
    for b in order:
        bone = bones[b]
        w.write(utf(bone["name"]))
        w.write(struct.pack("<i", index[bone["parent"]] if bone["parent"] >= 0 else -1))
        w.write(struct.pack("<12f", *bone["pose"]))
    w.write(struct.pack("<i", len(verts)))
    for v in verts:
        weights = list(v["w"])
        ids = [index.get(b, 0) if weights[j] > 0 else 0 for j, b in enumerate(v["b"])]
        w.write(struct.pack("<8f3f3B", *v["p"], *v["n"], *v["uv"], *weights, *ids))
    w.write(struct.pack("<i", len(tris)))
    w.write(struct.pack("<%di" % len(tris), *tris))
    seqs = read_sequences(mdl, bones)
    if knife in GUNS:
        for seq in seqs:
            if seq["name"] == "idle":
                seq["name"] = "idle1"
                seqs.append(gun_inspect(bones, seq["frames"][0]))
                break
    w.write(struct.pack("<i", len(seqs)))
    for seq in seqs:
        w.write(utf(seq["name"]))
        w.write(struct.pack("<fi", seq["fps"], len(seq["frames"])))
        for pose in seq["frames"]:
            for b in order:
                w.write(struct.pack("<7f", *pose[b]))
    print("  sequences", [(s["name"], len(s["frames"])) for s in seqs])
    return w, mdl, bones, index, verts, tris, seqs


def finish(w, out, folder, knife, skins, verts, tris, seqs, muzzle):
    """Ends the output: skins, sound events, the muzzle point, the colour mask."""
    w.write(struct.pack("<i", len(skins)))
    for skin, png, exp, boost, albedo, exponent, fres, env in skins:
        w.write(utf(skin) + utf(png) + utf(exp))
        w.write(struct.pack("<9f", boost, albedo, exponent, *fres, *env))
    print("  skins", [(s[0], s[3], s[4], s[6], s[7]) for s in skins])

    events = [(seq["name"], cycle, sound) for seq in seqs for cycle, sound in seq["events"]]
    w.write(struct.pack("<i", len(events)))
    for name, cycle, sound in events:
        w.write(utf(name) + struct.pack("<f", cycle) + utf(sound))

    w.write(struct.pack("<i3f", *muzzle))
    blade_mask(verts, tris, knife).save(os.path.join(folder, "mask.png"), compress_level=9)

    with gzip.open(os.path.join(out, knife + ".knife"), "wb", compresslevel=6) as f:
        f.write(w.getvalue())
    print("  file MB", round(os.path.getsize(os.path.join(out, knife + ".knife")) / 1e6, 2))


def convert(src, out, knife, model):
    w, mdl, bones, index, verts, tris, seqs = read_model(os.path.join(src, "models", "weapons", "v_csgo_" + model), knife)
    mats = os.path.join(src, "materials", "models", "csgo_knife")
    folder = os.path.join(out, knife)
    os.makedirs(folder, exist_ok=True)
    skins = []
    exp_done = set()
    tex_count, tex_off = i32(mdl, 204), i32(mdl, 208)
    for t in range(tex_count):
        name = cstr(mdl, tex_off + t * 64 + i32(mdl, tex_off + t * 64))
        vmt = os.path.join(mats, name + ".vmt")
        if not os.path.exists(vmt):
            continue
        text = re.sub(r"//[^\n]*", "", open(vmt, encoding="latin1").read())
        skin = skin_id(name)
        base_tex = (vmt_value(text, "basetexture") or name).replace("\\", "/").split("/")[-1]
        vtf_image(os.path.join(mats, base_tex + ".vtf")).save(os.path.join(folder, skin + ".png"), compress_level=6)
        exp_tex = vmt_value(text, "phongexponenttexture")
        exp_file = ""
        if exp_tex:
            exp_tex = exp_tex.replace("\\", "/").split("/")[-1]
            exp_file = "exp_" + exp_tex + ".png"
            if exp_tex not in exp_done:
                img = vtf_image(os.path.join(mats, exp_tex + ".vtf"))
                if img.width > 512:
                    img = img.resize((512, 512), Image.LANCZOS)
                img.save(os.path.join(folder, exp_file), compress_level=6)
                exp_done.add(exp_tex)
        boost = numbers(vmt_value(text, "phongboost"), [1.0])[0]
        albedo = numbers(vmt_value(text, "phongalbedoboost"), [1.0])[0]
        exponent = numbers(vmt_value(text, "phongexponent"), [5.0])[0]
        fres = numbers(vmt_value(text, "phongfresnelranges"), [.05, .5, 1.0])
        env = numbers(vmt_value(text, "envmaptint"), [0.0, 0.0, 0.0]) if vmt_value(text, "envmap") else [0.0, 0.0, 0.0]
        skins.append((skin, skin + ".png", exp_file, boost, albedo, exponent, fres, env))
    finish(w, out, folder, knife, skins, verts, tris, seqs, (-1, 0, 0, 0))


# Output gun -> (Workshop item of its skin pack, the skins to take, as the pack names them).
SKIN_PACKS = {
    "ak47": (3037441160, ["Fire Serpent", "Asiimov", "Case Hardened", "Bloodsport", "The Empress", "Neon Rider", "Wild Lotus", "Aquamarine Revenge"]),
    "m4a4": (3037453880, ["Howl", "\u9f8d\u738b (Dragon King)", "Neo-Noir", "The Emperor", "Desolate Space", "Buzz Kill", "Hellfire", "Poseidon", "Temukau"]),
    "m4a1s": (2943078567, ["Printstream", "Hyper Beast", "Golden Coil", "Icarus Fell", "Master Piece", "Cyrex", "Player Two", "Welcome To The Jungle", "Blue Phosphor", "Nightmare"]),
    "awp": (2970274532, ["Asimov", "Medusa", "Gungnir", "Hyper Beast", "Neo Noir", "Lightning Strike", "Wildfire", "Fade", "Containment Breach"]),
    "deagle": (2947102092, ["Blaze", "Printstream", "Kumicho Dragon", "Code Red", "Golden Koi", "Mecha Industries", "Hypnotic", "Ocean Drive", "Conspiracy", "Crimson Web"]),
    "glock": (3008746151, ["Fade", "Water Elemental", "Gamma Doppler", "Bullet Queen", "Neo-Noir", "Wasteland Rebel", "Dragon Tattoo", "Twilight Galaxy", "Vogue", "Reactor"]),
    "usps": (3008834189, ["Kill Confirmed", "Printstream", "Neo-Noir", "Orion", "The Traitor", "Cortex", "Caiman", "Guardian", "Monster Mashup", "Serum"]),
    "p2000": (3008754443, ["Fire Elemental", "Wicked Sick", "Ocean Foam", "Imperial Dragon", "Corticera", "Amber Fade", "Chainmail", "Scorpion", "Handgun", "Acid Etched"]),
    "p250": (3008819329, ["See Ya Later", "Asiimov", "Muertos", "Mehndi", "Cartel", "Nuclear Threat", "Supernova", "Undertow", "Visions", "Wingshot"]),
    "fiveseven": (3008740288, ["Hyper Beast", "Case Hardened", "Monkey Business", "Angry Mob", "Fowl Play", "Copper Galaxy", "Neon Kimono", "Fairy Tale", "Boost Protocol", "Retrobution"]),
    "tec9": (3008829290, ["Fuel Injector", "Decimator", "Nuclear Threat", "Titanum Bit", "Avalanche", "Bamboozle", "Red Quartz", "Re-Entry", "Isaac", "Toxic"]),
    "cz75": (3008720625, ["Victoria", "Xiangliu", "Yellow Jacket", "Red Astor", "Tigris", "The Fuschia Is Now", "Emerald", "Tacticat", "Chalice", "Pole Position"]),
    "mp9": (3037470433, ["Starlight Protector", "Hydra", "Wild Lily", "Bulldozer", "Hypnotic", "Rose Iron", "Food Chain", "Goo", "Airlock", "Mount Fuji"]),
    "mp7": (3037468638, ["Bloodsport", "Fade", "Nemesis", "Special Delivery", "Neon Ply", "Impire", "Skulls", "Abyssal Apparition", "Whiteout", "Ocean Foam"]),
    "ump45": (3037476815, ["Fade", "Primal Saber", "Blaze", "Momentum", "Moonrise", "Wild Child", "Crime Scene", "Arctic Wolf", "Grand Prix", "Exposure"]),
    "bizon": (3037472628, ["Judgement of Anubis", "Fuel Rod", "High Roller", "Blue Streak", "Osiris", "Antique", "Runic", "Space Cat", "Embargo", "Water Sigil"]),
    "famas": (3037446571, ["Roll Cage", "Mecha Industries", "Eye of Athena", "Afterimage", "Djinn", "Commemoration", "Neural Net", "Rapid Eye Movement", "Styx", "Spitfire"]),
    "galil": (3037450043, ["Chatterbox", "Sugar Rush", "Cerberus", "Eco", "Rocket Pop", "Stone Cold", "Akoben", "Firefight", "Crimson Tsunami", "Phoenix Blacklight"]),
    "sg553": (3037462105, ["Integrale", "Cyrex", "Tiger Moth", "Pulse", "Hypnotic", "Phantom", "Ultraviolet", "Colony IV", "Danger Close", "Darkwing"]),
    "ssg08": (2976151622, ["Dragonfire", "Blood in the Water", "Death's Head", "Big Iron", "Fever Dream", "Abyss", "Turbo Peek", "Bloodshot", "Necropos", "Acid Fade"]),
    "scar20": (2976100049, ["Emerald", "Bloodsport", "Cyrex", "Cardiac", "Crimson Web", "Splash Jam", "Enforcer", "Powercore", "Magna Carta", "Poultrygeist"]),
    "g3sg1": (2970717338, ["The Executioner", "Chronos", "Flux", "Dream Glade", "Stinger", "Scavenger", "Ventilator", "Azure Zebra", "Violet Murano", "New Roots"]),
    "nova": (3037435721, ["Hyper Beast", "Bloomstick", "Koi", "Antique", "Tempest", "Wild Six", "Toy Soldier", "Graphite", "Rising Skull", "Plume"]),
    "xm1014": (3037439272, ["Tranquility", "Incinegator", "Seasons", "XOXO", "Bone Machine", "Black Tie", "Entombed", "Ziggy", "Teclu Burner", "Elegant Vines"]),
    "mag7": (3037431738, ["Justice", "Heat", "Praetorian", "BI83 Spectrum", "SWAG-7", "Bulldozer", "Cinquedea", "Heaven Guard", "Firestarter", "Monster Call"]),
}
# Names the pack misspells or the dropdown can't show, for the file name.
SKIN_RENAMES = {"Asimov": "Asiimov", "Titanum Bit": "Titanium Bit", "Neo Noir": "Neo-Noir", "\u9f8d\u738b (Dragon King)": "Dragon King"}
SKIN_MAX_SIZE = 1024


def read_gma(path):
    """(file name -> bytes getter) for a Garry's Mod addon, unpacking nothing and running nothing."""
    d = open(path, "rb").read()
    if d[:4] != b"GMAD":
        d = lzma.LZMADecompressor(format=lzma.FORMAT_ALONE).decompress(d)
    p = 4 + 1 + 8 + 8
    while d[p] != 0:
        p = d.index(b"\0", p) + 1
    p += 1
    for _ in range(3):
        p = d.index(b"\0", p) + 1
    p += 4
    entries = []
    while True:
        number = i32(d, p)
        p += 4
        if number == 0:
            break
        end = d.index(b"\0", p)
        name = d[p:end].decode("utf-8", "replace")
        size = struct.unpack_from("<q", d, end + 1)[0]
        p = end + 1 + 8 + 4
        entries.append((name, size))
    files = {}
    for name, size in entries:
        files[name.lower()] = (p, size)
        p += size
    return d, files


def pack_skins(workshop, gun, settings):
    """Writes the chosen skins' textures next to the gun's own, and returns their skin entries."""
    item, names = SKIN_PACKS[gun]
    d, files = read_gma(os.path.join(workshop, str(item), "gmpublisher.gma"))
    text = lambda name: d[files[name][0]:files[name][0] + files[name][1]].decode("utf-8", "replace")
    textures = {}
    for name in files:
        if name.endswith(".lua") and "/attachments/" in name:
            lua = text(name)
            printed, material = re.search(r'PrintName\s*=\s*(?:\[\[|")(.*?)(?:\]\]|")', lua), re.search(r'ATT\.Material\s*=\s*"([^"]+)"', lua)
            if printed and material:
                textures[printed.group(1).lower()] = material.group(1).lower().replace("\\", "/")
    result = []
    for skin in names:
        material = textures[skin.lower()]
        base = re.search(r'"?\$basetexture"?\s+"?([^"\s]+)', text("materials/%s.vmt" % material), re.I).group(1).lower().replace("\\", "/")
        start, size = files["materials/%s.vtf" % base]
        with tempfile.NamedTemporaryFile(suffix=".vtf", delete=False) as f:
            f.write(d[start:start + size])
        try:
            image = vtf_image(f.name)
        finally:
            os.remove(f.name)
        if max(image.size) > SKIN_MAX_SIZE:
            # Alpha here is a shine mask, not transparency, and resizing RGBA in one go would wipe the colour wherever it's low.
            target = (SKIN_MAX_SIZE, SKIN_MAX_SIZE)
            if image.mode == "RGBA":
                alpha = image.getchannel("A").resize(target, Image.LANCZOS)
                image = image.convert("RGB").resize(target, Image.LANCZOS)
                image.putalpha(alpha)
            else:
                image = image.resize(target, Image.LANCZOS)
        skin_id = re.sub(r"[^a-z0-9]+", "_", SKIN_RENAMES.get(skin, skin).lower().replace("'", "")).strip("_")
        image.save(os.path.join(OUT, gun, skin_id + ".png"), compress_level=6)
        result.append((skin_id, skin_id + ".png") + settings)
    return result


def convert_gun(guns, out, gun):
    model, texture, extra = GUNS[gun]
    src = os.path.join(guns, "pack", "unpacked")
    w, mdl, bones, index, verts, tris, seqs = read_model(os.path.join(src, "models", "weapons", model), gun)
    print(gun, "uv range", min(v["uv"][0] for v in verts if v["uv"][0] < 1.5), max(v["uv"][0] for v in verts if v["uv"][0] < 1.5))
    mats = os.path.join(src, "materials", "models", "weapons", "csgo")
    folder = os.path.join(out, gun)
    os.makedirs(folder, exist_ok=True)

    text = re.sub(r"//[^\n]*", "", open(os.path.join(mats, texture + ".vmt"), encoding="latin1").read())
    exp_tex = (vmt_value(text, "phongexponenttexture") or "").replace("\\", "/").split("/")[-1]
    exp_file = ""
    if exp_tex:
        exp_file = "exp_" + exp_tex + ".png"
        img = vtf_image(os.path.join(mats, exp_tex + ".vtf"))
        if img.width > 512:
            img = img.resize((512, 512), Image.LANCZOS)
        img.save(os.path.join(folder, exp_file), compress_level=6)
    settings = (exp_file, numbers(vmt_value(text, "phongboost"), [1.0])[0], numbers(vmt_value(text, "phongalbedoboost"), [1.0])[0],
                numbers(vmt_value(text, "phongexponent"), [5.0])[0], numbers(vmt_value(text, "phongfresnelranges"), [.05, .5, 1.0]),
                numbers(vmt_value(text, "envmaptint"), [0.0, 0.0, 0.0]) if vmt_value(text, "envmap") else [0.0, 0.0, 0.0])

    vtf_image(os.path.join(mats, texture + ".vtf")).save(os.path.join(folder, "vanilla.png"), compress_level=6)
    skins = [("vanilla", "vanilla.png") + settings]
    for skin, (addon, path) in extra.items():
        vtf = os.path.join(guns, addon, "unpacked", "materials", "models", "weapons", "v_models", path + ".vtf")
        vtf_image(vtf).save(os.path.join(folder, skin + ".png"), compress_level=6)
        skins.append((skin, skin + ".png") + settings)
    if gun in SKIN_PACKS and WORKSHOP:
        skins.extend(pack_skins(WORKSHOP, gun, settings))

    # The first attachment is the muzzle; its transform is a 3x4 matrix, so its position is the last value of each row.
    o = i32(mdl, 244)
    if i32(mdl, 240) > 0 and cstr(mdl, o + i32(mdl, o)) == "1":
        muzzle = (index[i32(mdl, o + 8)], f32(mdl, o + 24), f32(mdl, o + 40), f32(mdl, o + 56))
    elif any(e[1] == "@muzzle" for seq in seqs for e in seq["events"]):
        raise ValueError("expected the muzzle attachment first")
    else:
        muzzle = (-1, 0, 0, 0)
    print("  muzzle", muzzle)
    finish(w, out, folder, gun, skins, verts, tris, seqs, muzzle)


if __name__ == "__main__":
    src, out, guns = sys.argv[1], sys.argv[2], sys.argv[3]
    OUT = out
    # Optional: the folder SteamCMD put the skin packs in (steamapps/workshop/content/4000).
    WORKSHOP = sys.argv[4] if len(sys.argv) > 4 else None
    only = os.environ.get("CONVERT_ONLY")
    os.makedirs(out, exist_ok=True)
    write_sounds(src, guns, out)
    if not only:
        for knife, model in KNIVES.items():
            convert(src, out, knife, model)
    for gun in GUNS:
        if not only or gun in only.split(","):
            convert_gun(guns, out, gun)
