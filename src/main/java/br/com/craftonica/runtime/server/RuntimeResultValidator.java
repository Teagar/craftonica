package br.com.craftonica.runtime.server;

import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrFault;
import br.com.craftonica.runtime.core.AvrMachineState;
import br.com.craftonica.runtime.protocol.RuntimeProtocol;
import br.com.craftonica.tile.RoboBoardState;

/** Shared validation for hostile or corrupted worker output. */
public final class RuntimeResultValidator {
    private RuntimeResultValidator() { }

    public static boolean valid(RuntimeProtocol.Result result, AvrMachineState machine, byte[] previousCheckpoint) {
        long firstCycle;
        try { firstCycle = AvrCheckpointCodec.decode(previousCheckpoint).getCycles(); }
        catch (AvrFault invalid) { return false; }
        if (result == null || machine == null || result.gpio.size() + result.pwm.size() > 1024) return false;
        long lastCycle = firstCycle;
        for (RuntimeProtocol.Gpio value : result.gpio) {
            if (value.pin < 0 || value.pin >= RoboBoardState.OUTPUT_PIN_COUNT
                    || value.cycle < lastCycle || value.cycle > result.completedAtCycle) return false;
            lastCycle = value.cycle;
        }
        lastCycle = firstCycle;
        for (RuntimeProtocol.Pwm value : result.pwm) {
            int expectedTimer = timerForPwmPin(value.pin);
            if (value.pin < 0 || value.pin >= RoboBoardState.OUTPUT_PIN_COUNT
                    || value.timer != expectedTimer || value.mode < 0 || value.mode > 15
                    || value.compare < 0 || value.compare > (value.timer == 1 ? 65535 : 255)
                    || !validPrescaler(value.timer, value.prescaler)
                    || value.phaseCorrect != (value.mode == 1 || value.mode == 5)
                    || value.cycle < lastCycle || value.cycle > result.completedAtCycle) return false;
            lastCycle = value.cycle;
        }
        boolean checkpointD13 = (machine.getMmio(0x24) & 0x20) != 0 && (machine.getMmio(0x25) & 0x20) != 0;
        return result.d13High == checkpointD13;
    }

    private static boolean validPrescaler(int timer, int value) {
        if (value == 0) return true;
        if (timer == 2) return value == 1 || value == 8 || value == 32 || value == 64
                || value == 128 || value == 256 || value == 1024;
        return value == 1 || value == 8 || value == 64 || value == 256 || value == 1024;
    }

    private static int timerForPwmPin(int pin) {
        if (pin == 5 || pin == 6) return 0;
        if (pin == 9 || pin == 10) return 1;
        if (pin == 3 || pin == 11) return 2;
        return -1;
    }
}
