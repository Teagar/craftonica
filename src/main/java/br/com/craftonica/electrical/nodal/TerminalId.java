package br.com.craftonica.electrical.nodal;

import br.com.craftonica.network.BlockPosition;

public final class TerminalId implements Comparable<TerminalId> {
    private final BlockPosition position;
    private final Face face;
    private final int ordinal;

    public TerminalId(BlockPosition position, Face face, int ordinal) {
        if (position == null || face == null || ordinal < 0) throw new IllegalArgumentException("Terminal invalido");
        this.position = position; this.face = face; this.ordinal = ordinal;
    }
    public BlockPosition getPosition() { return position; }
    public Face getFace() { return face; }
    public int getOrdinal() { return ordinal; }
    @Override public int compareTo(TerminalId other) {
        int c = Integer.compare(position.x, other.position.x);
        if (c == 0) c = Integer.compare(position.y, other.position.y);
        if (c == 0) c = Integer.compare(position.z, other.position.z);
        if (c == 0) c = Integer.compare(face.ordinal(), other.face.ordinal());
        return c == 0 ? Integer.compare(ordinal, other.ordinal) : c;
    }
    @Override public boolean equals(Object o) { return o instanceof TerminalId && compareTo((TerminalId)o) == 0; }
    @Override public int hashCode() { return position.hashCode() * 31 * 31 + face.ordinal() * 31 + ordinal; }
    @Override public String toString() { return position + ":" + face + ":" + ordinal; }
}
