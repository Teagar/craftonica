package br.com.craftonica.electrical.nodal.forge;

import br.com.craftonica.electrical.nodal.CircuitDiagnostic;
import br.com.craftonica.electrical.nodal.ComponentSnapshot;
import java.util.*;

public final class NodalExtractionResult {
    private final List<ComponentSnapshot> snapshots;
    private final List<CircuitDiagnostic> diagnostics;

    NodalExtractionResult(List<ComponentSnapshot> snapshots, List<CircuitDiagnostic> diagnostics) {
        this.snapshots = Collections.unmodifiableList(new ArrayList<ComponentSnapshot>(snapshots));
        this.diagnostics = Collections.unmodifiableList(new ArrayList<CircuitDiagnostic>(diagnostics));
    }

    public List<ComponentSnapshot> getSnapshots() { return snapshots; }
    public List<CircuitDiagnostic> getDiagnostics() { return diagnostics; }
    public boolean isComplete() { return diagnostics.isEmpty(); }
}
