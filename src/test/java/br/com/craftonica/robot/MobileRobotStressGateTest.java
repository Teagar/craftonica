package br.com.craftonica.robot;

import br.com.craftonica.firmware.CRLFirmware;
import br.com.craftonica.tile.RoboBoardState;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public final class MobileRobotStressGateTest {
    @Test public void thirtyTwoRobotsRemainDeterministicForTwentyThousandSubsteps() {
        Pose[] first = runFleet(), second = runFleet();
        assertEquals(first.length, second.length);
        for (int i = 0; i < first.length; i++) {
            assertEquals(first[i].x, second[i].x, 0.0);
            assertEquals(first[i].z, second[i].z, 0.0);
            assertEquals(first[i].yaw, second[i].yaw, 0.0);
            assertTrue(finite(first[i].x) && finite(first[i].z) && finite(first[i].yaw));
            assertTrue(StrictMath.abs(first[i].left) <= DifferentialDriveModel.MAX_WHEEL_SPEED);
            assertTrue(StrictMath.abs(first[i].right) <= DifferentialDriveModel.MAX_WHEEL_SPEED);
        }
    }

    @Test public void repeatedUnloadReloadKeepsOneIdentityAndMonotonicGenerations() {
        UUID robotId = new UUID(101L, 102L), owner = new UUID(103L, 104L);
        MobileRobotState state = MobileRobotState.minimal(robotId, owner);
        RoboBoardState board = state.getBoardState();
        board.installVerifiedFirmware(CRLFirmware.create(new byte[32],
                new byte[] { 0x0c, (byte) 0x94, 0, 0 }), board.getRevision());
        board.start(board.getRevision());
        long lastRobotGeneration = state.getGeneration(), lastBoardGeneration = board.getGeneration();
        for (int cycle = 0; cycle < 128; cycle++) {
            state.suspendForUnload();
            NBTTagCompound persisted = state.write();
            state = MobileRobotState.read(persisted);
            assertEquals(robotId, state.getRobotId());
            assertEquals(owner, state.getOwnerId());
            assertEquals(MobileRobotState.Status.SUSPENDED, state.getStatus());
            assertTrue(state.getGeneration() > lastRobotGeneration);
            assertTrue(state.getBoardState().getGeneration() > lastBoardGeneration);
            lastRobotGeneration = state.getGeneration();
            lastBoardGeneration = state.getBoardState().getGeneration();
            state.resumeAfterLoad();
            assertTrue(state.getBoardState().isRunning());
        }
    }

    @Test public void repeatedMalformedNbtAlwaysQuarantinesWithinBounds() {
        MobileRobotState valid = MobileRobotState.minimal(new UUID(201L, 202L), new UUID(203L, 204L));
        for (int index = 0; index < 256; index++) {
            NBTTagCompound tag = valid.write();
            if ((index & 1) == 0) tag.setInteger("Schema", index + 100);
            else tag.setByteArray("ManifestFingerprint", new byte[] { (byte) index });
            MobileRobotState restored = MobileRobotState.read(tag);
            assertEquals("malformed case " + index,
                    MobileRobotState.Status.QUARANTINED, restored.getStatus());
            assertFalse(restored.getBoardState().isRunning());
        }
    }

    private static Pose[] runFleet() {
        Pose[] fleet = new Pose[32];
        for (int i = 0; i < fleet.length; i++) fleet[i] = new Pose(i * 3.0, i * -2.0, i * 11.25);
        HBridgeModel.Output forward = HBridgeModel.evaluate(true, true, true, false, 180);
        HBridgeModel.Output reverse = HBridgeModel.evaluate(true, true, false, true, 180);
        for (int frame = 0; frame < 10000; frame++) for (int i = 0; i < fleet.length; i++) {
            Pose pose = fleet[i]; boolean turn = ((frame / 250) + i) % 5 == 0;
            HBridgeModel.Output left = turn ? reverse : forward;
            DifferentialDriveModel.Step step = DifferentialDriveModel.step(
                    pose.left, pose.right, left, forward, pose.yaw, 0.025);
            pose.left = step.leftSpeed; pose.right = step.rightSpeed;
            pose.x += step.deltaX; pose.z += step.deltaZ; pose.yaw += step.deltaYawDegrees;
        }
        return fleet;
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    private static final class Pose {
        double x, z, yaw, left, right;
        Pose(double x, double z, double yaw) { this.x = x; this.z = z; this.yaw = yaw; }
    }
}
