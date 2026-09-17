package br.com.craftonica.lesson;

import br.com.craftonica.electrical.nodal.BranchResult;
import br.com.craftonica.electrical.nodal.ComponentSnapshot;
import br.com.craftonica.electrical.nodal.ValueValidity;
import br.com.craftonica.network.ElectricalNetworkSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LessonEvaluator {
    public LessonEvaluation evaluate(LessonDefinition lesson, ElectricalNetworkSnapshot snapshot) {
        List<LessonEvaluation.Failure> failures = new ArrayList<LessonEvaluation.Failure>();
        if (snapshot == null) {
            failures.add(failure(LessonEvaluation.FailureCode.PENDING, "network"));
            return new LessonEvaluation(failures);
        }
        if (!snapshot.isSolved()) {
            failures.add(failure(LessonEvaluation.FailureCode.UNSOLVED, snapshot.getStatus().name()));
            return new LessonEvaluation(failures);
        }

        Map<String, Integer> counts = new HashMap<String, Integer>();
        for (ComponentSnapshot component : snapshot.getComponents()) {
            Integer count = counts.get(component.getKind());
            counts.put(component.getKind(), count == null ? 1 : count + 1);
            if (!lesson.getAllowedKinds().contains(component.getKind())) {
                failures.add(new LessonEvaluation.Failure(LessonEvaluation.FailureCode.FORBIDDEN_COMPONENT,
                        component.getKind(), component.getPosition(), Double.NaN, Double.NaN, Double.NaN));
            }
        }
        for (LessonDefinition.ComponentRule rule : lesson.getComponentRules()) {
            Integer count = counts.get(rule.getKind());
            int actual = count == null ? 0 : count;
            if (actual < rule.getMinimum() || actual > rule.getMaximum()) {
                failures.add(new LessonEvaluation.Failure(LessonEvaluation.FailureCode.COMPONENT_COUNT,
                        rule.getKind(), null, rule.getMinimum(), actual, rule.getMaximum()));
            }
        }
        if (!failures.isEmpty()) return new LessonEvaluation(failures);

        for (LessonDefinition.ElectricalGoal goal : lesson.getGoals()) evaluateGoal(goal, snapshot, failures);
        return new LessonEvaluation(failures);
    }

    private void evaluateGoal(LessonDefinition.ElectricalGoal goal, ElectricalNetworkSnapshot snapshot,
                              List<LessonEvaluation.Failure> failures) {
        double closest = Double.NaN;
        double closestDistance = Double.POSITIVE_INFINITY;
        for (BranchResult branch : snapshot.getBranches().values()) {
            if (!goal.getComponentKind().equals(branch.getBranch().getComponentKind())
                    || branch.getValidity() != ValueValidity.VALID) continue;
            double actual = value(goal.getQuantity(), branch);
            if (goal.isMagnitude()) actual = Math.abs(actual);
            if (!finite(actual)) continue;
            double distance = Math.abs(actual - goal.getExpected());
            double roundingMargin = Math.ulp(Math.max(Math.abs(actual), Math.abs(goal.getExpected()))) * 4.0;
            if (distance <= goal.getTolerance() + roundingMargin) return;
            if (distance < closestDistance) { closest = actual; closestDistance = distance; }
        }
        LessonEvaluation.FailureCode code = finite(closest)
                ? LessonEvaluation.FailureCode.GOAL_OUTSIDE_TOLERANCE
                : LessonEvaluation.FailureCode.GOAL_UNAVAILABLE;
        failures.add(new LessonEvaluation.Failure(code,
                goal.getComponentKind() + "." + goal.getQuantity().name().toLowerCase(), null,
                goal.getExpected(), closest, goal.getTolerance()));
    }

    private double value(LessonDefinition.Quantity quantity, BranchResult branch) {
        switch (quantity) {
            case VOLTAGE: return branch.getVoltage();
            case POWER: return branch.getAbsorbedPower();
            default: return branch.getCurrent();
        }
    }

    private LessonEvaluation.Failure failure(LessonEvaluation.FailureCode code, String subject) {
        return new LessonEvaluation.Failure(code, subject, null, Double.NaN, Double.NaN, Double.NaN);
    }

    private boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
