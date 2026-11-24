package rip.tempest.jenny.geo;

import java.util.ArrayList;
import java.util.List;

public class GeoBone {
    public final String name;
    public final String parentName;
    public final float[] pivot;
    public final float[] rotation;
    public final List<GeoCube> cubes = new ArrayList<>();
    public final List<GeoBone> children = new ArrayList<>();

    public GeoBone(String name, String parentName, float[] pivot, float[] rotation) {
        this.name = name;
        this.parentName = parentName;
        this.pivot = pivot;
        this.rotation = rotation;
    }
}
