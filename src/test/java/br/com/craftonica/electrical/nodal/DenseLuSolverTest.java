package br.com.craftonica.electrical.nodal;

import org.junit.Test;

import static org.junit.Assert.*;

public class DenseLuSolverTest {
    @Test public void solvesWithScaledPivotingWithoutMutatingInputs() {
        double[][] matrix = {{1e-9, 1.0}, {1.0, 1.0}};
        double[] rhs = {1.0, 2.0};
        double[][] original = {{1e-9, 1.0}, {1.0, 1.0}};
        DenseLuSolver.Result result = DenseLuSolver.solve(matrix, rhs);
        assertEquals(SolveStatus.SOLVED, result.getStatus());
        assertEquals(1.000000001, result.getSolution()[0], 1e-9);
        assertEquals(0.999999999, result.getSolution()[1], 1e-9);
        assertArrayEquals(original[0], matrix[0], 0.0);
        assertArrayEquals(original[1], matrix[1], 0.0);
    }

    @Test public void distinguishesRedundantAndConflictingConstraints() {
        assertEquals(SolveStatus.SINGULAR_MATRIX,
                DenseLuSolver.solve(new double[][]{{1, 1}, {2, 2}}, new double[]{1, 2}).getStatus());
        assertEquals(SolveStatus.CONFLICTING_CONSTRAINTS,
                DenseLuSolver.solve(new double[][]{{1, 1}, {2, 2}}, new double[]{1, 3}).getStatus());
    }

    @Test public void rejectsNonFiniteAndMalformedSystems() {
        assertEquals(SolveStatus.NON_FINITE_VALUE,
                DenseLuSolver.solve(new double[][]{{Double.NaN}}, new double[]{0}).getStatus());
        assertEquals(SolveStatus.NON_FINITE_VALUE,
                DenseLuSolver.solve(new double[][]{{1, 2}}, new double[]{1}).getStatus());
    }
}
