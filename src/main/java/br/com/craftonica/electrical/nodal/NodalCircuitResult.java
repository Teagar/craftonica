package br.com.craftonica.electrical.nodal;
import java.util.*;
public final class NodalCircuitResult {
    public enum SolveStatus { VALID, INVALID, PENDING }
    private final SolveStatus status; private final NodalCircuit circuit; private final List<CircuitDiagnostic> diagnostics;
    public NodalCircuitResult(SolveStatus status,NodalCircuit circuit,List<CircuitDiagnostic> diagnostics){this.status=status;this.circuit=circuit;this.diagnostics=Collections.unmodifiableList(new ArrayList<CircuitDiagnostic>(diagnostics));}
    public SolveStatus getStatus(){return status;} public NodalCircuit getCircuit(){return circuit;} public List<CircuitDiagnostic> getDiagnostics(){return diagnostics;}
}
