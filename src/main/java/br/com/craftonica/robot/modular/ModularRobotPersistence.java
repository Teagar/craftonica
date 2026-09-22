package br.com.craftonica.robot.modular;

import br.com.craftonica.persistence.NbtMigrations;
import br.com.craftonica.robot.modular.drive.CoupledDriveLoop;
import br.com.craftonica.robot.modular.physics.TerrestrialRigidBodyModel;
import br.com.craftonica.robot.modular.assembly.KinematicAssembly;
import br.com.craftonica.robot.modular.assembly.KinematicAssemblyAnalyzer;
import br.com.craftonica.robot.modular.joint.JointStateSet;
import br.com.craftonica.robot.modular.servo.ServoJointLoop;
import br.com.craftonica.tile.RoboBoardState;
import br.com.craftonica.tile.RoboBoardStateNbtCodec;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.CompressedStreamTools;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Checksummed, bounded persistence envelope for the complete modular simulation state. */
public final class ModularRobotPersistence {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_PAYLOAD_BYTES = 1024 * 1024;
    private static final int HASH_BYTES = 32;

    private ModularRobotPersistence() { }

    public static NBTTagCompound write(ModularRobotState robot, TerrestrialRigidBodyModel.State body,
            CoupledDriveLoop drive, CoupledDriveLoop.State driveState, RoboBoardState board,
            long sensorCounter) {
        return write(robot, body, drive, driveState, board, sensorCounter, JointStateSet.EMPTY);
    }

    public static NBTTagCompound write(ModularRobotState robot, TerrestrialRigidBodyModel.State body,
            CoupledDriveLoop drive, CoupledDriveLoop.State driveState, RoboBoardState board,
            long sensorCounter, JointStateSet joints) {
        return write(robot, body, drive, driveState, board, sensorCounter, joints, null, null);
    }

    public static NBTTagCompound write(ModularRobotState robot, TerrestrialRigidBodyModel.State body,
            CoupledDriveLoop drive, CoupledDriveLoop.State driveState, RoboBoardState board,
            long sensorCounter, JointStateSet joints, ServoJointLoop servoLoop, ServoJointLoop.State servoState) {
        if (robot == null || body == null || drive == null || driveState == null || sensorCounter < 0 || joints == null)
            throw new IllegalArgumentException("modular persistence state");
        NBTTagCompound payload = new NBTTagCompound(); payload.setTag("Robot", robot.write());
        NBTTagCompound rigid = new NBTTagCompound();
        rigid.setDouble("X", body.x); rigid.setDouble("Y", body.y); rigid.setDouble("Z", body.z);
        rigid.setDouble("Yaw", body.yawRadians); rigid.setDouble("VelocityX", body.velocityX);
        rigid.setDouble("VelocityY", body.velocityY); rigid.setDouble("VelocityZ", body.velocityZ);
        rigid.setDouble("AngularVelocity", body.angularVelocityRadiansPerSecond);
        payload.setTag("RigidBody", rigid); payload.setTag("Drive", drive.writeState(driveState));
        payload.setBoolean("HasBoard", board != null);
        if (board != null) {
            NBTTagCompound boardTag = new NBTTagCompound(); RoboBoardStateNbtCodec.write(board, boardTag);
            payload.setTag("Board", boardTag);
        }
        payload.setLong("SensorCounter", sensorCounter);
        payload.setTag("Joints", joints.write());
        if (servoLoop != null && servoState != null) payload.setTag("ServoJoints", servoLoop.writeState(servoState));
        byte[] hash = hash(payload);
        NBTTagCompound envelope = new NBTTagCompound(); envelope.setInteger("Schema", SCHEMA_VERSION);
        envelope.setString("Kind", "modular_robot"); envelope.setTag("Payload", payload);
        envelope.setByteArray("Checksum", hash); return envelope;
    }

