package br.com.craftonica.network;

public final class BlockPosition {
    public final int x;
    public final int y;
    public final int z;

    public BlockPosition(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BlockPosition)) {
            return false;
        }
        BlockPosition position = (BlockPosition) other;
        return x == position.x && y == position.y && z == position.z;
    }

    @Override
    public int hashCode() {
        int result = x;
        result = 31 * result + y;
        return 31 * result + z;
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
