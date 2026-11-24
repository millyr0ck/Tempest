package rip.tempest.modules.impl.player;

import rip.tempest.Tempest;
import rip.tempest.api.events.impl.game.PreUpdateEvent;
import rip.tempest.api.events.impl.packet.PacketReceiveEvent;
import rip.tempest.api.events.impl.packet.PacketSendEvent;
import rip.tempest.api.events.impl.world.WorldLoadEvent;
import rip.tempest.api.properties.Property;
import rip.tempest.api.properties.impl.ModeProperty;
import rip.tempest.api.properties.impl.NumberProperty;
import rip.tempest.api.properties.impl.SelectProperty;
import rip.tempest.modules.Module;
import rip.tempest.modules.ModuleCategory;
import rip.tempest.modules.ModuleInfo;
import rip.tempest.processes.RotationProcess;
import rip.tempest.utils.client.Logger;
import rip.tempest.utils.mc.RotationUtils;
import rip.tempest.utils.misc.MovementFix;
import rip.tempest.utils.misc.NbsReader;
import io.github.nevalackin.homoBus.Listener;
import io.github.nevalackin.homoBus.Priorities;
import io.github.nevalackin.homoBus.annotations.EventLink;
import net.minecraft.block.material.Material;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.server.S24PacketBlockAction;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import org.lwjgl.util.vector.Vector2f;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;

import static rip.tempest.utils.Util.mc;

@ModuleInfo(label = "Notebot", category = ModuleCategory.PLAYER)
public final class NotebotModule extends Module {
    private final SelectProperty song = new SelectProperty("Song");

    private final Property<Boolean> repeat = new Property<>("Repeat", true);
    private final NumberProperty speed = new NumberProperty("Speed", 100, 50, 200, 5);
    private final ModeProperty<Instruments> instruments = new ModeProperty<>("Instruments", Instruments.PreferHarp);
    private final NumberProperty range = new NumberProperty("Range", 4.5, 2, 4.5, 0.1);

    private final NumberProperty clickDelay = new NumberProperty("Click Delay", 1, 1, 5, 1);
    private final Property<Boolean> rotate = new Property<>("Rotate", true);

    private static final int LISTEN_TICKS = 30, LISTEN_TRIES = 3;

    private static final int CONFIRM_TICKS = 40;

    private static final int GAP_TICKS = 30;

    private enum Stage { LISTEN, TUNE, PLAY }

    private static final class NoteBlock {
        final BlockPos pos;

        int instrument;

        int pitch = -1;

        int expected = -1;

        int target = -1;
        int tries;
        int askedTick = Integer.MIN_VALUE / 2;

        NoteBlock(BlockPos pos, int instrument) {
            this.pos = pos;
            this.instrument = instrument;
        }
    }

    private record Heard(BlockPos pos, int instrument, int pitch) {
    }

    private final Map<BlockPos, NoteBlock> blocks = new LinkedHashMap<>();
    private final Queue<Heard> heard = new ConcurrentLinkedQueue<>();

    private Stage stage = Stage.LISTEN;
    private int tick, stageTick, lastClickTick;

    private NoteBlock aimed;
    private float sentYaw = Float.NaN, sentPitch = Float.NaN;

    private static final String RANDOM = "Random";

    private record Tune(String name, int[][] notes, TreeSet<Integer> pitches) {
        @Override
        public String toString() {
            return name;
        }
    }

    private Tune playing;

    private String playingChoice = "";

    private final Map<String, Object[]> nbsCache = new HashMap<>();

    private final List<Integer> noteTicks = new ArrayList<>();
    private final List<NoteBlock> noteBlocks = new ArrayList<>();
    private int noteIndex;
    private double songTick;
    private boolean warnedTool;

    private boolean starting;

    public NotebotModule() {
        song.setOnOpen(this::scanSongs);
        scanSongs();
    }

