package br.com.craftonica.electrical.nodal;

public final class NodalBranch {
    private final BranchId id; private final TerminalId a; private final TerminalId b;
    public NodalBranch(BranchId id, TerminalId a, TerminalId b) { if(id==null||a==null||b==null) throw new IllegalArgumentException("Ramo invalido"); this.id=id;this.a=a;this.b=b; }
    public BranchId getId(){return id;} public TerminalId getA(){return a;} public TerminalId getB(){return b;}
}
