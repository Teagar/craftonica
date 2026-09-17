package br.com.craftonica.lesson;

import br.com.craftonica.electrical.nodal.DiagnosticCode;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LessonCatalog {
    private static final Set<String> BASIC = kinds("wire", "source", "ground", "switch", "resistor", "led");
    private static final Map<String, LessonDefinition> LESSONS;

    static {
        Map<String, LessonDefinition> lessons = new LinkedHashMap<String, LessonDefinition>();
        add(lessons, new LessonDefinition("closed-circuit", kinds("wire", "source", "ground", "switch", "resistor"),
                rules(rule("source", 1), rule("ground", 1), rule("switch", 1), rule("resistor", 1)),
                goals(current("switch", 0.021739130435), current("resistor", 0.021739130435))));
        add(lessons, new LessonDefinition("ohm-led-220", BASIC,
                rules(rule("source", 1), rule("ground", 1), rule("switch", 1), rule("resistor", 1), rule("led", 1)),
                goals(current("switch", 0.012987012987), current("led", 0.012987012987))));
        add(lessons, new LessonDefinition("led-polarity", kinds("wire", "source", "ground", "resistor", "led"),
                rules(rule("source", 1), rule("ground", 1), rule("resistor", 1), rule("led", 1)),
                Collections.<LessonDefinition.ElectricalGoal>emptyList(), diagnostics(DiagnosticCode.POLARITY_INCORRECT)));
        add(lessons, new LessonDefinition("series-resistors", kinds("wire", "source", "ground", "resistor"),
                rules(rule("source", 1), rule("ground", 1), rule("resistor", 2)),
                goals(allCurrent("resistor", 0.011111111111))));
        add(lessons, new LessonDefinition("parallel-resistors", kinds("wire", "source", "ground", "resistor"),
                rules(rule("source", 1), rule("ground", 1), rule("resistor", 2)),
                goals(allCurrent("resistor", 0.020833333333))));
        add(lessons, new LessonDefinition("diagnose-short", kinds("wire", "source", "ground"),
                rules(rule("source", 1), rule("ground", 1)),
                Collections.<LessonDefinition.ElectricalGoal>emptyList(), diagnostics(DiagnosticCode.SHORT_CIRCUIT)));
        LESSONS = Collections.unmodifiableMap(lessons);
    }

    private LessonCatalog() { }

    public static LessonDefinition get(String id) { return LESSONS.get(id); }
    public static Iterable<String> ids() { return LESSONS.keySet(); }

    private static void add(Map<String, LessonDefinition> lessons, LessonDefinition lesson) {
        if (lessons.put(lesson.getId(), lesson) != null) throw new IllegalStateException("Licao duplicada");
    }

    private static LessonDefinition.ComponentRule rule(String kind, int count) {
        return new LessonDefinition.ComponentRule(kind, count, count);
    }

    private static List<LessonDefinition.ComponentRule> rules(LessonDefinition.ComponentRule... rules) {
        return Arrays.asList(rules);
    }

    private static LessonDefinition.ElectricalGoal current(String kind, double amperes) {
        return new LessonDefinition.ElectricalGoal(kind, LessonDefinition.Quantity.CURRENT,
                amperes, 0.001, 0.05, true);
    }

    private static LessonDefinition.ElectricalGoal allCurrent(String kind, double amperes) {
        return new LessonDefinition.ElectricalGoal(kind, LessonDefinition.Quantity.CURRENT,
                amperes, 0.001, 0.05, true, LessonDefinition.Quantifier.ALL);
    }

    private static List<LessonDefinition.ElectricalGoal> goals(LessonDefinition.ElectricalGoal... goals) {
        return Arrays.asList(goals);
    }

    private static Set<String> kinds(String... kinds) {
        return new LinkedHashSet<String>(Arrays.asList(kinds));
    }

    private static Set<DiagnosticCode> diagnostics(DiagnosticCode... diagnostics) {
        return new LinkedHashSet<DiagnosticCode>(Arrays.asList(diagnostics));
    }
}
