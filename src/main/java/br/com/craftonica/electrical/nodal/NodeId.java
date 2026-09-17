package br.com.craftonica.electrical.nodal;

public final class NodeId implements Comparable<NodeId> {
    public static final NodeId REFERENCE = new NodeId("REFERENCE");
    private final String value;
    public NodeId(TerminalId representative) { this(representative.toString()); }
    private NodeId(String value) { this.value = value; }
    public String getValue() { return value; }
    public boolean isReference() { return this == REFERENCE || "REFERENCE".equals(value); }
    @Override public int compareTo(NodeId o) { return value.compareTo(o.value); }
    @Override public boolean equals(Object o) { return o instanceof NodeId && value.equals(((NodeId)o).value); }
    @Override public int hashCode() { return value.hashCode(); }
    @Override public String toString() { return value; }
}
