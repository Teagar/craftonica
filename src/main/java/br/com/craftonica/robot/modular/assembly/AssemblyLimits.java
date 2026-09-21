package br.com.craftonica.robot.modular.assembly;

public final class AssemblyLimits {
    public static final AssemblyLimits PROFILE_1 = new AssemblyLimits(256, 512, 1024, 16);

    public final int maximumComponents;
    public final int maximumInspectedPositions;
    public final int maximumEdges;
    public final int maximumExtentPerAxis;

    public AssemblyLimits(int maximumComponents, int maximumInspectedPositions,
                          int maximumEdges, int maximumExtentPerAxis) {
        if (maximumComponents <= 0 || maximumInspectedPositions < maximumComponents
                || maximumEdges <= 0 || maximumExtentPerAxis <= 0)
            throw new IllegalArgumentException("assembly limits");
        this.maximumComponents = maximumComponents;
        this.maximumInspectedPositions = maximumInspectedPositions;
        this.maximumEdges = maximumEdges;
        this.maximumExtentPerAxis = maximumExtentPerAxis;
    }
}
