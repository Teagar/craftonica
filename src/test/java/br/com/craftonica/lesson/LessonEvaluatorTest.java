package br.com.craftonica.lesson;

import br.com.craftonica.electrical.nodal.BranchId;
import br.com.craftonica.electrical.nodal.BranchResult;
import br.com.craftonica.electrical.nodal.ComponentSnapshot;
import br.com.craftonica.electrical.nodal.SolveStatus;
import br.com.craftonica.electrical.nodal.TerminalSnapshot;
import br.com.craftonica.electrical.nodal.ValueValidity;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkSnapshot;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LessonEvaluatorTest {
    private final LessonEvaluator evaluator = new LessonEvaluator();

    @Test
    public void acceptsElectricallyEquivalentLayouts() {
        LessonDefinition lesson = lesson(0.013, 0.001);
        assertTrue(evaluator.evaluate(lesson, network(0, 0.013)).isSuccess());
        assertTrue(evaluator.evaluate(lesson, network(100, 0.013)).isSuccess());
    }

    @Test
    public void toleranceBoundaryIsInclusiveAndOutsideValueIsActionable() {
        LessonDefinition lesson = lesson(0.013, 0.001);
        assertTrue(evaluator.evaluate(lesson, network(0, 0.014)).isSuccess());
        LessonEvaluation result = evaluator.evaluate(lesson, network(0, 0.014001));
        assertFalse(result.isSuccess());
        assertEquals(LessonEvaluation.FailureCode.GOAL_OUTSIDE_TOLERANCE,
                result.getFailures().get(0).getCode());
    }

    @Test
    public void forbiddenComponentsFailBeforeElectricalGoals() {
        ElectricalNetworkSnapshot base = network(0, 0.013);
        List<ComponentSnapshot> components = Arrays.asList(component(0, "source"), component(1, "led"),
                component(2, "diode"));
        ElectricalNetworkSnapshot forbidden = new ElectricalNetworkSnapshot(1, components, SolveStatus.SOLVED,
                base.getBranches(), Collections.emptyList());
        LessonEvaluation result = evaluator.evaluate(lesson(0.013, 0.001), forbidden);
        assertEquals(LessonEvaluation.FailureCode.FORBIDDEN_COMPONENT, result.getFailures().get(0).getCode());
    }

    @Test
    public void pendingAndUnsolvedNetworksNeverComplete() {
        assertEquals(LessonEvaluation.FailureCode.PENDING,
                evaluator.evaluate(lesson(0.013, 0.001), null).getFailures().get(0).getCode());
        ElectricalNetworkSnapshot unsolved = new ElectricalNetworkSnapshot(1, Collections.<ComponentSnapshot>emptyList(),
                SolveStatus.FLOATING_NODE, Collections.<BranchId, BranchResult>emptyMap(), Collections.emptyList());
        assertEquals(LessonEvaluation.FailureCode.UNSOLVED,
                evaluator.evaluate(lesson(0.013, 0.001), unsolved).getFailures().get(0).getCode());
    }

    @Test
    public void catalogLessonRequiresTheSwitchToCarryTargetCurrent() {
        List<ComponentSnapshot> components = Arrays.asList(component(0, "source"), component(1, "switch"),
                component(2, "resistor"), component(3, "led"), component(4, "ground"));
        Map<BranchId, BranchResult> branches = new LinkedHashMap<BranchId, BranchResult>();
        branch(branches, 1, "switch", 0.0);
        branch(branches, 3, "led", 0.012987012987);
        ElectricalNetworkSnapshot snapshot = new ElectricalNetworkSnapshot(1, components, SolveStatus.SOLVED,
                branches, Collections.emptyList());
        LessonEvaluation result = evaluator.evaluate(LessonCatalog.get("ohm-led-220"), snapshot);
        assertFalse(result.isSuccess());
        assertEquals("switch.current", result.getFailures().get(0).getSubject());

        branches.clear();
        branch(branches, 1, "switch", 0.012987012987);
        branch(branches, 3, "led", 0.012987012987);
        snapshot = new ElectricalNetworkSnapshot(2, components, SolveStatus.SOLVED,
                branches, Collections.emptyList());
        assertTrue(evaluator.evaluate(LessonCatalog.get("ohm-led-220"), snapshot).isSuccess());
    }

    private LessonDefinition lesson(double expected, double tolerance) {
        return new LessonDefinition("test", new LinkedHashSet<String>(Arrays.asList("source", "led")),
                Arrays.asList(new LessonDefinition.ComponentRule("source", 1, 1),
                        new LessonDefinition.ComponentRule("led", 1, 1)),
                Collections.singletonList(new LessonDefinition.ElectricalGoal("led",
                        LessonDefinition.Quantity.CURRENT, expected, tolerance, 0.0, true)));
    }

    private ElectricalNetworkSnapshot network(int offset, double current) {
        BlockPosition led = new BlockPosition(offset + 1, 64, 0);
        BranchId id = new BranchId(led, "led", 0);
        Map<BranchId, BranchResult> branches = new LinkedHashMap<BranchId, BranchResult>();
        branches.put(id, new BranchResult(id, 2.0, current, 2.0 * current, ValueValidity.VALID));
        return new ElectricalNetworkSnapshot(1,
                Arrays.asList(component(offset, "source"), component(offset + 1, "led")),
                SolveStatus.SOLVED, branches, Collections.emptyList());
    }

    private ComponentSnapshot component(int x, String kind) {
        return new ComponentSnapshot(new BlockPosition(x, 64, 0), kind,
                Collections.<TerminalSnapshot>emptyList());
    }

    private void branch(Map<BranchId, BranchResult> branches, int x, String kind, double current) {
        BranchId id = new BranchId(new BlockPosition(x, 64, 0), kind, 0);
        branches.put(id, new BranchResult(id, 0.0, current, 0.0, ValueValidity.VALID));
    }
}