    private void scanSongs() {
        List<String> names = new ArrayList<>();
        names.add(RANDOM);
        for (Song builtIn : Song.values()) names.add(builtIn.toString());
        List<String> files = new ArrayList<>();
        try {
            File folder = songsFolder();
            if (!folder.exists()) folder.mkdirs();
            File[] found = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".nbs"));
            if (found != null) for (File file : found) files.add(file.getName().substring(0, file.getName().length() - 4));
        } catch (Exception ignored) {
        }
        files.sort(String.CASE_INSENSITIVE_ORDER);
        names.addAll(files);
        song.setOptions(names);
    }

    private static File songsFolder() {
        return new File(mc.mcDataDir, Tempest.NAME + "/songs");
    }

    @Override
    public void onEnable() {
        super.onEnable();
        blocks.clear();
        heard.clear();
        aimed = null;
        playing = null;
        warnedTool = false;
        tick = lastClickTick = 0;
        sentYaw = sentPitch = Float.NaN;
        starting = true;
    }

    private boolean start() {
        starting = false;
        sentYaw = mc.thePlayer.rotationYaw;
        sentPitch = mc.thePlayer.rotationPitch;

        if (!mc.playerController.isNotCreative()) {
            Logger.chatPrint("§cNotebot: in creative a hit breaks the note block. Switch to survival.");
            toggle();
            return false;
        }
        scan();
        if (blocks.isEmpty()) {
            Logger.chatPrint("§cNotebot: no note blocks within reach (each needs air above it).");
            toggle();
            return false;
        }
        Logger.chatPrint("§aNotebot: found " + blocks.size() + " note blocks (" + describeInstruments() + "). Listening to their pitches...");
        setStage(Stage.LISTEN);
        return true;
    }

    @Override
    public void onDisable() {
        if (mc.playerController != null && mc.playerController.getIsHittingBlock()) mc.playerController.resetBlockRemoving();
        blocks.clear();
        aimed = null;
        super.onDisable();
    }

    @EventLink
    public final Listener<WorldLoadEvent> onWorldLoad = event -> {
        if (isEnabled()) toggle();
    };

    @EventLink
    public final Listener<PacketReceiveEvent> onReceive = event -> {
        if (event.getPacket() instanceof S24PacketBlockAction packet && packet.getBlockType() == Blocks.noteblock) {
            heard.add(new Heard(packet.getBlockPosition(), packet.getData1(), packet.getData2()));
        }
    };

    @EventLink(Priorities.VERY_LOW)
    public final Listener<PacketSendEvent> onSend = event -> {
        if (!event.isCancelled() && event.getPacket() instanceof C03PacketPlayer packet && packet.getRotating()) {
            sentYaw = packet.getYaw();
            sentPitch = packet.getPitch();
        }
    };

    @EventLink(Priorities.HIGH)
    public final Listener<PreUpdateEvent> onPreUpdate = event -> {
        if (mc.thePlayer == null || mc.theWorld == null) return;
        if (starting && !start()) return;
        tick++;
        stageTick++;

        for (Heard h; (h = heard.poll()) != null; ) {
            NoteBlock block = blocks.get(h.pos());
            if (block != null) {
                block.pitch = h.pitch();
                block.instrument = h.instrument();
            }
        }

        if (mc.currentScreen instanceof GuiContainer || mc.thePlayer.isSneaking()) return;

        switch (stage) {
            case LISTEN -> listen();
            case TUNE -> tune();
            case PLAY -> play();
        }
        setSuffix(playing != null ? playing.toString() : stage.name().charAt(0) + stage.name().substring(1).toLowerCase());
    };

    private void listen() {
        if (aimed != null && aimed.pitch == -1) {
            MovingObjectPosition hit = facing(aimed);
            if (hit != null && hit(aimed, hit)) {
                aimed.tries++;
                aimed.askedTick = tick;
            }
        }
        aimed = null;

        NoteBlock next = null;
        boolean waiting = false;
        for (NoteBlock block : blocks.values()) {
            if (block.pitch != -1) continue;
            if (tick - block.askedTick < LISTEN_TICKS) {
                waiting = true;
            } else if (block.tries < LISTEN_TRIES && next == null) {
                next = block;
            }
        }
        if (next != null) {
            aimAt(next);
            return;
        }
        if (waiting) return;

        long silent = blocks.values().stream().filter(b -> b.pitch == -1).count();
        if (silent > 0) Logger.chatPrint("§eNotebot: " + silent + " note block(s) never played, so they're left out.");
        blocks.values().removeIf(b -> b.pitch == -1);
        if (blocks.isEmpty()) {
            Logger.chatPrint("§cNotebot: none of the note blocks played.");
            toggle();
            return;
        }
        startSong(pickSong());
    }

    private String choice() {
        String value = song.getValue();
        return value == null || value.isEmpty() ? RANDOM : value;
    }

    private void tune() {
        if (aimed != null && aimed.expected != aimed.target && tick - lastClickTick >= clickDelay.getValue().intValue()) {
            MovingObjectPosition hit = facing(aimed);
            if (hit != null && use(aimed, hit)) {
                aimed.expected = (aimed.expected + 1) % 25;
                lastClickTick = tick;
            }
        }
        aimed = null;

        for (NoteBlock block : blocks.values()) {
            if (block.target != -1 && block.expected != block.target) {
                aimAt(block);
                stageTick = 0;
                return;
            }
        }

        boolean tuned = blocks.values().stream().allMatch(b -> b.target == -1 || b.pitch == b.target);
        if (tuned) {
            Logger.chatPrint("§aNotebot: playing " + playing + ".");
            startPlaying();
        } else if (stageTick > CONFIRM_TICKS) {
            for (NoteBlock block : blocks.values()) block.expected = block.pitch;
            stageTick = 0;
        }
    }

    private void play() {
        int now = (int) Math.floor(songTick);
        while (noteIndex < noteTicks.size() && scaled(noteTicks.get(noteIndex)) <= now) {
            NoteBlock block = noteBlocks.get(noteIndex++);

            if (block == aimed || !rotate.getValue()) {
                MovingObjectPosition hit = facing(block);
                if (hit != null) hit(block, hit);
            }
        }
        aimed = null;

        if (noteIndex < noteTicks.size()) {
            aimAt(noteBlocks.get(noteIndex));
        } else if (now > scaled(noteTicks.isEmpty() ? 0 : noteTicks.get(noteTicks.size() - 1)) + GAP_TICKS) {
            if (choice().equals(RANDOM)) {
                startSong(pickSong());
                return;
            } else if (repeat.getValue()) {
                startPlaying();
                return;
            }
            Logger.chatPrint("§aNotebot: done.");
            toggle();
            return;
        }

        if (!choice().equals(playingChoice)) {
            startSong(pickSong());
            return;
        }
        songTick += 1.0;
    }

    private Tune pickSong() {
        String choice = choice();
        playingChoice = choice;
        if (!choice.equals(RANDOM)) return resolve(choice);

        List<String> candidates = new ArrayList<>();
        for (Song builtIn : Song.values()) {
            if (builtIn.tune().pitches().size() <= usableCount()) candidates.add(builtIn.toString());
        }
        for (String option : song.getOptions()) {
            if (!option.equals(RANDOM) && builtIn(option) == null) candidates.add(option);
        }
        if (candidates.isEmpty()) for (Song builtIn : Song.values()) candidates.add(builtIn.toString());
        if (playing != null && candidates.size() > 1) candidates.remove(playing.name());

        while (!candidates.isEmpty()) {
            String pick = candidates.remove((int) (Math.random() * candidates.size()));
            Tune tune = resolve(pick);
            if (tune != null) return tune;
        }
        return null;
    }

    private static Song builtIn(String name) {
        String wanted = name.replace(" ", "");
        for (Song builtIn : Song.values()) {
            if (builtIn.toString().replace(" ", "").equalsIgnoreCase(wanted) || builtIn.name().equalsIgnoreCase(wanted)) return builtIn;
        }
        return null;
    }

    private Tune resolve(String name) {
        Song builtIn = builtIn(name);
        if (builtIn != null) return builtIn.tune();

        File file = new File(songsFolder(), name + ".nbs");
        if (!file.isFile()) {
            Logger.chatPrint("§cNotebot: can't find " + file.getName() + " in Tempest/songs.");
            return null;
        }
        boolean chords = !rotate.getValue();
        Object[] cached = nbsCache.get(name);
        if (cached != null && (long) cached[0] == file.lastModified() && (boolean) cached[1] == chords) return (Tune) cached[2];
        try {
            NbsReader.Song nbs = NbsReader.read(file);
            String shown = nbs.name().isBlank() ? name : nbs.name().trim();
            if (!nbs.author().isBlank()) shown += " by " + nbs.author().trim();
            Tune tune = fromNbs(shown, nbs, chords);
            if (tune.notes().length == 0) {
                Logger.chatPrint("§cNotebot: " + file.getName() + " has no notes to play.");
                return null;
            }
            nbsCache.put(name, new Object[]{file.lastModified(), chords, tune});
            return tune;
        } catch (Exception e) {
            Logger.chatPrint("§cNotebot: couldn't read " + file.getName() + " (" + e.getMessage() + ").");
            return null;
        }
    }

    private static final int[] NBS_INSTRUMENTS = {0, 4, 1, 2, 3, 0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0};

    private static Tune fromNbs(String name, NbsReader.Song nbs, boolean chords) {
        double gameTicksPerSongTick = 20.0 / nbs.ticksPerSecond();
        List<int[]> audible = new ArrayList<>();
        boolean melody = false;
        for (NbsReader.Note note : nbs.notes()) {
            if (note.velocity() == 0 || nbs.volume(note.layer()) == 0) continue;
            int instrument = note.instrument() < nbs.vanillaInstruments() && note.instrument() < NBS_INSTRUMENTS.length
                    ? NBS_INSTRUMENTS[note.instrument()] : 0;
            boolean drum = instrument == 1 || instrument == 2 || instrument == 3;
            melody |= !drum;
            audible.add(new int[]{(int) Math.round(note.tick() * gameTicksPerSongTick), note.key(), drum ? 1 : 0});
        }

        TreeMap<Integer, List<int[]>> byTick = new TreeMap<>();
        for (int[] note : audible) {
            if (melody && note[2] == 1) continue;
            byTick.computeIfAbsent(note[0], tick -> new ArrayList<>()).add(note);
        }

        List<int[]> notes = new ArrayList<>();
        TreeSet<Integer> pitches = new TreeSet<>();
        for (Map.Entry<Integer, List<int[]>> entry : byTick.entrySet()) {
            if (chords) {
                TreeSet<Integer> played = new TreeSet<>();
                for (int[] note : entry.getValue()) {
                    int pitch = intoRange(note[1] - 33);
                    if (played.add(pitch)) notes.add(new int[]{entry.getKey(), pitch});
                }
                pitches.addAll(played);
            } else {
                int[] top = entry.getValue().get(0);
                for (int[] note : entry.getValue()) if (note[1] > top[1]) top = note;
                int pitch = intoRange(top[1] - 33);
                notes.add(new int[]{entry.getKey(), pitch});
                pitches.add(pitch);
            }
        }
        return new Tune(name, notes.toArray(new int[0][]), pitches);
    }

    private static int intoRange(int pitch) {
        while (pitch < 0) pitch += 12;
        while (pitch > 24) pitch -= 12;
        return pitch;
    }

    private void startSong(Tune next) {
        if (next == null) {
            toggle();
            return;
        }
        playing = next;
        for (NoteBlock block : blocks.values()) block.target = -1;

        List<NoteBlock> pool = new ArrayList<>();
        for (NoteBlock block : blocks.values()) {
            if (instruments.getValue() == Instruments.HarpOnly && block.instrument != 0) continue;
            pool.add(block);
        }

        Map<Integer, NoteBlock> byPitch = new HashMap<>();
        int missing = 0;
        for (int pitch : next.pitches()) {
            NoteBlock best = null;
            for (NoteBlock block : pool) {
                if (best == null || better(block, best, pitch)) best = block;
            }
            if (best == null) {
                missing++;
                continue;
            }
            pool.remove(best);
            best.target = pitch;
            best.expected = best.pitch;
            byPitch.put(pitch, best);
        }

        noteTicks.clear();
        noteBlocks.clear();
        int[][] notes = next.notes();
        for (int[] note : notes) {
            NoteBlock block = byPitch.get(note[1]);
            if (block == null) continue;
            noteTicks.add(note[0]);
            noteBlocks.add(block);
        }

        if (missing > 0) {
            Logger.chatPrint("§eNotebot: " + next + " needs " + next.pitches().size() + " note blocks"
                    + (instruments.getValue() == Instruments.HarpOnly ? " on harp" : "") + "; " + missing + " of its notes will be missing.");
        }
        if (noteTicks.isEmpty()) {
            Logger.chatPrint("§cNotebot: not enough note blocks to play anything.");
            toggle();
            return;
        }
        long clicks = blocks.values().stream().filter(b -> b.target != -1).mapToInt(b -> Math.floorMod(b.target - b.pitch, 25)).sum();
        Logger.chatPrint("§aNotebot: tuning for " + next + " (" + clicks + " clicks)...");
        setStage(Stage.TUNE);
    }

    private void startPlaying() {
        noteIndex = 0;
        songTick = -1;
        setStage(Stage.PLAY);
    }

    private boolean better(NoteBlock a, NoteBlock b, int pitch) {
        if (instruments.getValue() == Instruments.PreferHarp) {
            int ra = instrumentRank(a.instrument), rb = instrumentRank(b.instrument);
            if (ra != rb) return ra < rb;
        }
        return Math.floorMod(pitch - a.pitch, 25) < Math.floorMod(pitch - b.pitch, 25);
    }

    private static int instrumentRank(int instrument) {
        return switch (instrument) {
            case 0 -> 0;
            case 4 -> 1;
            case 3 -> 2;
            case 2 -> 3;
            default -> 4;
        };
    }

    private int usableCount() {
        if (instruments.getValue() != Instruments.HarpOnly) return blocks.size();
        return (int) blocks.values().stream().filter(b -> b.instrument == 0).count();
    }

    private int scaled(int songTicks) {
        return (int) Math.round(songTicks * 100.0 / speed.getValue());
    }

    private void scan() {
        Vec3 eye = mc.thePlayer.getPositionEyes(1f);
        int r = (int) Math.ceil(range.getValue()) + 1;
        BlockPos center = new BlockPos(eye);
        List<NoteBlock> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.getAllInBox(center.add(-r, -r, -r), center.add(r, r, r))) {
            if (mc.theWorld.getBlockState(pos).getBlock() != Blocks.noteblock) continue;
            if (!mc.theWorld.isAirBlock(pos.up())) continue;
            BlockPos immutable = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
            if (aimRotation(immutable) == null) continue;
            found.add(new NoteBlock(immutable, instrumentBelow(immutable)));
        }
        found.sort(Comparator.comparingDouble(b -> eye.squareDistanceTo(new Vec3(b.pos).addVector(0.5, 0.5, 0.5))));
        for (NoteBlock block : found) blocks.put(block.pos, block);
    }

    private static int instrumentBelow(BlockPos pos) {
        Material material = mc.theWorld.getBlockState(pos.down()).getBlock().getMaterial();
        if (material == Material.rock) return 1;
        if (material == Material.sand) return 2;
        if (material == Material.glass) return 3;
        if (material == Material.wood) return 4;
        return 0;
    }

    private String describeInstruments() {
        int[] counts = new int[5];
        for (NoteBlock block : blocks.values()) counts[block.instrument]++;
        String[] names = {"harp", "bass drum", "snare", "clicks", "bass"};
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < 5; i++) if (counts[i] > 0) parts.add(counts[i] + " " + names[i]);
        return String.join(", ", parts);
    }

    private void aimAt(NoteBlock block) {
        aimed = block;
        if (!rotate.getValue()) return;
        Vector2f rotation = aimRotation(block.pos);
        if (rotation != null) RotationProcess.setRotations(rotation, 10, MovementFix.NORMAL);
    }

    private Vector2f aimRotation(BlockPos pos) {
        double[][] points = {{0.5, 0.9, 0.5}, {0.5, 0.5, 0.5}, {0.5, 0.5, 0.1}, {0.5, 0.5, 0.9}, {0.1, 0.5, 0.5}, {0.9, 0.5, 0.5}};
        for (double[] point : points) {
            Vector2f rotation = RotationUtils.calculate(new Vec3(pos.getX() + point[0], pos.getY() + point[1], pos.getZ() + point[2]));
            if (trace(rotation.x, rotation.y, pos) != null) return rotation;
        }
        return null;
    }

    private MovingObjectPosition facing(NoteBlock block) {
        if (!rotate.getValue()) {
            Vector2f rotation = aimRotation(block.pos);
            return rotation == null ? null : trace(rotation.x, rotation.y, block.pos);
        }
        if (Float.isNaN(sentYaw)) return null;
        return trace(sentYaw, sentPitch, block.pos);
    }

    private MovingObjectPosition trace(float yaw, float pitch, BlockPos pos) {
        Vec3 eye = mc.thePlayer.getPositionEyes(1f);
        double reach = range.getValue();
        Vec3 look = look(yaw, pitch);
        Vec3 end = eye.addVector(look.xCoord * reach, look.yCoord * reach, look.zCoord * reach);
        MovingObjectPosition hit = mc.theWorld.rayTraceBlocks(eye, end, false, false, false);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || !pos.equals(hit.getBlockPos())) return null;
        return hit.hitVec.distanceTo(eye) <= reach ? hit : null;
    }

    private static Vec3 look(float yaw, float pitch) {
        float cosYaw = MathHelper.cos(-yaw * 0.017453292F - (float) Math.PI);
        float sinYaw = MathHelper.sin(-yaw * 0.017453292F - (float) Math.PI);
        float cosPitch = -MathHelper.cos(-pitch * 0.017453292F);
        float sinPitch = MathHelper.sin(-pitch * 0.017453292F);
        return new Vec3(sinYaw * cosPitch, sinPitch, cosYaw * cosPitch);
    }

    private boolean hit(NoteBlock block, MovingObjectPosition hit) {
        if (mc.theWorld.getBlockState(block.pos).getBlock().getPlayerRelativeBlockHardness(mc.thePlayer, mc.theWorld, block.pos) >= 1.0F) {
            if (!warnedTool) Logger.chatPrint("§cNotebot: what you're holding breaks note blocks in one hit. Hold something else.");
            warnedTool = true;
            return false;
        }
        warnedTool = false;
        if (mc.playerController.getIsHittingBlock()) mc.playerController.resetBlockRemoving();
        mc.thePlayer.swingItem();
        mc.playerController.clickBlock(block.pos, hit.sideHit);
        return true;
    }

    private boolean use(NoteBlock block, MovingObjectPosition hit) {
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, mc.thePlayer.getHeldItem(), block.pos, hit.sideHit, hit.hitVec)) {
            mc.thePlayer.swingItem();
            return true;
        }
        return false;
    }

    private void setStage(Stage next) {
        stage = next;
        stageTick = 0;
        aimed = null;
    }

    private enum Instruments {
        HarpOnly("Harp Only"), PreferHarp("Prefer Harp"), Any("Any");

        private final String name;

        Instruments(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Built-in tunes, all traditional or long out of copyright. Each note is {@code name:steps},
     * {@code r} a rest, and a step lasts {@code ticksPerStep} game ticks at 100% speed. Note
     * blocks reach from F#3 to F#5.
     */
    public enum Song {
        JingleBells("Jingle Bells", 3,
                "E4:2 E4:2 E4:4 E4:2 E4:2 E4:4 E4:2 G4:2 C4:3 D4:1 E4:8 "
                        + "F4:2 F4:2 F4:3 F4:1 F4:2 E4:2 E4:2 E4:1 E4:1 E4:2 D4:2 D4:2 E4:2 D4:4 G4:4 "
                        + "E4:2 E4:2 E4:4 E4:2 E4:2 E4:4 E4:2 G4:2 C4:3 D4:1 E4:8 "
                        + "F4:2 F4:2 F4:3 F4:1 F4:2 E4:2 E4:2 E4:1 E4:1 G4:2 G4:2 F4:2 D4:2 C4:8"),
        Twinkle("Twinkle Twinkle", 3,
                "C4:2 C4:2 G4:2 G4:2 A4:2 A4:2 G4:4 F4:2 F4:2 E4:2 E4:2 D4:2 D4:2 C4:4 "
                        + "G4:2 G4:2 F4:2 F4:2 E4:2 E4:2 D4:4 G4:2 G4:2 F4:2 F4:2 E4:2 E4:2 D4:4 "
                        + "C4:2 C4:2 G4:2 G4:2 A4:2 A4:2 G4:4 F4:2 F4:2 E4:2 E4:2 D4:2 D4:2 C4:4"),
        OdeToJoy("Ode to Joy", 3,
                "E4:2 E4:2 F4:2 G4:2 G4:2 F4:2 E4:2 D4:2 C4:2 C4:2 D4:2 E4:2 E4:3 D4:1 D4:4 "
                        + "E4:2 E4:2 F4:2 G4:2 G4:2 F4:2 E4:2 D4:2 C4:2 C4:2 D4:2 E4:2 D4:3 C4:1 C4:4"),
        Mary("Mary Had a Little Lamb", 3,
                "E4:2 D4:2 C4:2 D4:2 E4:2 E4:2 E4:4 D4:2 D4:2 D4:4 E4:2 G4:2 G4:4 "
                        + "E4:2 D4:2 C4:2 D4:2 E4:2 E4:2 E4:2 E4:2 D4:2 D4:2 E4:2 D4:2 C4:8"),
        HappyBirthday("Happy Birthday", 2,
                "G3:3 G3:1 A3:4 G3:4 C4:4 B3:8 G3:3 G3:1 A3:4 G3:4 D4:4 C4:8 "
                        + "G3:3 G3:1 G4:4 E4:4 C4:4 B3:4 A3:8 F4:3 F4:1 E4:4 C4:4 D4:4 C4:8"),
        FrereJacques("Frère Jacques", 3,
                "C4:2 D4:2 E4:2 C4:2 C4:2 D4:2 E4:2 C4:2 E4:2 F4:2 G4:4 E4:2 F4:2 G4:4 "
                        + "G4:1 A4:1 G4:1 F4:1 E4:2 C4:2 G4:1 A4:1 G4:1 F4:1 E4:2 C4:2 C4:2 G3:2 C4:4 C4:2 G3:2 C4:4"),
        FurElise("Für Elise", 3,
                "E5:1 D#5:1 E5:1 D#5:1 E5:1 B4:1 D5:1 C5:1 A4:3 C4:1 E4:1 A4:1 B4:3 E4:1 G#4:1 B4:1 C5:3 "
                        + "E4:1 E5:1 D#5:1 E5:1 D#5:1 E5:1 B4:1 D5:1 C5:1 A4:3 C4:1 E4:1 A4:1 B4:3 E4:1 C5:1 B4:1 A4:6"),
        Korobeiniki("Korobeiniki", 3,
                "B4:2 F#4:1 G4:1 A4:2 G4:1 F#4:1 E4:2 E4:1 G4:1 B4:2 A4:1 G4:1 F#4:3 G4:1 A4:2 B4:2 G4:2 E4:2 E4:4 "
                        + "r:2 A4:3 C5:1 E5:2 D5:1 C5:1 B4:3 G4:1 B4:2 A4:1 G4:1 F#4:2 F#4:1 G4:1 A4:2 B4:2 G4:2 E4:2 E4:4"),
        Westminster("Westminster Chimes", 3,
                "E4:4 D4:4 C4:4 G3:8 C4:4 E4:4 D4:4 G3:8 C4:4 D4:4 E4:4 C4:8 E4:4 C4:4 D4:4 G3:8 G3:4 D4:4 E4:4 C4:8");

        private final String name, score;
        private final int ticksPerStep;
        private int[][] notes;
        private TreeSet<Integer> pitches;
        private Tune tune;

        Song(String name, int ticksPerStep, String score) {
            this.name = name;
            this.ticksPerStep = ticksPerStep;
            this.score = score;
        }

        Tune tune() {
            if (tune == null) tune = new Tune(name, notes(), pitches());
            return tune;
        }

        int[][] notes() {
            if (notes == null) parse();
            return notes;
        }

        TreeSet<Integer> pitches() {
            if (pitches == null) parse();
            return pitches;
        }

        private void parse() {
            List<int[]> out = new ArrayList<>();
            pitches = new TreeSet<>();
            int step = 0;
            for (String token : score.trim().split("\\s+")) {
                if (token.isEmpty()) continue;
                String[] parts = token.split(":");
                int length = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                if (!parts[0].equals("r")) {
                    int pitch = pitch(parts[0]);
                    out.add(new int[]{step * ticksPerStep, pitch});
                    pitches.add(pitch);
                }
                step += length;
            }
            notes = out.toArray(new int[0][]);
        }

        private static int pitch(String name) {
            int semitone = switch (name.charAt(0)) {
                case 'C' -> 0;
                case 'D' -> 2;
                case 'E' -> 4;
                case 'F' -> 5;
                case 'G' -> 7;
                case 'A' -> 9;
                default -> 11;
            };
            int index = 1;
            if (name.charAt(index) == '#') {
                semitone++;
                index++;
            } else if (name.charAt(index) == 'b') {
                semitone--;
                index++;
            }
            int octave = Integer.parseInt(name.substring(index));
            int pitch = octave * 12 + semitone - (3 * 12 + 6);
            if (pitch < 0 || pitch > 24) throw new IllegalArgumentException("Out of note block range: " + name);
            return pitch;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
