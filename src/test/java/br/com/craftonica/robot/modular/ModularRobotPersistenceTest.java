package br.com.craftonica.robot.modular;

import br.com.craftonica.robot.modular.drive.CoupledDriveLoop;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.physics.TerrestrialRigidBodyModel;
import br.com.craftonica.tile.RoboBoardState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.CompressedStreamTools;
import org.junit.Test;

import java.util.UUID;
import java.util.Collections;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.Assert.*;

public final class ModularRobotPersistenceTest {
    @Test public void completeEnvelopeRoundTripsIdentityMechanicsRuntimeAndSensorCounter() throws Exception {
        ModularRobotManifest manifest = manifest();
        ModularRobotState robot = new ModularRobotState(new UUID(20, 21), new UUID(22, 23),
                new GridVector(4, 70, -8), ComponentOrientation.NORTH_UP, manifest);
        CoupledDriveLoop drive = new CoupledDriveLoop(manifest, StandardComponentCatalog.create());
        TerrestrialRigidBodyModel.State body = new TerrestrialRigidBodyModel.State(
                4.5, 70.25, -7.5, 0.75, 1.25, -0.5, 0.125, -0.25);
        RoboBoardState board = new RoboBoardState(new UUID(24, 25));
        NBTTagCompound encoded = binaryRoundTrip(ModularRobotPersistence.write(robot, body, drive,
                drive.initialState(), board, 91L));

        ModularRobotPersistence.Snapshot restored = ModularRobotPersistence.read(
                encoded, StandardComponentCatalog.create());

        assertEquals(robot.getRobotId(), restored.robot.getRobotId());
        assertArrayEquals(manifest.getFingerprint(), restored.robot.getManifest().getFingerprint());
        assertEquals(Double.doubleToLongBits(body.velocityX), Double.doubleToLongBits(restored.body.velocityX));
        assertEquals(Double.doubleToLongBits(body.angularVelocityRadiansPerSecond),
                Double.doubleToLongBits(restored.body.angularVelocityRadiansPerSecond));
        assertEquals(board.getBoardId(), restored.board.getBoardId());
        assertEquals(91L, restored.sensorCounter); assertEquals(0L, restored.driveState.nextSequence);
    }

    @Test public void payloadTamperingAndFutureSchemaAreRejected() {
        NBTTagCompound encoded = envelope();
        encoded.getCompoundTag("Payload").getCompoundTag("RigidBody").setDouble("VelocityX", 99.0);
        assertRejected(encoded);
        NBTTagCompound future = envelope(); future.setInteger("Schema", 99); assertRejected(future);
    }

    @Test public void developmentSchemaMigratesOnceToCurrentEnvelope() {
        ModularRobotManifest manifest = manifest();
        ModularRobotState state = new ModularRobotState(new UUID(31, 32), new UUID(33, 34),
                new GridVector(8, 64, 9), ComponentOrientation.NORTH_UP, manifest);
        NBTTagCompound old = new NBTTagCompound(); old.setTag("CraftonicaModularRobot", state.write());
        NBTTagCompound rigid = new NBTTagCompound(); rigid.setDouble("VelocityX", 0.75);
        old.setTag("CraftonicaRigidBody", rigid); old.setLong("CraftonicaSensorCounter", 12L);
        EntityModularRobot entity = new EntityModularRobot(null); entity.setPosition(8.5, 64.0, 9.5);

        entity.readEntityFromNBT(old);
        NBTTagCompound migrated = new NBTTagCompound(); entity.writeEntityToNBT(migrated);

        assertEquals(new UUID(31, 32), entity.getRobotState().getRobotId());
        assertTrue(migrated.hasKey("CraftonicaModularRobotV2"));
        assertFalse(migrated.hasKey("CraftonicaModularRobot"));
        ModularRobotPersistence.Snapshot restored = ModularRobotPersistence.read(
                migrated.getCompoundTag("CraftonicaModularRobotV2"), StandardComponentCatalog.create());
        assertEquals(12L, restored.sensorCounter); assertEquals(0.75, restored.body.velocityX, 0.0);
    }

    @Test public void corruptEntityRemainsInRecoverableQuarantineWithoutDrops() {
        NBTTagCompound brokenEnvelope = envelope();
        brokenEnvelope.getCompoundTag("Payload").setLong("SensorCounter", -5L);
        String original = brokenEnvelope.toString(); NBTTagCompound root = new NBTTagCompound();
        root.setTag("CraftonicaModularRobotV2", brokenEnvelope);
        EntityModularRobot entity = new EntityModularRobot(null);

        entity.readEntityFromNBT(root);

        assertFalse(entity.isDead); assertEquals(ModularRobotState.Status.QUARANTINED,
                entity.getRobotState().getStatus());
        assertEquals(original, entity.getPreservedInvalidEnvelope().toString());
        NBTTagCompound saved = new NBTTagCompound(); entity.writeEntityToNBT(saved);
        assertEquals(original, saved.getCompoundTag("CraftonicaModularRobotV2").toString());
    }

    private static NBTTagCompound envelope() {
        ModularRobotManifest manifest = manifest();
        ModularRobotState state = new ModularRobotState(new UUID(1,2), new UUID(3,4),
                new GridVector(0,64,0), ComponentOrientation.NORTH_UP, manifest);
        CoupledDriveLoop drive = new CoupledDriveLoop(manifest, StandardComponentCatalog.create());
        return ModularRobotPersistence.write(state, new TerrestrialRigidBodyModel.State(
                0.5,64,0.5,0,0,0,0,0), drive, drive.initialState(), null, 0);
    }
    private static void assertRejected(NBTTagCompound value) {
        try { ModularRobotPersistence.read(value, StandardComponentCatalog.create()); fail("accepted invalid envelope"); }
        catch (IllegalArgumentException expected) { }
    }
    private static NBTTagCompound binaryRoundTrip(NBTTagCompound value) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream output = new DataOutputStream(bytes); CompressedStreamTools.write(value, output); output.close();
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        return CompressedStreamTools.read(input);
    }
    private static ModularRobotManifest manifest() {
        ModularBlockSnapshot chassis = new ModularBlockSnapshot(StandardComponentCatalog.CHASSIS, 1,
                new GridVector(0,0,0), ComponentOrientation.NORTH_UP, StandardComponentCatalog.CHASSIS, 0, null);
        return new ModularRobotManifest(new UUID(5,6), Collections.singletonList(chassis),
                Collections.<AssemblyEdge>emptyList());
    }
}
