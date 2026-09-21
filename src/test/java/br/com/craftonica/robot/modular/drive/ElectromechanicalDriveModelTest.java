package br.com.craftonica.robot.modular.drive;

import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluation;
import org.junit.Test;

import static org.junit.Assert.*;

public final class ElectromechanicalDriveModelTest {
    private final DcMotorParameters motor = DcMotorParameters.educational();
    private final HBridgeParameters bridge = HBridgeParameters.educational();

    @Test public void stallCurrentAndTorqueFollowPublishedEquationsAndLimits() {
        DriveStep step = step(DriveState.ambient(motor, bridge),
                new DriveInput(true, 5.0, 255, DriveInput.Mode.FORWARD, 0.0));
        assertEquals(4.2, step.terminalVoltageVolts, 1.0e-12);
        assertEquals(1.0, step.currentAmps, 1.0e-12);
        assertEquals(0.04, step.electromagneticTorqueNm, 1.0e-12);
        assertEquals(5.0, step.sourcePowerWatts, 1.0e-12);
        assertEquals(4.0, step.motorCopperLossWatts, 1.0e-12);
        assertEquals(1.0, step.bridgeLossWatts, 1.0e-12);
        assertEquals(DriveStep.Diagnostic.NONE, step.diagnostic);
    }

    @Test public void noLoadAndIntermediateLoadReachDistinctFiniteSpeeds() {
        DriveState noLoad = run(0.0, DriveInput.Mode.FORWARD, 6000);
        DriveState loaded = run(0.01, DriveInput.Mode.FORWARD, 6000);
        assertEquals(83.17, noLoad.angularVelocityRadPerSecond, 0.2);
        assertTrue(loaded.angularVelocityRadPerSecond > 0.0);
        assertTrue(loaded.angularVelocityRadPerSecond < noLoad.angularVelocityRadPerSecond);
        assertTrue(noLoad.angularVelocityRadPerSecond * 60.0 / (2.0 * StrictMath.PI) > 700.0);
    }

    @Test public void reverseBrakeAndCoastArePhysicallyDistinct() {
        DriveState spinning = new DriveState(100.0, 25.0, 25.0, false);
        DriveStep reverse = step(spinning, new DriveInput(true, 5.0, 255, DriveInput.Mode.REVERSE, 0.0));
        DriveStep brake = step(spinning, new DriveInput(true, 5.0, 255, DriveInput.Mode.BRAKE, 0.0));
        DriveStep coast = step(spinning, new DriveInput(true, 5.0, 255, DriveInput.Mode.COAST, 0.0));
        assertEquals(-1.0, reverse.currentAmps, 0.0);
        assertTrue(brake.currentAmps < 0.0); assertEquals(0.0, brake.terminalVoltageVolts, 0.0);
        assertEquals(0.0, coast.currentAmps, 0.0);
        assertTrue(reverse.state.angularVelocityRadPerSecond < brake.state.angularVelocityRadPerSecond);
        assertTrue(brake.state.angularVelocityRadPerSecond < coast.state.angularVelocityRadPerSecond);
    }

    @Test public void pwmChangesAverageTerminalVoltageWithoutNormalizedEffortShortcut() {
        DriveStep quarter = step(DriveState.ambient(motor, bridge),
                new DriveInput(true, 5.0, 64, DriveInput.Mode.FORWARD, 0.0));
        DriveStep full = step(DriveState.ambient(motor, bridge),
                new DriveInput(true, 5.0, 255, DriveInput.Mode.FORWARD, 0.0));
        assertEquals(4.2 * 64.0 / 255.0, quarter.terminalVoltageVolts, 1.0e-12);
        assertTrue(quarter.currentAmps < full.currentAmps);
    }

    @Test public void physicalPwmAndDirectionSignalsSelectCoastForwardReverseAndBrake() {
        assertEquals(DriveInput.Mode.COAST,
                DriveInput.fromControlSignals(true, 5.0, 0, true, false, 0.0).mode);
        assertEquals(DriveInput.Mode.FORWARD,
                DriveInput.fromControlSignals(true, 5.0, 128, true, false, 0.0).mode);
        assertEquals(DriveInput.Mode.REVERSE,
                DriveInput.fromControlSignals(true, 5.0, 128, false, false, 0.0).mode);
        assertEquals(DriveInput.Mode.BRAKE,
                DriveInput.fromControlSignals(true, 5.0, 128, false, true, 0.0).mode);
    }

