package br.com.craftonica.electrical.nodal;

import br.com.craftonica.network.BlockPosition;

public final class BranchId implements Comparable<BranchId> {
    private final BlockPosition position; private final String componentKind; private final int ordinal;
    public BranchId(BlockPosition position, String componentKind, int ordinal) {
        if (position == null || componentKind == null || ordinal < 0) throw new IllegalArgumentException("Ramo invalido");
        this.position = position; this.componentKind = componentKind; this.ordinal = ordinal;
    }
    public BlockPosition getPosition() { return position; }
    public String getComponentKind() { return componentKind; }
    public int getOrdinal() { return ordinal; }
    @Override public int compareTo(BranchId o) {
        int c = Integer.compare(position.x, o.position.x); if (c == 0) c = Integer.compare(position.y, o.position.y);
        if (c == 0) c = Integer.compare(position.z, o.position.z); if (c == 0) c = componentKind.compareTo(o.componentKind);
        return c == 0 ? Integer.compare(ordinal, o.ordinal) : c;
    }
    @Override public boolean equals(Object o) { return o instanceof BranchId && compareTo((BranchId)o) == 0; }
    @Override public int hashCode() { return position.hashCode() * 31 + componentKind.hashCode() * 31 + ordinal; }
    @Override public String toString() { return position + ":" + componentKind + ":" + ordinal; }
}
