package br.com.craftonica.electrical.nodal;

import br.com.craftonica.network.BlockPosition;

public final class TerminalSnapshot {
    private final TerminalId id;
    public TerminalSnapshot(BlockPosition position, Face face) { this(position, face, 0); }
    public TerminalSnapshot(BlockPosition position, Face face, int ordinal) { id = new TerminalId(position, face, ordinal); }
    public TerminalId getId() { return id; }
}