    @Test public void invalidPowerWiringAndTemperatureFailSafeToCoast() {
        DriveState moving = new DriveState(20.0, 25.0, 25.0, false);
        assertSafe(moving, new DriveInput(false, 5.0, 255, DriveInput.Mode.FORWARD, 0.0),
                DriveStep.Diagnostic.OPEN_CIRCUIT);
        assertSafe(moving, new DriveInput(true, 13.0, 255, DriveInput.Mode.FORWARD, 0.0),
                DriveStep.Diagnostic.INVALID_SUPPLY);
        assertSafe(moving, new DriveInput(true, 0.0, 255, DriveInput.Mode.FORWARD, 0.0),
                DriveStep.Diagnostic.INVALID_SUPPLY);
        assertSafe(new DriveState(20.0, 121.0, 25.0, false),
                new DriveInput(true, 5.0, 255, DriveInput.Mode.FORWARD, 0.0),
                DriveStep.Diagnostic.OVER_TEMPERATURE);
    }

    @Test public void heatingIsDeterministicAndCanTripConfiguredThermalLimit() {
        DcMotorParameters sensitive = new DcMotorParameters(4.0, 0.04, 0.04, 0.0002, 0.0001,
                1.0, 300.0, 25.0, 25.01, 0.1, 8.0);
        DriveState state = DriveState.ambient(sensitive, bridge); DriveStep result = null;
        for (int i = 0; i < 20 && !state.thermalShutdown; i++) {
            result = ElectromechanicalDriveModel.step(sensitive, bridge, state,
                    new DriveInput(true, 5.0, 255, DriveInput.Mode.FORWARD, 1.0), 0.001);
            state = result.state;
        }
        assertNotNull(result); assertTrue(state.thermalShutdown);
        assertEquals(DriveStep.Diagnostic.OVER_TEMPERATURE, result.diagnostic);
        DriveStep latched = ElectromechanicalDriveModel.step(sensitive, bridge, state,
                new DriveInput(true, 5.0, 255, DriveInput.Mode.FORWARD, 0.0), 0.001);
        assertEquals(0.0, latched.currentAmps, 0.0);
    }

    @Test public void identicalTraceProducesBitIdenticalState() {
        DriveState a = DriveState.ambient(motor, bridge), b = DriveState.ambient(motor, bridge);
        for (int i = 0; i < 1000; i++) {
            DriveInput input = new DriveInput(true, 7.4, i % 256,
                    (i & 1) == 0 ? DriveInput.Mode.FORWARD : DriveInput.Mode.BRAKE, 0.003);
            a = ElectromechanicalDriveModel.step(motor, bridge, a, input, 0.001).state;
            b = ElectromechanicalDriveModel.step(motor, bridge, b, input, 0.001).state;
        }
        assertEquals(Double.doubleToLongBits(a.angularVelocityRadPerSecond),
                Double.doubleToLongBits(b.angularVelocityRadPerSecond));
        assertEquals(Double.doubleToLongBits(a.motorTemperatureCelsius),
                Double.doubleToLongBits(b.motorTemperatureCelsius));
    }

    @Test public void disconnectedPhysicalBindingCannotProduceTorque() {
        MobileElectricalEvaluation.DriveBinding disconnected =
                new MobileElectricalEvaluation.DriveBinding(false, null, null, null);
        DriveStep result = MobileDriveSimulation.step(disconnected, motor, bridge,
                DriveState.ambient(motor, bridge),
                new DriveInput(true, 5.0, 255, DriveInput.Mode.FORWARD, 0.0), 0.001);
        assertEquals(DriveStep.Diagnostic.OPEN_CIRCUIT, result.diagnostic);
        assertEquals(0.0, result.currentAmps, 0.0); assertEquals(0.0, result.electromagneticTorqueNm, 0.0);
        MobileElectricalEvaluation.DriveBinding connected =
                new MobileElectricalEvaluation.DriveBinding(true, "D5", "D8", new GridVector(1, 0, 0));
        assertTrue(MobileDriveSimulation.step(connected, motor, bridge, DriveState.ambient(motor, bridge),
                new DriveInput(true, 5.0, 255, DriveInput.Mode.FORWARD, 0.0), 0.001).currentAmps > 0.0);
    }

    private DriveState run(double load, DriveInput.Mode mode, int count) {
        DriveState state = DriveState.ambient(motor, bridge);
        for (int i = 0; i < count; i++) state = step(state,
                new DriveInput(true, 5.0, 255, mode, load)).state;
        return state;
    }
    private DriveStep step(DriveState state, DriveInput input) {
        return ElectromechanicalDriveModel.step(motor, bridge, state, input, 0.001);
    }
    private void assertSafe(DriveState state, DriveInput input, DriveStep.Diagnostic diagnostic) {
        DriveStep result = step(state, input);
        assertEquals(diagnostic, result.diagnostic); assertEquals(DriveInput.Mode.COAST, result.appliedMode);
        assertEquals(0.0, result.currentAmps, 0.0); assertEquals(0.0, result.terminalVoltageVolts, 0.0);
    }
}
