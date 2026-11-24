package rip.tempest.utils.render.models;

public interface Poser {
    void pose(AutoRig.Angles angles, float[][] outPositions, float[][] outNormals);

    String describe();
}
