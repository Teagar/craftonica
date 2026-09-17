package br.com.craftonica.electrical.nodal;

import java.util.*;

public final class NodalCircuitResult {
    private final SolveStatus status; private final Map<NodeId,NodeResult> nodes; private final Map<BranchId,BranchResult> branches;
    private final List<CircuitDiagnostic> diagnostics; private final double residual,condition;
    NodalCircuitResult(SolveStatus status,Map<NodeId,NodeResult> nodes,Map<BranchId,BranchResult> branches,List<CircuitDiagnostic> diagnostics,double residual,double condition){
        this.status=status;this.nodes=immutable(nodes);this.branches=immutable(branches);List<CircuitDiagnostic> d=new ArrayList<CircuitDiagnostic>(diagnostics);Collections.sort(d);this.diagnostics=Collections.unmodifiableList(d);this.residual=residual;this.condition=condition;
    }
    private static <K,V> Map<K,V> immutable(Map<K,V> m){return Collections.unmodifiableMap(new LinkedHashMap<K,V>(m));}
    public SolveStatus getStatus(){return status;} public Map<NodeId,NodeResult> getNodeResults(){return nodes;} public Map<BranchId,BranchResult> getBranchResults(){return branches;}
    public NodeResult getNodeResult(NodeId n){return nodes.get(n);} public BranchResult getBranchResult(BranchId b){return branches.get(b);}
    public List<CircuitDiagnostic> getDiagnostics(){return diagnostics;} public double getResidual(){return residual;} public double getConditionEstimate(){return condition;}
    public boolean isSolved(){return status==SolveStatus.SOLVED;}
}
