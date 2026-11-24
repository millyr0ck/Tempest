package rip.tempest.jenny.anim;

public class BoneAnim {
    public final Channel rotation;
    public final Channel position;
    public final Channel scale;

    public BoneAnim(Channel rotation, Channel position, Channel scale) {
        this.rotation = rotation;
        this.position = position;
        this.scale = scale;
    }
}
