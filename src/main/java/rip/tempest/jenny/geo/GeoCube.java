package rip.tempest.jenny.geo;

public class GeoCube {
    public final float[] origin;
    public final float[] size;
    public final float inflate;
    public final float[] localPivot;
    public final float[] localRotation;
    public final GeoFace north, south, east, west, up, down;

    public GeoCube(float[] origin, float[] size, float inflate,
                   float[] localPivot, float[] localRotation,
                   GeoFace north, GeoFace south, GeoFace east,
                   GeoFace west, GeoFace up, GeoFace down) {
        this.origin = origin;
        this.size = size;
        this.inflate = inflate;
        this.localPivot = localPivot;
        this.localRotation = localRotation;
        this.north = north;
        this.south = south;
        this.east = east;
        this.west = west;
        this.up = up;
        this.down = down;
    }
}
