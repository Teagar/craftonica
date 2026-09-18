package br.com.craftonica.tile;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class TileEntityRoboBoardBudgetTest {
    @Test
    public void advancesByOneTickBatchWithoutPassingTheAbsoluteTickBudget() {
        assertEquals(800000L, TileEntityRoboBoard.nextAbsoluteTarget(0L, 0L));
        assertEquals(800000L, TileEntityRoboBoard.nextAbsoluteTarget(790000L, 0L));
        assertEquals(1600000L, TileEntityRoboBoard.nextAbsoluteTarget(800000L, 1L));
    }

    @Test
    public void saturatesLogicalArithmeticInsteadOfWrapping() {
        assertEquals(Long.MAX_VALUE, TileEntityRoboBoard.nextAbsoluteTarget(Long.MAX_VALUE - 10L, Long.MAX_VALUE));
    }
}
