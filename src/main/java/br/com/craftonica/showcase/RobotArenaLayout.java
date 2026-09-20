package br.com.craftonica.showcase;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure deterministic maze plan; each logical cell expands to a 3 x 3 block footprint. */
public final class RobotArenaLayout {
    public static final int LOGICAL_SIZE = 17;
    public static final int SCALE = 3;
    public static final int WIDTH = LOGICAL_SIZE * SCALE;
    public static final int DEPTH = LOGICAL_SIZE * SCALE;

    public enum Cell { WALL, PASSAGE, START, RECOVERY, EXIT, SMALL_MDF, SMALL_FOAM }
    public enum WallMaterial { BOUNDARY, MDF, RIGID_PLASTIC, ABSORBENT }

    private final Cell[][] cells = new Cell[LOGICAL_SIZE][LOGICAL_SIZE];

    public RobotArenaLayout() {
        for (int x = 0; x < LOGICAL_SIZE; x++) for (int z = 0; z < LOGICAL_SIZE; z++)
            cells[x][z] = Cell.WALL;
        carveMaze();
        carveBay(1, 1); carveBay(LOGICAL_SIZE - 4, LOGICAL_SIZE - 4);
        cells[1][1] = Cell.START;
        cells[LOGICAL_SIZE - 4][LOGICAL_SIZE - 4] = Cell.EXIT;
        cells[LOGICAL_SIZE - 2][LOGICAL_SIZE - 2] = Cell.RECOVERY;
        cells[LOGICAL_SIZE - 1][LOGICAL_SIZE - 2] = Cell.EXIT;
        placeSmallTargets();
    }

    public Cell cell(int logicalX, int logicalZ) {
        if (logicalX < 0 || logicalX >= LOGICAL_SIZE || logicalZ < 0 || logicalZ >= LOGICAL_SIZE)
            throw new IndexOutOfBoundsException("arena cell");
        return cells[logicalX][logicalZ];
    }

    public WallMaterial wallMaterial(int logicalX, int logicalZ) {
        if (cell(logicalX, logicalZ) != Cell.WALL) throw new IllegalArgumentException("not a wall");
        if (logicalX == 0 || logicalZ == 0 || logicalX == LOGICAL_SIZE - 1 || logicalZ == LOGICAL_SIZE - 1)
            return WallMaterial.BOUNDARY;
        if (logicalX < LOGICAL_SIZE / 3) return WallMaterial.MDF;
        if (logicalX < LOGICAL_SIZE * 2 / 3) return WallMaterial.RIGID_PLASTIC;
        return WallMaterial.ABSORBENT;
    }

    public int startBlockX() { return blockCenter(1); }
    public int startBlockZ() { return blockCenter(1); }
    public int recoveryBlockX() { return blockCenter(LOGICAL_SIZE - 2); }
    public int recoveryBlockZ() { return blockCenter(LOGICAL_SIZE - 2); }
    public static int blockCenter(int logical) { return logical * SCALE + SCALE / 2; }

    private void carveMaze() {
        boolean[][] visited = new boolean[LOGICAL_SIZE][LOGICAL_SIZE];
        List<Point> stack = new ArrayList<Point>();
        Random random = new Random(0x43524c4152454e41L);
        Point current = new Point(1, 1);
        visited[1][1] = true; cells[1][1] = Cell.PASSAGE; stack.add(current);
        int[][] directions = { { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 } };
        while (!stack.isEmpty()) {
            current = stack.get(stack.size() - 1);
            List<Point> choices = new ArrayList<Point>(4);
            for (int[] direction : directions) {
                int nx = current.x + direction[0], nz = current.z + direction[1];
                if (nx > 0 && nx < LOGICAL_SIZE - 1 && nz > 0 && nz < LOGICAL_SIZE - 1
                        && !visited[nx][nz]) choices.add(new Point(nx, nz));
            }
            if (choices.isEmpty()) { stack.remove(stack.size() - 1); continue; }
            Point next = choices.get(random.nextInt(choices.size()));
            cells[(current.x + next.x) / 2][(current.z + next.z) / 2] = Cell.PASSAGE;
            cells[next.x][next.z] = Cell.PASSAGE; visited[next.x][next.z] = true; stack.add(next);
        }
    }

    private void carveBay(int minX, int minZ) {
        for (int x = minX; x <= minX + 2; x++) for (int z = minZ; z <= minZ + 2; z++)
            cells[x][z] = Cell.PASSAGE;
    }

    private void placeSmallTargets() {
        int placed = 0;
        for (int z = 1; z < LOGICAL_SIZE - 1 && placed < 4; z += 2)
            for (int x = 1; x < LOGICAL_SIZE - 1 && placed < 4; x += 2) {
                if ((x <= 3 && z <= 3) || (x >= LOGICAL_SIZE - 4 && z >= LOGICAL_SIZE - 4)) continue;
                if (cells[x][z] == Cell.PASSAGE && exits(x, z) == 1) {
                    cells[x][z] = (placed & 1) == 0 ? Cell.SMALL_MDF : Cell.SMALL_FOAM;
                    placed++;
                }
            }
    }

    private int exits(int x, int z) {
        int result = 0;
        if (cells[x - 1][z] != Cell.WALL) result++;
        if (cells[x + 1][z] != Cell.WALL) result++;
        if (cells[x][z - 1] != Cell.WALL) result++;
        if (cells[x][z + 1] != Cell.WALL) result++;
        return result;
    }

    private static final class Point {
        final int x, z;
        Point(int x, int z) { this.x = x; this.z = z; }
    }
}
