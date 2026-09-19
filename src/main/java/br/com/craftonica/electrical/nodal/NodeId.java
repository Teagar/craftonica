package br.com.craftonica.electrical.nodal;

public final class NodeId implements Comparable<NodeId> {
    public static final NodeId REFERENCE = new NodeId("REFERENCE");
    private final String value;
    private final TerminalId representative;
    public NodeId(TerminalId representative) {
        if (representative == null) throw new IllegalArgumentException("No invalido");
        this.representative = representative;
        this.value = representative.toString();
    }
    public NodeId(String value) {
        this.value = value;
        this.representative = null;
        if (value == null || value.length() == 0) throw new IllegalArgumentException("No invalido");
    }
    public static NodeId named(String value) { return new NodeId(value); }
    public String getValue() { return value; }
    public boolean isReference() { return this == REFERENCE || "REFERENCE".equals(value); }
    @Override public int compareTo(NodeId o) {
        if (isReference() || o.isReference()) return isReference() ? (o.isReference() ? 0 : -1) : 1;
        if (representative != null && o.representative != null) return representative.compareTo(o.representative);
        if (representative != null || o.representative != null) return representative != null ? -1 : 1;
        return value.compareTo(o.value);
    }
    @Override public boolean equals(Object o) { return o instanceof NodeId && compareTo((NodeId)o) == 0; }
    @Override public int hashCode() { return representative == null ? value.hashCode() : representative.hashCode(); }
    @Override public String toString() { return value; }
}