    public static Snapshot read(NBTTagCompound envelope, ComponentCatalog catalog) {
        if (envelope == null || catalog == null || envelope.getInteger("Schema") != SCHEMA_VERSION
                || !"modular_robot".equals(envelope.getString("Kind"))
                || !envelope.hasKey("Payload") || !envelope.hasKey("Checksum"))
            throw new IllegalArgumentException("modular envelope schema");
        NBTTagCompound payload = envelope.getCompoundTag("Payload"); byte[] expected = envelope.getByteArray("Checksum");
        if (expected.length != HASH_BYTES || !MessageDigest.isEqual(expected, hash(payload)))
            throw new IllegalArgumentException("modular envelope checksum");
        if (!payload.hasKey("Robot") || !payload.hasKey("RigidBody") || !payload.hasKey("Drive")
                || !payload.hasKey("HasBoard") || !payload.hasKey("SensorCounter"))
            throw new IllegalArgumentException("modular payload fields");
        ModularRobotState robot = ModularRobotState.read(payload.getCompoundTag("Robot"));
        CoupledDriveLoop drive = new CoupledDriveLoop(robot.getManifest(), catalog);
        CoupledDriveLoop.State driveState = drive.readState(payload.getCompoundTag("Drive"));
        NBTTagCompound rigid = payload.getCompoundTag("RigidBody");
        if (!rigid.hasKey("X") || !rigid.hasKey("Y") || !rigid.hasKey("Z") || !rigid.hasKey("Yaw")
                || !rigid.hasKey("VelocityX") || !rigid.hasKey("VelocityY") || !rigid.hasKey("VelocityZ")
                || !rigid.hasKey("AngularVelocity")) throw new IllegalArgumentException("rigid body fields");
        TerrestrialRigidBodyModel.State body = new TerrestrialRigidBodyModel.State(
                rigid.getDouble("X"), rigid.getDouble("Y"), rigid.getDouble("Z"), rigid.getDouble("Yaw"),
                rigid.getDouble("VelocityX"), rigid.getDouble("VelocityY"), rigid.getDouble("VelocityZ"),
                rigid.getDouble("AngularVelocity"));
        validateBody(body);
        RoboBoardState board = null;
        if (payload.getBoolean("HasBoard")) {
            if (!payload.hasKey("Board")) throw new IllegalArgumentException("missing board state");
            board = RoboBoardStateNbtCodec.read(payload.getCompoundTag("Board"));
            String fault = board.getFault();
            if ("UNKNOWN_SCHEMA".equals(fault) || "INVALID_IDENTITY".equals(fault)
                    || "INVALID_PERSISTED_STATE".equals(fault))
                throw new IllegalArgumentException("modular board state");
        }
        long sensorCounter = payload.getLong("SensorCounter");
        if (sensorCounter < 0) throw new IllegalArgumentException("sensor counter");
        KinematicAssembly kinematic = KinematicAssemblyAnalyzer.analyze(robot.getManifest(), catalog);
        if (!kinematic.isValid()) throw new IllegalArgumentException("invalid kinematic assembly");
        JointStateSet joints;
        if (payload.hasKey("Joints")) joints = JointStateSet.read(payload.getCompoundTag("Joints"), kinematic);
        else if (kinematic.getJoints().isEmpty()) joints = JointStateSet.EMPTY;
        else throw new IllegalArgumentException("missing joint state");
        ServoJointLoop servoLoop = new ServoJointLoop(robot.getManifest(), catalog, kinematic);
        ServoJointLoop.State servoState = payload.hasKey("ServoJoints")
                ? servoLoop.readState(payload.getCompoundTag("ServoJoints")) : servoLoop.initialState();
        return new Snapshot(robot, body, drive, driveState, board, sensorCounter, joints, servoLoop, servoState);
    }

    public static NBTTagCompound preserve(NBTTagCompound source) {
        NBTTagCompound copy = NbtMigrations.copy(source);
        if (encoded(copy).length > MAX_PAYLOAD_BYTES) {
            NBTTagCompound bounded = new NBTTagCompound(); bounded.setInteger("Schema", -1);
            bounded.setString("Failure", "PERSISTED_STATE_TOO_LARGE"); return bounded;
        }
        return copy;
    }

    private static void validateBody(TerrestrialRigidBodyModel.State value) {
        if (StrictMath.abs(value.x) > 30000000.0 || StrictMath.abs(value.z) > 30000000.0
                || value.y < -4096.0 || value.y > 4096.0 || StrictMath.abs(value.yawRadians) > 1000000.0
                || StrictMath.abs(value.velocityX) > 100.0 || StrictMath.abs(value.velocityY) > 100.0
                || StrictMath.abs(value.velocityZ) > 100.0
                || StrictMath.abs(value.angularVelocityRadiansPerSecond) > 100.0)
            throw new IllegalArgumentException("rigid body state");
    }

    private static byte[] hash(NBTTagCompound payload) {
        byte[] encoded = encoded(payload);
        if (encoded.length > MAX_PAYLOAD_BYTES) throw new IllegalArgumentException("modular payload too large");
        try { return MessageDigest.getInstance("SHA-256").digest(encoded); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static byte[] encoded(NBTTagCompound payload) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            CompressedStreamTools.write(payload, output); output.close(); return bytes.toByteArray();
        } catch (IOException invalid) {
            throw new IllegalArgumentException("modular payload cannot be encoded", invalid);
        }
    }

    public static final class Snapshot {
        public final ModularRobotState robot;
        public final TerrestrialRigidBodyModel.State body;
        public final CoupledDriveLoop drive;
        public final CoupledDriveLoop.State driveState;
        public final RoboBoardState board;
        public final long sensorCounter;
        public final JointStateSet joints;
        public final ServoJointLoop servoLoop;
        public final ServoJointLoop.State servoState;

        Snapshot(ModularRobotState robot, TerrestrialRigidBodyModel.State body, CoupledDriveLoop drive,
                CoupledDriveLoop.State driveState, RoboBoardState board, long sensorCounter, JointStateSet joints,
                ServoJointLoop servoLoop, ServoJointLoop.State servoState) {
            this.robot = robot; this.body = body; this.drive = drive; this.driveState = driveState;
            this.board = board; this.sensorCounter = sensorCounter; this.joints = joints;
            this.servoLoop = servoLoop; this.servoState = servoState;
        }
    }
}
