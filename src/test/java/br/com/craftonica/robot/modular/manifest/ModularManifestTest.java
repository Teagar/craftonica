package br.com.craftonica.robot.modular.manifest;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.ModularRobotState;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.*;

public final class ModularManifestTest {
    @Test public void asymmetricManifestRoundTripsWithIdentityGeometryAndTileState() {
        UUID id = new UUID(7, 11); NBTTagCompound board = new NBTTagCompound();
        board.setString("Owner", "student"); board.setInteger("Revision", 42);
        NBTTagCompound stack = new NBTTagCompound(); stack.setString("id", "craftonica:wrench"); stack.setByte("Count", (byte) 1);
        NBTTagList inventory = new NBTTagList(); inventory.appendTag(stack); board.setTag("Items", inventory);
        ModularRobotManifest original = new ModularRobotManifest(id, Arrays.asList(
                module("craftonica:robot_chassis", p(0, 0, 0), ComponentOrientation.NORTH_UP, null),
                module("craftonica:robo_board", p(0, 1, 0), new ComponentOrientation(Direction.EAST, Direction.UP), board),
                module("craftonica:ultrasonic_sensor", p(1, 1, -2), new ComponentOrientation(Direction.WEST, Direction.UP), null)),
                Arrays.asList(new AssemblyEdge(AssemblyEdge.Kind.STRUCTURAL, p(0, 0, 0), "mount_up",
                        p(0, 1, 0), "mount_down")));
        ModularRobotManifest restored = ModularManifestNbtCodec.read(ModularManifestNbtCodec.write(original));
        assertEquals(id, restored.getManifestId());
        assertArrayEquals(original.getFingerprint(), restored.getFingerprint());
        assertEquals(3, restored.getModules().size()); assertEquals(1, restored.getEdges().size());
        ModularBlockSnapshot restoredBoard = restored.getModules().get(1);
        assertEquals(42, restoredBoard.getTileData().getInteger("Revision"));
        assertEquals("craftonica:wrench", restoredBoard.getTileData().getTagList("Items", 10)
                .getCompoundTagAt(0).getString("id"));
        NBTTagCompound escaped = restoredBoard.getTileData(); escaped.setInteger("Revision", 99);
        assertEquals(42, restoredBoard.getTileData().getInteger("Revision"));
    }

    @Test public void checksumRejectsTampering() {
        ModularRobotManifest original = manifest(); NBTTagCompound encoded = ModularManifestNbtCodec.write(original);
        encoded.getTagList("Modules", 10).getCompoundTagAt(0).setInteger("X", 9);
        try { ModularManifestNbtCodec.read(encoded); fail("tampered manifest"); }
        catch (IllegalArgumentException expected) { }
    }

    @Test public void moduleOrderDoesNotChangeCanonicalFingerprint() {
        UUID id = new UUID(1, 2); ModularBlockSnapshot a = module("craftonica:robot_chassis", p(0, 0, 0), ComponentOrientation.NORTH_UP, null);
        ModularBlockSnapshot b = module("craftonica:robo_board", p(1, 2, 0), ComponentOrientation.NORTH_UP, null);
        assertArrayEquals(new ModularRobotManifest(id, Arrays.asList(a, b), Collections.<AssemblyEdge>emptyList()).getFingerprint(),
                new ModularRobotManifest(id, Arrays.asList(b, a), Collections.<AssemblyEdge>emptyList()).getFingerprint());
    }

    @Test public void persistentRobotStateKeepsUuidManifestAndAnchorTransform() {
        ModularRobotManifest manifest = manifest(); ModularRobotState state = new ModularRobotState(
                new UUID(90, 91), new UUID(92, 93), p(12, 7, -4),
                new ComponentOrientation(Direction.EAST, Direction.UP), manifest);
        ModularRobotState restored = ModularRobotState.read(state.write());
        assertEquals(state.getRobotId(), restored.getRobotId());
        assertEquals(state.getOwnerId(), restored.getOwnerId());
        assertEquals(state.getAnchor(), restored.getAnchor());
        assertEquals(Direction.EAST, restored.getAnchorOrientation().getForward());
        assertArrayEquals(manifest.getFingerprint(), restored.getManifest().getFingerprint());
        assertTrue(restored.canSimulate());

        NBTTagCompound legacy = state.write(); legacy.setByte("Status", (byte) 0);
        assertFalse(ModularRobotState.read(legacy).canSimulate());
    }

    public static ModularRobotManifest manifest() {
        return new ModularRobotManifest(new UUID(3, 4), Arrays.asList(
                module("craftonica:robot_chassis", p(0, 0, 0), ComponentOrientation.NORTH_UP, null),
                module("craftonica:robo_board", p(1, 0, 0), ComponentOrientation.NORTH_UP, null),
                module("craftonica:dc_motor", p(1, 1, -1), ComponentOrientation.NORTH_UP, null)),
                Collections.<AssemblyEdge>emptyList());
    }

    static ModularBlockSnapshot module(String type, GridVector p, ComponentOrientation orientation, NBTTagCompound tile) {
        return new ModularBlockSnapshot(type, 1, p, orientation, type, 3, tile);
    }
    static GridVector p(int x, int y, int z) { return new GridVector(x, y, z); }
}
