package br.com.craftonica.robot.modular;

public enum Direction {
    DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1),
    WEST(-1, 0, 0), EAST(1, 0, 0);

    public final GridVector vector;

    Direction(int x, int y, int z) { vector = new GridVector(x, y, z); }

    public Direction opposite() {
        switch (this) {
            case DOWN: return UP;
            case UP: return DOWN;
            case NORTH: return SOUTH;
            case SOUTH: return NORTH;
            case WEST: return EAST;
            default: return WEST;
        }
    }

    static Direction fromVector(GridVector vector) {
        for (Direction direction : values()) if (direction.vector.equals(vector)) return direction;
        throw new IllegalArgumentException("vector is not a unit direction");
    }
}
