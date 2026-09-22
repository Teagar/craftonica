package br.com.craftonica.robot.modular.servo;

import org.junit.Test;

import static org.junit.Assert.*;

public final class ServoModelTest {
    private final ServoParameters parameters = ServoParameters.educational();

    @Test public void targetStepMovesByTorqueAndLoadSlowsItWithoutTeleporting() {
        ServoState unloaded = ServoState.ambient(parameters), loaded = unloaded;
        ServoInput command = input(2000, 0.0, ServoInput.SignalLossPolicy.HOLD);
        ServoInput resisted = input(2000, 0.12, ServoInput.SignalLossPolicy.HOLD);
        ServoStep first = ServoModel.step(parameters, unloaded, command, 0.025);
        assertTrue(first.state.positionRadians > unloaded.positionRadians);
        assertTrue(first.state.positionRadians < parameters.maximumAngleRadians);
        assertTrue(first.currentAmps > 0.0); assertTrue(first.appliedTorqueNm > 0.0);
        unloaded = first.state;
        loaded = ServoModel.step(parameters, loaded, resisted, 0.025).state;
        for (int i = 0; i < 19; i++) {
            unloaded = ServoModel.step(parameters, unloaded, command, 0.025).state;
            loaded = ServoModel.step(parameters, loaded, resisted, 0.025).state;
        }
        assertTrue(unloaded.positionRadians > loaded.positionRadians);
    }

    @Test public void noPowerAlwaysRemovesTorqueAndSignalPoliciesAreExplicit() {
        ServoState acquired = ServoModel.step(parameters, ServoState.ambient(parameters),
                input(2000, 0.0, ServoInput.SignalLossPolicy.HOLD), 0.025).state;
        ServoInput unpowered = new ServoInput(true, false, 0.0, null,
                ServoInput.SignalLossPolicy.HOLD, 0.0);
        ServoStep off = ServoModel.step(parameters, acquired, unpowered, 0.025);
        assertEquals(0.0, off.appliedTorqueNm, 0.0); assertEquals(0.0, off.currentAmps, 0.0);
        assertEquals(ServoStep.Diagnostic.UNPOWERED, off.diagnostic);

        ServoState hold = acquired, coast = acquired, safe = acquired;
        for (int i = 0; i < 6; i++) {
            hold = ServoModel.step(parameters, hold, input(null, 0.0,
                    ServoInput.SignalLossPolicy.HOLD), 0.025).state;
            coast = ServoModel.step(parameters, coast, input(null, 0.0,
                    ServoInput.SignalLossPolicy.COAST), 0.025).state;
            safe = ServoModel.step(parameters, safe, input(null, 0.0,
                    ServoInput.SignalLossPolicy.SAFE_POSITION), 0.025).state;
        }
        assertEquals(parameters.maximumAngleRadians, hold.targetRadians, 1.0e-12);
        assertEquals(parameters.safeAngleRadians, safe.targetRadians, 1.0e-12);
        assertTrue(StrictMath.abs(coast.velocityRadPerSecond) < StrictMath.abs(hold.velocityRadPerSecond));
    }

    @Test public void sustainedStallDrawsCurrentHeatsAndTripsProtection() {
        ServoState state = ServoState.ambient(parameters); ServoStep step = null;
        ServoInput stalled = input(2000, 0.22, ServoInput.SignalLossPolicy.HOLD);
        for (int i = 0; i < 16000 && !state.thermalShutdown; i++) {
            step = ServoModel.step(parameters, state, stalled, 0.025); state = step.state;
        }
        assertNotNull(step); assertTrue(state.thermalShutdown);
        assertEquals(ServoStep.Diagnostic.OVER_TEMPERATURE, step.diagnostic);
        ServoStep protectedStep = ServoModel.step(parameters, state, stalled, 0.025);
        assertEquals(0.0, protectedStep.appliedTorqueNm, 0.0);
        assertEquals(0.0, protectedStep.currentAmps, 0.0);
    }

    @Test public void invalidPulseIsRejectedWithoutReplacingLastTarget() {
        ServoState acquired = ServoModel.step(parameters, ServoState.ambient(parameters),
                input(1800, 0.0, ServoInput.SignalLossPolicy.HOLD), 0.025).state;
        ServoStep invalid = ServoModel.step(parameters, acquired,
                input(2500, 0.0, ServoInput.SignalLossPolicy.HOLD), 0.025);
        assertEquals(ServoStep.Diagnostic.INVALID_PULSE, invalid.diagnostic);
        assertEquals(acquired.targetRadians, invalid.state.targetRadians, 0.0);
    }

    private static ServoInput input(Integer pulse, double load, ServoInput.SignalLossPolicy policy) {
        return new ServoInput(true, true, 5.0, pulse, policy, load);
    }
}
