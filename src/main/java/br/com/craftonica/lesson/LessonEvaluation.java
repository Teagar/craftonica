package br.com.craftonica.lesson;

import br.com.craftonica.network.BlockPosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LessonEvaluation {
    public enum FailureCode { PENDING, UNSOLVED, FORBIDDEN_COMPONENT, COMPONENT_COUNT, GOAL_UNAVAILABLE, GOAL_OUTSIDE_TOLERANCE }

    public static final class Failure {
        private final FailureCode code;
        private final String subject;
        private final BlockPosition position;
        private final double expected;
        private final double actual;
        private final double tolerance;

        public Failure(FailureCode code, String subject, BlockPosition position,
                       double expected, double actual, double tolerance) {
            this.code = code;
            this.subject = subject;
            this.position = position;
            this.expected = expected;
            this.actual = actual;
            this.tolerance = tolerance;
        }

        public FailureCode getCode() { return code; }
        public String getSubject() { return subject; }
        public BlockPosition getPosition() { return position; }
        public double getExpected() { return expected; }
        public double getActual() { return actual; }
        public double getTolerance() { return tolerance; }
    }

    private final List<Failure> failures;

    public LessonEvaluation(List<Failure> failures) {
        this.failures = Collections.unmodifiableList(new ArrayList<Failure>(failures));
    }

    public boolean isSuccess() { return failures.isEmpty(); }
    public List<Failure> getFailures() { return failures; }
}
