package br.com.craftonica.network;

import br.com.craftonica.electrical.nodal.BranchId;
import br.com.craftonica.electrical.nodal.BranchResult;
import br.com.craftonica.electrical.nodal.CircuitDiagnostic;
import br.com.craftonica.electrical.nodal.ComponentSnapshot;
import br.com.craftonica.electrical.nodal.SolveStatus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable published electrical state used by server-side lesson checks. */
public final class ElectricalNetworkSnapshot {
    private final long generation;
    private final List<ComponentSnapshot> components;
    private final SolveStatus status;
    private final Map<BranchId, BranchResult> branches;
    private final List<CircuitDiagnostic> diagnostics;

    public ElectricalNetworkSnapshot(long generation, List<ComponentSnapshot> components, SolveStatus status,
                                     Map<BranchId, BranchResult> branches, List<CircuitDiagnostic> diagnostics) {
        if (components == null || status == null || branches == null || diagnostics == null)
            throw new IllegalArgumentException("Snapshot eletrico invalido");
        this.generation = generation;
        this.components = Collections.unmodifiableList(new ArrayList<ComponentSnapshot>(components));
        this.status = status;
        this.branches = Collections.unmodifiableMap(new LinkedHashMap<BranchId, BranchResult>(branches));
        this.diagnostics = Collections.unmodifiableList(new ArrayList<CircuitDiagnostic>(diagnostics));
    }

    public long getGeneration() { return generation; }
    public List<ComponentSnapshot> getComponents() { return components; }
    public SolveStatus getStatus() { return status; }
    public Map<BranchId, BranchResult> getBranches() { return branches; }
    public List<CircuitDiagnostic> getDiagnostics() { return diagnostics; }
    public boolean isSolved() { return status == SolveStatus.SOLVED; }
}
