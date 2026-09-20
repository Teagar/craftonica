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

    @Test public void autonomousSketchTimingTurnsApproximatelyNinetyDegrees() {
        HBridgeModel.Output left = HBridgeModel.evaluate(true, true, true, false, 165);
        HBridgeModel.Output right = HBridgeModel.evaluate(true, true, false, true, 165);
        double leftSpeed = 0.0, rightSpeed = 0.0, yaw = 0.0;
        for (int frame = 0; frame < 20; frame++) for (int substep = 0; substep < 2; substep++) {
            DifferentialDriveModel.Step step = DifferentialDriveModel.step(
                    leftSpeed, rightSpeed, left, right, yaw, 0.025);
            leftSpeed = step.leftSpeed; rightSpeed = step.rightSpeed; yaw += step.deltaYawDegrees;
        }
        assertEquals(-87.0, yaw, 3.0);
    }
}
