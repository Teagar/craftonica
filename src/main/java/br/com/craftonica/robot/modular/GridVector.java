package br.com.craftonica.robot.modular;

public final class GridVector {
    public static final GridVector ZERO = new GridVector(0, 0, 0);

    public final int x;
    public final int y;
    public final int z;

    public GridVector(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public GridVector add(GridVector other) {
        if (other == null) throw new IllegalArgumentException("other");
        return new GridVector(x + other.x, y + other.y, z + other.z);
    }

    public GridVector subtract(GridVector other) {
        if (other == null) throw new IllegalArgumentException("other");
        return new GridVector(x - other.x, y - other.y, z - other.z);
    }

    public GridVector scale(int factor) { return new GridVector(x * factor, y * factor, z * factor); }

    @Override public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof GridVector)) return false;
        GridVector other = (GridVector) value;
        return x == other.x && y == other.y && z == other.z;
    }

    @Override public int hashCode() { return (x * 31 + y) * 31 + z; }

    @Override public String toString() { return x + ":" + y + ":" + z; }
}
