package br.com.craftonica.lesson;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

public final class LessonCatalog {
    private static final Map<String, LessonDefinition> LESSONS;

    static {
        Map<String, LessonDefinition> lessons = new LinkedHashMap<String, LessonDefinition>();
        LessonDefinition ohmLed = new LessonDefinition("ohm-led-220",
                new LinkedHashSet<String>(Arrays.asList("wire", "source", "ground", "switch", "resistor", "led")),
                Arrays.asList(
                        new LessonDefinition.ComponentRule("source", 1, 1),
                        new LessonDefinition.ComponentRule("ground", 1, 1),
                        new LessonDefinition.ComponentRule("switch", 1, 1),
                        new LessonDefinition.ComponentRule("resistor", 1, 1),
                        new LessonDefinition.ComponentRule("led", 1, 1)),
                Arrays.asList(
                        new LessonDefinition.ElectricalGoal("switch", LessonDefinition.Quantity.CURRENT,
                                0.012987012987, 0.001, 0.05, true),
                        new LessonDefinition.ElectricalGoal("led", LessonDefinition.Quantity.CURRENT,
                                0.012987012987, 0.001, 0.05, true)));
        lessons.put(ohmLed.getId(), ohmLed);
        LESSONS = Collections.unmodifiableMap(lessons);
    }

    private LessonCatalog() { }

    public static LessonDefinition get(String id) { return LESSONS.get(id); }
    public static Iterable<String> ids() { return LESSONS.keySet(); }
}
