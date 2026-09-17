package br.com.craftonica.lesson;

import br.com.craftonica.electrical.nodal.DiagnosticCode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class LessonDefinition {
    public enum Quantity { CURRENT, VOLTAGE, POWER }
    public enum Quantifier { ANY, ALL }

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
        private final Quantifier quantifier;

        public ElectricalGoal(String componentKind, Quantity quantity, double expected,
                              double absoluteTolerance, double relativeTolerance, boolean magnitude) {
            this(componentKind, quantity, expected, absoluteTolerance, relativeTolerance, magnitude, Quantifier.ANY);
        }

        public ElectricalGoal(String componentKind, Quantity quantity, double expected,
                              double absoluteTolerance, double relativeTolerance, boolean magnitude,
                              Quantifier quantifier) {
            if (componentKind == null || quantity == null || !finite(expected) || absoluteTolerance < 0.0
                    || relativeTolerance < 0.0 || !finite(absoluteTolerance) || !finite(relativeTolerance)
                    || quantifier == null)
                throw new IllegalArgumentException("Objetivo invalido");
            this.componentKind = componentKind;
            this.quantity = quantity;
            this.expected = expected;
            this.absoluteTolerance = absoluteTolerance;
            this.relativeTolerance = relativeTolerance;
            this.magnitude = magnitude;
            this.quantifier = quantifier;
        }

        public String getComponentKind() { return componentKind; }
        public Quantity getQuantity() { return quantity; }
        public double getExpected() { return expected; }
        public double getAbsoluteTolerance() { return absoluteTolerance; }
        public double getRelativeTolerance() { return relativeTolerance; }
        public double getTolerance() { return Math.max(absoluteTolerance, relativeTolerance * Math.abs(expected)); }
        public boolean isMagnitude() { return magnitude; }
        public Quantifier getQuantifier() { return quantifier; }
    }

    private final String id;
    private final Set<String> allowedKinds;
    private final List<ComponentRule> componentRules;
    private final List<ElectricalGoal> goals;
    private final Set<DiagnosticCode> requiredDiagnostics;

    public LessonDefinition(String id, Set<String> allowedKinds, List<ComponentRule> componentRules,
                            List<ElectricalGoal> goals) {
        this(id, allowedKinds, componentRules, goals, Collections.<DiagnosticCode>emptySet());
    }

    public LessonDefinition(String id, Set<String> allowedKinds, List<ComponentRule> componentRules,
                            List<ElectricalGoal> goals, Set<DiagnosticCode> requiredDiagnostics) {
        if (id == null || id.isEmpty() || allowedKinds == null || componentRules == null || goals == null)
            throw new IllegalArgumentException("Licao invalida");
        this.id = id;
        this.allowedKinds = Collections.unmodifiableSet(new LinkedHashSet<String>(allowedKinds));
        this.componentRules = Collections.unmodifiableList(new ArrayList<ComponentRule>(componentRules));
        this.goals = Collections.unmodifiableList(new ArrayList<ElectricalGoal>(goals));
        this.requiredDiagnostics = Collections.unmodifiableSet(new LinkedHashSet<DiagnosticCode>(requiredDiagnostics));
    }

    public String getId() { return id; }
    public Set<String> getAllowedKinds() { return allowedKinds; }
    public List<ComponentRule> getComponentRules() { return componentRules; }
    public List<ElectricalGoal> getGoals() { return goals; }
    public Set<DiagnosticCode> getRequiredDiagnostics() { return requiredDiagnostics; }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
