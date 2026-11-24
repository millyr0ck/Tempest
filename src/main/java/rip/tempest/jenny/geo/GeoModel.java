package rip.tempest.jenny.geo;

import java.util.List;
import java.util.Map;

public class GeoModel {
    public final int textureWidth;
    public final int textureHeight;
    public final List<GeoBone> rootBones;
    public final Map<String, GeoBone> bones;

    public GeoModel(int textureWidth, int textureHeight,
                    List<GeoBone> rootBones, Map<String, GeoBone> bones) {
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.rootBones = rootBones;
        this.bones = bones;
    }
}
