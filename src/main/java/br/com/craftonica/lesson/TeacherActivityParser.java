package br.com.craftonica.lesson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TeacherActivityParser {
    private static final Set<String> KNOWN_KINDS = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
            "wire", "source", "ground", "switch", "breaker", "resistor", "potentiometer", "led", "diode")));

    private TeacherActivityParser() { }

    public static LessonDefinition parse(String slug, String rulesSpec, String goalKind, String quantity,
                                         String expected, String absoluteTolerance, String relativeTolerance,
                                         String quantifier) {
        if (slug == null || !slug.matches("[a-z0-9][a-z0-9_-]{0,31}"))
            throw new IllegalArgumentException("ID invalido");
        String id = slug.startsWith("teacher-") ? slug : "teacher-" + slug;
        String[] encodedRules = rulesSpec == null ? new String[0] : rulesSpec.split(",");
        if (encodedRules.length == 0 || encodedRules.length > 16) throw new IllegalArgumentException("Regras invalidas");
        List<LessonDefinition.ComponentRule> rules = new ArrayList<LessonDefinition.ComponentRule>();
        Set<String> allowed = new LinkedHashSet<String>();
        for (String encoded : encodedRules) {
            String[] parts = encoded.split(":");
            String kind = parts.length == 3 ? kind(parts[0]) : null;
            if (kind == null || !allowed.add(kind))
                throw new IllegalArgumentException("Regra de componente invalida");
            int minimum = integer(parts[1]);
            int maximum = integer(parts[2]);
            if (minimum < 0 || maximum < minimum || maximum > 1024) throw new IllegalArgumentException("Contagem invalida");
            rules.add(new LessonDefinition.ComponentRule(kind, minimum, maximum));
        }
        goalKind = kind(goalKind);
        if (!allowed.contains(goalKind)) throw new IllegalArgumentException("Componente do objetivo nao permitido");
        LessonDefinition.Quantity parsedQuantity;
        LessonDefinition.Quantifier parsedQuantifier;
        try {
            parsedQuantity = LessonDefinition.Quantity.valueOf(quantity(quantity));
            parsedQuantifier = LessonDefinition.Quantifier.valueOf(quantifier(quantifier));
        } catch (IllegalArgumentException invalidEnum) {
            throw new IllegalArgumentException("Objetivo invalido");
        }
        double target = number(expected);
        double absolute = number(absoluteTolerance);
        double relative = number(relativeTolerance);
        if (Math.abs(target) > 1000000.0 || absolute < 0.0 || absolute > 1000000.0
                || relative < 0.0 || relative > 1.0) throw new IllegalArgumentException("Tolerancia invalida");
        LessonDefinition.ElectricalGoal goal = new LessonDefinition.ElectricalGoal(goalKind, parsedQuantity,
                target, absolute, relative, true, parsedQuantifier);
        return new LessonDefinition(id, allowed, rules, Collections.singletonList(goal));
    }

    public static boolean isValidLessonId(String id) {
        return id != null && id.matches("[a-z0-9][a-z0-9_-]{0,39}");
    }

    public static boolean isKnownKind(String kind) { return KNOWN_KINDS.contains(kind); }

    public static LessonDefinition parseCompact(String slug, String rulesSpec, String goalSpec) {
        String[] goal = goalSpec.split(":");
        if (goal.length != 6) throw new IllegalArgumentException("Objetivo compacto invalido");
        return parse(slug, rulesSpec, goal[0], goal[1], goal[2], goal[3], goal[4], goal[5]);
    }

    private static String kind(String value) {
        if (KNOWN_KINDS.contains(value)) return value;
        if ("w".equals(value)) return "wire"; if ("s".equals(value)) return "source";
        if ("g".equals(value)) return "ground"; if ("k".equals(value)) return "switch";
        if ("b".equals(value)) return "breaker"; if ("r".equals(value)) return "resistor";
        if ("p".equals(value)) return "potentiometer"; if ("l".equals(value)) return "led";
        if ("d".equals(value)) return "diode"; return null;
    }

    private static String quantity(String value) {
        if ("i".equalsIgnoreCase(value)) return "CURRENT";
        if ("v".equalsIgnoreCase(value)) return "VOLTAGE";
        if ("p".equalsIgnoreCase(value)) return "POWER";
        return value.toUpperCase(Locale.ENGLISH);
    }

    private static String quantifier(String value) {
        if ("a".equalsIgnoreCase(value)) return "ANY";
        return value.toUpperCase(Locale.ENGLISH);
    }

    private static int integer(String value) {
        try { return Integer.parseInt(value); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("Inteiro invalido"); }
    }

    private static double number(String value) {
        try {
            double parsed = Double.parseDouble(value);
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) throw new IllegalArgumentException("Numero invalido");
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Numero invalido");
        }
    }
}
