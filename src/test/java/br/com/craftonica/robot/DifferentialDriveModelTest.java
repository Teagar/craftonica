package br.com.craftonica.robot;

import org.junit.Test;
import static org.junit.Assert.*;

public final class DifferentialDriveModelTest {
    @Test public void equalForwardAndReverseCommandsMoveStraightWithinLimit() {
        HBridgeModel.Output forward = HBridgeModel.evaluate(true, true, true, false, 255);
        DifferentialDriveModel.Step step = DifferentialDriveModel.step(0, 0, forward, forward, 0, 0.05);
        assertEquals(0.0, step.deltaX, 1e-12); assertTrue(step.deltaZ > 0);
        assertTrue(step.leftSpeed <= DifferentialDriveModel.MAX_WHEEL_SPEED);
        HBridgeModel.Output reverse = HBridgeModel.evaluate(true, true, false, true, 255);
        step = DifferentialDriveModel.step(0, 0, reverse, reverse, 0, 0.05);
        assertTrue(step.deltaZ < 0); assertEquals(0.0, step.deltaYawDegrees, 1e-12);
    }

    @Test public void oppositeCommandsTurnInPlace() {
        HBridgeModel.Output forward = HBridgeModel.evaluate(true, true, true, false, 255);
        HBridgeModel.Output reverse = HBridgeModel.evaluate(true, true, false, true, 255);
        DifferentialDriveModel.Step step = DifferentialDriveModel.step(0, 0, reverse, forward, 0, 0.05);
        assertEquals(0.0, step.deltaZ, 1e-12); assertTrue(step.deltaYawDegrees > 0);
    }

    @Test public void invalidPowerDeadZoneAndBrakeAreSafe() {
        assertEquals("NO_POWER", HBridgeModel.evaluate(false, true, true, false, 255).diagnostic);
        assertEquals("INVALID_WIRING", HBridgeModel.evaluate(true, false, true, false, 255).diagnostic);
        assertEquals(0.0, HBridgeModel.evaluate(true, true, true, false, 31).effort, 0.0);
        assertTrue(HBridgeModel.evaluate(true, true, true, true, 255).braking);
    }
}
