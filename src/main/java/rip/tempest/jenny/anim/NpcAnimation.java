package rip.tempest.jenny.anim;

import java.util.Map;

public class NpcAnimation {
    public final String name;

    public final boolean loop;
    public final float length;
    public final Map<String, BoneAnim> bones;

    public final float[] eventTimes;
    public final String[] eventNames;

    public NpcAnimation(String name, boolean loop, float length, Map<String, BoneAnim> bones,
                        float[] eventTimes, String[] eventNames) {
        this.name = name;
        this.loop = loop;
        this.length = length;
        this.bones = bones;
        this.eventTimes = eventTimes;
        this.eventNames = eventNames;
    }

    public boolean animates(String boneName) {
        return bones.containsKey(boneName);
    }
}
