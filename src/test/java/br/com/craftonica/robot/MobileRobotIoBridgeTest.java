package br.com.craftonica.robot;

import br.com.craftonica.runtime.protocol.RuntimeProtocol;
import br.com.craftonica.tile.RoboBoardState;
import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.runtime.core.AvrCheckpointCodec;
import br.com.craftonica.runtime.core.AvrMachineState;
import org.junit.Test;

import java.util.UUID;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public final class MobileRobotIoBridgeTest {
    @Test public void stoppedBoardAlwaysProducesSafeZeroEffort() {
        RoboBoardState board = new RoboBoardState(new UUID(1, 2));
        MobileRobotIoBridge.DriveCommand command = MobileRobotIoBridge.drive(board);
        assertEquals(0.0, command.left.effort, 0.0);
        assertEquals(0.0, command.right.effort, 0.0);
    }

    @Test public void mobileRuntimeKeysAreSeparatedByRobotUuid() {
        RuntimeProtocol.Identity a = RuntimeProtocol.Identity.mobile(0, new UUID(1, 2), 0);
        RuntimeProtocol.Identity b = RuntimeProtocol.Identity.mobile(0, new UUID(1, 3), 0);
        assertNotEquals(a.key(), b.key());
    }

    @Test public void gpioAndPwmOutputsFeedBothBridgeChannels() {
        RoboBoardState board = new RoboBoardState(new UUID(4, 5));
        CRLFirmware firmware = CRLFirmware.create(new byte[32], new byte[] { 0x0c, (byte) 0x94, 0, 0 });
        long revision = board.installVerifiedFirmware(firmware, 0);
        board.commitRuntimeCheckpoint(board.getGeneration(), revision,
                AvrCheckpointCodec.encode(new AvrMachineState()), RoboBoardState.Status.RUNNING,
                "", true, false,
                Arrays.asList(new RuntimeProtocol.Gpio(0, 2, true, true),
                        new RuntimeProtocol.Gpio(0, 4, true, false),
                        new RuntimeProtocol.Gpio(0, 5, true, true),
                        new RuntimeProtocol.Gpio(0, 8, true, true),
                        new RuntimeProtocol.Gpio(0, 10, true, false),
                        new RuntimeProtocol.Gpio(0, 9, true, true)),
                Arrays.asList(new RuntimeProtocol.Pwm(0, 5, 0, 3, 64, 255, false),
                        new RuntimeProtocol.Pwm(0, 9, 1, 3, 64, 128, false)), new byte[0]);
        MobileRobotIoBridge.DriveCommand command = MobileRobotIoBridge.drive(board);
        assertEquals(1.0, command.left.effort, 1.0e-12);
        assertEquals((128.0 - 32.0) / 223.0, command.right.effort, 1.0e-12);
    }
}
