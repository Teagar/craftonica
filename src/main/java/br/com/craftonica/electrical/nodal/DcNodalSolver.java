package br.com.craftonica.electrical.nodal;

import java.util.*;

/** Deterministic DC MNA solver, including the bounded LED active-set model. */
public strictfp final class DcNodalSolver {
    private static final double LED_ON_TOLERANCE = 1e-9;
    private static final double LED_REVERSE_TOLERANCE = 1e-12;

    public NodalCircuitResult solve(MnaSystem system) {
        if (system == null) return failure(SolveStatus.NON_FINITE_VALUE, DiagnosticCode.NON_FINITE_VALUE);
        List<MnaSystem.Element> elements = system.getElements();
        List<MnaSystem.Element> leds = leds(elements);
        if (leds.size() > NodalLimits.MAX_ACTIVE_LEDS)
            return failure(SolveStatus.NONLINEAR_LIMIT, DiagnosticCode.NONLINEAR_LIMIT);
        boolean[] active = new boolean[leds.size()];
        for (int i = 0; i < leds.size(); i++) active[i] = false;

        LinearResult last = null;
        for (int iteration = 0; iteration < NodalLimits.MAX_ITERATIONS; iteration++) {
            last = solveLinear(system, active, leds);
            if (!last.result.isSolved()) return last.result;
            boolean changed = false;
            for (int i = 0; i < leds.size(); i++) {
                MnaSystem.Element led = leds.get(i);
                if (led.isBurned()) continue;
                BranchResult branch = last.result.getBranchResult(led.getId());
                boolean shouldBeOn = branch != null && branch.getVoltage() > led.getValue() + LED_ON_TOLERANCE;
                if (active[i] && branch != null && branch.getCurrent() < -LED_REVERSE_TOLERANCE) shouldBeOn = false;
                if (active[i] != shouldBeOn) { active[i] = shouldBeOn; changed = true; }
            }
            if (!changed) return withDiagnostics(last.result, diagnostics(elements, last.result, leds, active));
        }
        return failure(SolveStatus.NON_CONVERGENT, DiagnosticCode.NON_CONVERGENT);
    }

    private LinearResult solveLinear(MnaSystem system, boolean[] active, List<MnaSystem.Element> leds) {
        List<MnaSystem.Element> elements = system.getElements();
        List<NodeId> nodes = system.getNodes();
        if (nodes.size() + countSources(elements) > NodalLimits.MAX_UNKNOWNS)
            return new LinearResult(failure(SolveStatus.MATRIX_LIMIT, DiagnosticCode.MATRIX_LIMIT));
        if (!hasReference(elements)) return new LinearResult(failure(SolveStatus.MISSING_REFERENCE, DiagnosticCode.MISSING_REFERENCE));
        Set<NodeId> connected = connectedToReference(elements, leds, active);
        for (NodeId node : nodes) if (!connected.contains(node))
            return new LinearResult(failure(SolveStatus.FLOATING_NODE, DiagnosticCode.FLOATING_NODE));

        int nodeCount = nodes.size();
        int sourceCount = countSources(elements);
        double[][] matrix = new double[nodeCount + sourceCount][nodeCount + sourceCount];
        double[] rhs = new double[matrix.length];
        Map<NodeId, Integer> indices = new HashMap<NodeId, Integer>();
        for (int i = 0; i < nodeCount; i++) indices.put(nodes.get(i), i);
        Map<MnaSystem.Element, Integer> sourceIndices = new HashMap<MnaSystem.Element, Integer>();
        int sourceIndex = nodeCount;
        for (MnaSystem.Element element : elements) {
            if (element.getKind() == MnaSystem.Element.Kind.VOLTAGE_SOURCE) {
                sourceIndices.put(element, sourceIndex);
                stampSource(matrix, rhs, indices, sourceIndex++, element);
            } else if (conductive(element, leds, active)) {
                stampConductive(matrix, rhs, indices, element);
            }
        }
        DenseLuSolver.Result solved = DenseLuSolver.solve(matrix, rhs);
        if (solved.getStatus() != SolveStatus.SOLVED)
            return new LinearResult(failure(solved.getStatus(), diagnostic(solved.getStatus())));
        double[] solution = solved.getSolution();
        Map<NodeId, NodeResult> nodeResults = new LinkedHashMap<NodeId, NodeResult>();
        for (NodeId node : nodes) nodeResults.put(node, new NodeResult(node, value(node, indices, solution), ValueValidity.VALID));
        Map<BranchId, BranchResult> branchResults = new LinkedHashMap<BranchId, BranchResult>();
        for (MnaSystem.Element element : elements) {
            double voltage = value(element.getA(), indices, solution) - value(element.getB(), indices, solution);
            double current = current(element, voltage, solution, sourceIndices, leds, active);
            branchResults.put(element.getId(), new BranchResult(element.getId(), voltage, current,
                    voltage * current, ValueValidity.VALID));
        }
        return new LinearResult(new NodalCircuitResult(SolveStatus.SOLVED, nodeResults, branchResults,
                Collections.<CircuitDiagnostic>emptyList(), solved.getResidual(), solved.getConditionEstimate()));
    }

    private static boolean conductive(MnaSystem.Element e, List<MnaSystem.Element> leds, boolean[] active) {
        if (e.getKind() == MnaSystem.Element.Kind.RESISTOR) return true;
        if (e.getKind() == MnaSystem.Element.Kind.SWITCH || e.getKind() == MnaSystem.Element.Kind.BREAKER) return e.isClosed();
        if (e.getKind() == MnaSystem.Element.Kind.VOLTAGE_SOURCE) return true;
        int index = leds.indexOf(e);
        return index >= 0 && active[index] && !e.isBurned();
    }

    private static void stampConductive(double[][] a, double[] z, Map<NodeId, Integer> ix, MnaSystem.Element e) {
        boolean diode = e.getKind() == MnaSystem.Element.Kind.LED || e.getKind() == MnaSystem.Element.Kind.DIODE;
        double conductance = diode ? 1.0 / e.getDynamicResistance() : 1.0 / e.getValue();
        add(a, ix, e.getA(), e.getA(), conductance);
        add(a, ix, e.getB(), e.getB(), conductance);
        add(a, ix, e.getA(), e.getB(), -conductance);
        add(a, ix, e.getB(), e.getA(), -conductance);
        if (diode) {
            add(z, ix, e.getA(), conductance * e.getValue());
            add(z, ix, e.getB(), -conductance * e.getValue());
        }
    }

    private static double current(MnaSystem.Element e, double voltage, double[] x,
                                  Map<MnaSystem.Element, Integer> sourceIndices,
                                  List<MnaSystem.Element> leds, boolean[] active) {
        if (e.getKind() == MnaSystem.Element.Kind.VOLTAGE_SOURCE) return x[sourceIndices.get(e)];
        if (e.getKind() == MnaSystem.Element.Kind.LED || e.getKind() == MnaSystem.Element.Kind.DIODE) {
            int index = leds.indexOf(e);
            return index >= 0 && active[index] && !e.isBurned() ? (voltage - e.getValue()) / e.getDynamicResistance() : 0.0;
        }
        if ((e.getKind() == MnaSystem.Element.Kind.SWITCH || e.getKind() == MnaSystem.Element.Kind.BREAKER) && !e.isClosed()) return 0.0;
        return voltage / e.getValue();
    }

    private static List<MnaSystem.Element> leds(List<MnaSystem.Element> elements) {
        List<MnaSystem.Element> result = new ArrayList<MnaSystem.Element>();
        for (MnaSystem.Element e : elements) if (e.getKind() == MnaSystem.Element.Kind.LED || e.getKind() == MnaSystem.Element.Kind.DIODE) result.add(e);
        return result;
    }

    private static List<CircuitDiagnostic> diagnostics(List<MnaSystem.Element> elements, NodalCircuitResult result,
                                                       List<MnaSystem.Element> leds, boolean[] active) {
        List<CircuitDiagnostic> diagnostics = new ArrayList<CircuitDiagnostic>();
        for (int i = 0; i < leds.size(); i++) {
            MnaSystem.Element led = leds.get(i);
            BranchResult branch = result.getBranchResult(led.getId());
            if (branch == null) continue;
            if (!active[i] && branch.getVoltage() < -LED_REVERSE_TOLERANCE)
                diagnostics.add(diagnostic(DiagnosticCode.POLARITY_INCORRECT, led.getId().getPosition()));
            if (active[i] && branch.getCurrent() > 0.02)
                diagnostics.add(diagnostic(DiagnosticCode.LED_ABOVE_RECOMMENDED_CURRENT, led.getId().getPosition()));
            if (active[i] && branch.getCurrent() > 0.03)
                diagnostics.add(diagnostic(DiagnosticCode.LED_OVERCURRENT, led.getId().getPosition()));
            if (active[i] && branch.getAbsorbedPower() > 0.1)
                diagnostics.add(diagnostic(DiagnosticCode.POWER_EXCEEDED, led.getId().getPosition()));
        }
        for (MnaSystem.Element element : elements) {
            BranchResult branch = result.getBranchResult(element.getId());
            if (branch == null) continue;
            if (element.getKind() == MnaSystem.Element.Kind.VOLTAGE_SOURCE && Math.abs(branch.getCurrent()) > 0.1)
                diagnostics.add(diagnostic(DiagnosticCode.SOURCE_OVERCURRENT, element.getId().getPosition()));
            if ((element.getKind() == MnaSystem.Element.Kind.RESISTOR || element.getKind() == MnaSystem.Element.Kind.SWITCH || element.getKind() == MnaSystem.Element.Kind.BREAKER)
                    && element.getValue() < 1.0 && Math.abs(branch.getCurrent()) > 0.1)
                diagnostics.add(diagnostic(DiagnosticCode.SHORT_CIRCUIT, element.getId().getPosition()));
        }
        Collections.sort(diagnostics);
        return diagnostics;
    }

    private static CircuitDiagnostic diagnostic(DiagnosticCode code) { return new CircuitDiagnostic(code, CircuitDiagnostic.Severity.WARNING, Collections.emptyList()); }
    private static CircuitDiagnostic diagnostic(DiagnosticCode code, br.com.craftonica.network.BlockPosition position) {
        return new CircuitDiagnostic(code, CircuitDiagnostic.Severity.WARNING, Collections.singletonList(position));
    }
    private static NodalCircuitResult withDiagnostics(NodalCircuitResult result, List<CircuitDiagnostic> diagnostics) {
        return new NodalCircuitResult(result.getStatus(), result.getNodeResults(), result.getBranchResults(), diagnostics,
                result.getResidual(), result.getConditionEstimate());
    }
    private static int countSources(List<MnaSystem.Element> elements) { int count = 0; for (MnaSystem.Element e : elements) if (e.getKind() == MnaSystem.Element.Kind.VOLTAGE_SOURCE) count++; return count; }
    private static boolean hasReference(List<MnaSystem.Element> elements) { for (MnaSystem.Element e : elements) if (e.getA().isReference() || e.getB().isReference()) return true; return false; }
    private static Set<NodeId> connectedToReference(List<MnaSystem.Element> elements, List<MnaSystem.Element> leds, boolean[] active) {
        Set<NodeId> seen = new HashSet<NodeId>(); Deque<NodeId> queue = new ArrayDeque<NodeId>(); queue.add(NodeId.REFERENCE);
        while (!queue.isEmpty()) { NodeId node = queue.remove(); for (MnaSystem.Element e : elements) if (conductive(e, leds, active) || diodeTopology(e)) {
            NodeId next = e.getA().equals(node) ? e.getB() : e.getB().equals(node) ? e.getA() : null;
            if (next != null && !next.isReference() && seen.add(next)) queue.add(next);
        }} return seen;
    }
    private static boolean diodeTopology(MnaSystem.Element e) {
        return (e.getKind() == MnaSystem.Element.Kind.LED || e.getKind() == MnaSystem.Element.Kind.DIODE) && !e.isBurned();
    }
    private static void stampSource(double[][] a, double[] z, Map<NodeId, Integer> ix, int k, MnaSystem.Element e) {
        Integer p = ix.get(e.getA()), q = ix.get(e.getB()); if (p != null) a[p][k] += 1; if (q != null) a[q][k] -= 1;
        if (p != null) a[k][p] += 1; if (q != null) a[k][q] -= 1; z[k] += e.getValue();
    }
    private static void add(double[][] a, Map<NodeId, Integer> ix, NodeId row, NodeId col, double value) { Integer i = ix.get(row), j = ix.get(col); if (i != null && j != null) a[i][j] += value; }
    private static void add(double[] z, Map<NodeId, Integer> ix, NodeId row, double value) { Integer i = ix.get(row); if (i != null) z[i] += value; }
    private static double value(NodeId node, Map<NodeId, Integer> ix, double[] x) { Integer i = ix.get(node); return i == null ? 0.0 : x[i]; }
    private static DiagnosticCode diagnostic(SolveStatus status) { switch (status) {
        case SINGULAR_MATRIX: return DiagnosticCode.SINGULAR_MATRIX; case ILL_CONDITIONED_MATRIX: return DiagnosticCode.ILL_CONDITIONED_MATRIX;
        case RESIDUAL_TOO_LARGE: return DiagnosticCode.RESIDUAL_TOO_LARGE; case CONFLICTING_CONSTRAINTS: return DiagnosticCode.CONFLICTING_CONSTRAINTS;
        default: return DiagnosticCode.NON_FINITE_VALUE; } }
    private static NodalCircuitResult failure(SolveStatus status, DiagnosticCode code) {
        return new NodalCircuitResult(status, Collections.<NodeId, NodeResult>emptyMap(), Collections.<BranchId, BranchResult>emptyMap(),
                Collections.singletonList(new CircuitDiagnostic(code, CircuitDiagnostic.Severity.ERROR, Collections.emptyList())), Double.NaN, Double.NaN);
    }
    private static final class LinearResult { private final NodalCircuitResult result; private LinearResult(NodalCircuitResult result) { this.result = result; } }
}
