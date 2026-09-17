package br.com.craftonica.electrical.nodal;

import br.com.craftonica.network.BlockPosition;
import java.util.*;

public final class NodalCircuit {
    private final List<NodeId> nodes; private final List<NodalBranch> branches; private final Map<TerminalId,NodeId> terminalToNode;
    private final List<ComponentSnapshot> snapshots; private final List<CircuitDiagnostic> diagnostics; private final String fingerprint;
    NodalCircuit(List<NodeId> nodes,List<NodalBranch> branches,Map<TerminalId,NodeId> terminals,List<ComponentSnapshot> snapshots,List<CircuitDiagnostic> diagnostics,String fingerprint){
        this.nodes=Collections.unmodifiableList(new ArrayList<NodeId>(nodes));this.branches=Collections.unmodifiableList(new ArrayList<NodalBranch>(branches));
        this.terminalToNode=Collections.unmodifiableMap(new LinkedHashMap<TerminalId,NodeId>(terminals));this.snapshots=Collections.unmodifiableList(new ArrayList<ComponentSnapshot>(snapshots));
        this.diagnostics=Collections.unmodifiableList(new ArrayList<CircuitDiagnostic>(diagnostics));this.fingerprint=fingerprint;
    }
    public List<NodeId> getNodes(){return nodes;} public List<NodalBranch> getBranches(){return branches;} public Map<TerminalId,NodeId> getTerminalToNode(){return terminalToNode;}
    public List<ComponentSnapshot> getSnapshots(){return snapshots;} public List<CircuitDiagnostic> getDiagnostics(){return diagnostics;} public String getTopologyFingerprint(){return fingerprint;}
    public boolean isValid(){for(CircuitDiagnostic d:diagnostics)if(d.getSeverity()==CircuitDiagnostic.Severity.ERROR)return false;return true;}
    public NodeId getNode(TerminalId id){return terminalToNode.get(id);}
}
