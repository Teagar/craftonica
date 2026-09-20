package br.com.craftonica.robot;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import br.com.craftonica.tile.RoboBoardState;

public final class MobileRobotStateTest {
    @Test public void identityPoseOwnerAndCanonicalManifestRoundTrip() {
        UUID robotId = new UUID(1L, 2L);
        UUID ownerId = new UUID(3L, 4L);
        MobileRobotState original = new MobileRobotState(robotId, ownerId, Arrays.asList(
                new RobotModuleSnapshot(2, RobotModuleSnapshot.Type.ULTRASONIC_SENSOR, 0, 1, -1, 2),
                new RobotModuleSnapshot(0, RobotModuleSnapshot.Type.CHASSIS_CORE, 0, 0, 0, 0),
                new RobotModuleSnapshot(1, RobotModuleSnapshot.Type.ROBO_BOARD, 0, 1, 0, 0)));
        original.setPose(12.25, 64.0, -7.5, 225.0F, 5.0F);

        MobileRobotState restored = MobileRobotState.read(original.write());

        assertEquals(robotId, restored.getRobotId());
        assertEquals(ownerId, restored.getOwnerId());
        assertEquals(3, restored.getModules().size());
        assertEquals(0, restored.getModules().get(0).id);
        assertEquals(-135.0F, restored.getYaw(), 0.0F);
        assertEquals(12.25, restored.getX(), 0.0);
        assertEquals(-7.5, restored.getZ(), 0.0);
        assertArrayEquals(original.getManifestFingerprint(), restored.getManifestFingerprint());
    }

    @Test public void unknownSchemaAndTamperedManifestAreQuarantined() {
        MobileRobotState original = MobileRobotState.minimal(new UUID(1L, 2L), new UUID(3L, 4L));
        NBTTagCompound unknown = original.write();
        unknown.setInteger("Schema", 99);
        NBTTagCompound tampered = original.write();
        byte[] fingerprint = tampered.getByteArray("ManifestFingerprint");
        fingerprint[0] ^= 1;
        tampered.setByteArray("ManifestFingerprint", fingerprint);

        assertEquals(MobileRobotState.Status.QUARANTINED, MobileRobotState.read(unknown).getStatus());
        assertEquals(MobileRobotState.Status.QUARANTINED, MobileRobotState.read(tampered).getStatus());
    }

    @Test public void loadAndUnloadSuspendWithoutAdvancingFrame() {
        MobileRobotState state = MobileRobotState.minimal(new UUID(1L, 2L), new UUID(3L, 4L));
        NBTTagCompound running = state.write();
        running.setByte("Status", (byte) MobileRobotState.Status.RUNNING.ordinal());
        state = MobileRobotState.read(running);
        long frame = state.getSimulationFrame();

        assertEquals(MobileRobotState.Status.SUSPENDED, state.getStatus());
        assertEquals(1L, state.getGeneration());
        assertEquals(frame, state.getSimulationFrame());
        assertTrue(state.isResumeRequested());
        state.suspendForUnload();
        assertEquals(1L, state.getGeneration());
        state.resumeAfterLoad();
        assertEquals(MobileRobotState.Status.STOPPED, state.getStatus());
        state.suspendForUnload();
        assertEquals(2L, state.getGeneration());
        assertEquals(frame, state.getSimulationFrame());
    }

    @Test public void duplicateModulesAndInvalidPoseFailClosed() {
        try {
            new MobileRobotState(new UUID(1L, 2L), new UUID(3L, 4L), Arrays.asList(
                    new RobotModuleSnapshot(0, RobotModuleSnapshot.Type.CHASSIS_CORE, 0, 0, 0, 0),
                    new RobotModuleSnapshot(0, RobotModuleSnapshot.Type.ROBO_BOARD, 0, 1, 0, 0)));
            fail("duplicate module accepted");
        } catch (IllegalArgumentException expected) { }

        MobileRobotState state = MobileRobotState.minimal(new UUID(1L, 2L), new UUID(3L, 4L));
        try {
            state.setPose(Double.NaN, 0.0, 0.0, 0.0F, 0.0F);
            fail("invalid pose accepted");
        } catch (IllegalArgumentException expected) { }
        assertFalse(state.getStatus() == MobileRobotState.Status.QUARANTINED);
    }

    @Test public void entityAdapterRestoresPersistentStateAndPose() {
        MobileRobotState state = MobileRobotState.minimal(new UUID(11L, 12L), new UUID(13L, 14L));
        state.setPose(8.5, 70.0, -4.25, 37.0F, 0.0F);
        NBTTagCompound input = new NBTTagCompound();
        input.setTag("CraftonicaRobot", state.write());
        EntityMobileRobot entity = new EntityMobileRobot(null);

        entity.readEntityFromNBT(input);

        assertEquals(new UUID(11L, 12L), entity.getRobotState().getRobotId());
        assertEquals(8.5, entity.posX, 0.0);
        assertEquals(70.0, entity.posY, 0.0);
        assertEquals(-4.25, entity.posZ, 0.0);
        NBTTagCompound output = new NBTTagCompound();
        entity.writeEntityToNBT(output);
        assertEquals(new UUID(13L, 14L), MobileRobotState.read(
                output.getCompoundTag("CraftonicaRobot")).getOwnerId());
    }

    @Test public void embeddedBoardIdentityRoundTrips() {
        MobileRobotState original = MobileRobotState.minimal(new UUID(21L, 22L), new UUID(23L, 24L));
        RoboBoardState board = original.getBoardState();
        MobileRobotState restored = MobileRobotState.read(original.write());
        assertEquals(board.getBoardId(), restored.getBoardState().getBoardId());
        assertEquals(board.getRevision(), restored.getBoardState().getRevision());
        assertEquals(board.getStatus(), restored.getBoardState().getStatus());
    }
}
