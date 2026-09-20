package br.com.craftonica.showcase;

/** Complete voxel contract for the exact reserved arena volume. */
public final class RobotArenaBlueprint {
    public static final int WALL_HEIGHT = 3;
    public static final int CLEAR_HEIGHT = 6;

    public enum Voxel {
        AIR, FLOOR, START_FLOOR, RECOVERY_FLOOR, EXIT_FLOOR,
        BOUNDARY_WALL, MDF_WALL, PLASTIC_WALL, ABSORBENT_WALL,
        SMALL_MDF, SMALL_FOAM
    }

    private final RobotArenaLayout layout = new RobotArenaLayout();

    public Voxel voxel(int x, int y, int z) {
        if (x < 0 || x >= RobotArenaLayout.WIDTH || z < 0 || z >= RobotArenaLayout.DEPTH
                || y < -1 || y > CLEAR_HEIGHT) throw new IndexOutOfBoundsException("arena voxel");
        int lx = x / RobotArenaLayout.SCALE, lz = z / RobotArenaLayout.SCALE;
        RobotArenaLayout.Cell cell = layout.cell(lx, lz);
        if (y == -1) {
            if (cell == RobotArenaLayout.Cell.START) return Voxel.START_FLOOR;
            if (cell == RobotArenaLayout.Cell.RECOVERY) return Voxel.RECOVERY_FLOOR;
            if (cell == RobotArenaLayout.Cell.EXIT) return Voxel.EXIT_FLOOR;
            return Voxel.FLOOR;
        }
        if (cell == RobotArenaLayout.Cell.WALL && y < WALL_HEIGHT) {
            RobotArenaLayout.WallMaterial material = layout.wallMaterial(lx, lz);
            if (material == RobotArenaLayout.WallMaterial.MDF) return Voxel.MDF_WALL;
            if (material == RobotArenaLayout.WallMaterial.RIGID_PLASTIC) return Voxel.PLASTIC_WALL;
            if (material == RobotArenaLayout.WallMaterial.ABSORBENT) return Voxel.ABSORBENT_WALL;
            return Voxel.BOUNDARY_WALL;
        }
        boolean center = x == RobotArenaLayout.blockCenter(lx) && z == RobotArenaLayout.blockCenter(lz);
        if (center && y < 2 && cell == RobotArenaLayout.Cell.SMALL_MDF) return Voxel.SMALL_MDF;
        if (center && y < 2 && cell == RobotArenaLayout.Cell.SMALL_FOAM) return Voxel.SMALL_FOAM;
        return Voxel.AIR;
    }

    public RobotArenaLayout layout() { return layout; }
}
