package br.com.craftonica.lesson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LessonDefinition {
    public enum Quantity { CURRENT, VOLTAGE, POWER }

    public static final class ComponentRule {
        private final String kind;
        private final int minimum;
        private final int maximum;

        public ComponentRule(String kind, int minimum, int maximum) {
            if (kind == null || minimum < 0 || maximum < minimum) throw new IllegalArgumentException("Regra invalida");
            this.kind = kind;
            this.minimum = minimum;
            this.maximum = maximum;
        }

        public String getKind() { return kind; }
        public int getMinimum() { return minimum; }
        public int getMaximum() { return maximum; }
    }

    public static final class ElectricalGoal {
        private final String componentKind;
        private final Quantity quantity;
        private final double expected;
        private final double absoluteTolerance;
        private final double relativeTolerance;
        private final boolean magnitude;

        public ElectricalGoal(String componentKind, Quantity quantity, double expected,
                              double absoluteTolerance, double relativeTolerance, boolean magnitude) {
            if (componentKind == null || quantity == null || !finite(expected) || absoluteTolerance < 0.0
                    || relativeTolerance < 0.0 || !finite(absoluteTolerance) || !finite(relativeTolerance))
                throw new IllegalArgumentException("Objetivo invalido");
            this.componentKind = componentKind;
            this.quantity = quantity;
            this.expected = expected;
            this.absoluteTolerance = absoluteTolerance;
            this.relativeTolerance = relativeTolerance;
            this.magnitude = magnitude;
        }

        public String getComponentKind() { return componentKind; }
        public Quantity getQuantity() { return quantity; }
        public double getExpected() { return expected; }
        public double getTolerance() { return Math.max(absoluteTolerance, relativeTolerance * Math.abs(expected)); }
        public boolean isMagnitude() { return magnitude; }
    }

    private final String id;
    private final Set<String> allowedKinds;
    private final List<ComponentRule> componentRules;
    private final List<ElectricalGoal> goals;

    public LessonDefinition(String id, Set<String> allowedKinds, List<ComponentRule> componentRules,
                            List<ElectricalGoal> goals) {
        if (id == null || id.isEmpty() || allowedKinds == null || componentRules == null || goals == null)
            throw new IllegalArgumentException("Licao invalida");
        this.id = id;
        this.allowedKinds = Collections.unmodifiableSet(new LinkedHashSet<String>(allowedKinds));
        this.componentRules = Collections.unmodifiableList(new ArrayList<ComponentRule>(componentRules));
        this.goals = Collections.unmodifiableList(new ArrayList<ElectricalGoal>(goals));
    }

    public String getId() { return id; }
    public Set<String> getAllowedKinds() { return allowedKinds; }
    public List<ComponentRule> getComponentRules() { return componentRules; }
    public List<ElectricalGoal> getGoals() { return goals; }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
