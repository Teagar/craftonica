package br.com.craftonica.robot.modular.transaction;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.manifest.ModularManifestTest;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public final class AssemblyTransactionTest {
    private static final UUID ROBOT = new UUID(10, 20), OWNER = new UUID(30, 40);
    private static final GridVector ANCHOR = new GridVector(100, 8, -40);

    @Test public void assemblyCommitsAllBlocksAndOneEntity() {
        ModularRobotManifest manifest = ModularManifestTest.manifest(); FakeWorld world = assembledWorld(manifest);
        AssemblyTransactionResult result = AssemblyTransaction.assemble(world, ROBOT, OWNER, ANCHOR,
                ComponentOrientation.NORTH_UP, manifest);
        assertTrue(result.committed()); assertTrue(world.blocks.isEmpty()); assertTrue(world.robotExists(ROBOT));
    }

    @Test public void failureAtEveryRemovalRestoresExactWorld() {
        ModularRobotManifest manifest = ModularManifestTest.manifest();
        for (int failure = 1; failure <= manifest.getModules().size(); failure++) {
            FakeWorld world = assembledWorld(manifest); Map<GridVector, ModularBlockSnapshot> before = copy(world.blocks);
            world.failRemoveAt = failure;
            AssemblyTransactionResult result = AssemblyTransaction.assemble(world, ROBOT, OWNER, ANCHOR,
                    ComponentOrientation.NORTH_UP, manifest);
            assertEquals(AssemblyTransactionResult.Status.MUTATION_FAILED, result.status);
            assertWorldEquals(before, world.blocks); assertFalse(world.robotExists(ROBOT));
        }
    }

    @Test public void spawnFailureRestoresExactWorld() {
        ModularRobotManifest manifest = ModularManifestTest.manifest(); FakeWorld world = assembledWorld(manifest);
        Map<GridVector, ModularBlockSnapshot> before = copy(world.blocks); world.failSpawn = true;
        assertEquals(AssemblyTransactionResult.Status.MUTATION_FAILED,
                AssemblyTransaction.assemble(world, ROBOT, OWNER, ANCHOR, ComponentOrientation.NORTH_UP, manifest).status);
        assertWorldEquals(before, world.blocks); assertFalse(world.robotExists(ROBOT));
    }

    @Test public void failureAtEveryPlacementRemovesPartialBlocksAndKeepsEntity() {
        ModularRobotManifest manifest = ModularManifestTest.manifest();
        for (int failure = 1; failure <= manifest.getModules().size(); failure++) {
            FakeWorld world = mobileWorld(); world.robots.put(ROBOT, manifest); world.failRestoreAt = failure;
            AssemblyTransactionResult result = AssemblyTransaction.disassemble(world, ROBOT, ANCHOR,
                    ComponentOrientation.NORTH_UP, manifest);
            assertEquals(AssemblyTransactionResult.Status.MUTATION_FAILED, result.status);
            assertTrue(world.blocks.isEmpty()); assertTrue(world.robotExists(ROBOT));
        }
    }

    @Test public void occupiedTargetLeavesEntityAndWorldUntouched() {
        ModularRobotManifest manifest = ModularManifestTest.manifest(); FakeWorld world = mobileWorld();
        world.robots.put(ROBOT, manifest); GridVector occupied = ANCHOR.add(manifest.getModules().get(1).localPosition);
        world.blocks.put(occupied, manifest.getModules().get(0)); Map<GridVector, ModularBlockSnapshot> before = copy(world.blocks);
        AssemblyTransactionResult result = AssemblyTransaction.disassemble(world, ROBOT, ANCHOR,
                ComponentOrientation.NORTH_UP, manifest);
        assertEquals(AssemblyTransactionResult.Status.PRECONDITION_FAILED, result.status);
        assertEquals("TARGET_BLOCKED", result.detail); assertTrue(world.robotExists(ROBOT)); assertWorldEquals(before, world.blocks);
    }

    @Test public void disassemblyCommitRestoresSnapshotsThenRemovesEntity() {
        ModularRobotManifest manifest = ModularManifestTest.manifest(); FakeWorld world = mobileWorld(); world.robots.put(ROBOT, manifest);
        assertTrue(AssemblyTransaction.disassemble(world, ROBOT, ANCHOR,
                ComponentOrientation.NORTH_UP, manifest).committed());
        assertEquals(3, world.blocks.size()); assertFalse(world.robotExists(ROBOT));
    }

    @Test public void entityRemovalFailureRollsBlocksBackAndKeepsEntity() {
        ModularRobotManifest manifest = ModularManifestTest.manifest(); FakeWorld world = mobileWorld();
        world.robots.put(ROBOT, manifest); world.failEntityRemove = true;
        AssemblyTransactionResult result = AssemblyTransaction.disassemble(world, ROBOT, ANCHOR,
                ComponentOrientation.NORTH_UP, manifest);
        assertEquals(AssemblyTransactionResult.Status.MUTATION_FAILED, result.status);
        assertTrue(world.blocks.isEmpty()); assertTrue(world.robotExists(ROBOT));
    }

    @Test public void failedRollbackIsExplicitlyRecoveryRequired() {
        ModularRobotManifest manifest = ModularManifestTest.manifest(); FakeWorld world = assembledWorld(manifest);
        world.failRemoveAt = 2; world.failRestoreAt = 1;
        AssemblyTransactionResult result = AssemblyTransaction.assemble(world, ROBOT, OWNER, ANCHOR,
                ComponentOrientation.NORTH_UP, manifest);
        assertEquals(AssemblyTransactionResult.Status.RECOVERY_REQUIRED, result.status);
    }

    private static FakeWorld assembledWorld(ModularRobotManifest manifest) {
        FakeWorld world = mobileWorld();
        for (ModularBlockSnapshot snapshot : manifest.getModules())
            world.blocks.put(ANCHOR.add(snapshot.localPosition), snapshot);
        return world;
    }
    private static FakeWorld mobileWorld() { return new FakeWorld(); }
    private static Map<GridVector, ModularBlockSnapshot> copy(Map<GridVector, ModularBlockSnapshot> source) {
        return new HashMap<GridVector, ModularBlockSnapshot>(source);
    }
    private static void assertWorldEquals(Map<GridVector, ModularBlockSnapshot> expected,
                                          Map<GridVector, ModularBlockSnapshot> actual) {
        assertEquals(expected.keySet(), actual.keySet());
        for (GridVector key : expected.keySet()) assertSame(expected.get(key), actual.get(key));
    }

    private static final class FakeWorld implements AssemblyTransactionWorld {
        final Map<GridVector, ModularBlockSnapshot> blocks = new HashMap<GridVector, ModularBlockSnapshot>();
        final Map<UUID, ModularRobotManifest> robots = new HashMap<UUID, ModularRobotManifest>();
        int removeCalls, restoreCalls, failRemoveAt, failRestoreAt; boolean failSpawn, failEntityRemove;
        @Override public boolean matches(GridVector p, ModularBlockSnapshot expected) { return blocks.get(p) == expected; }
        @Override public boolean remove(GridVector p, ModularBlockSnapshot expected) {
            if (++removeCalls == failRemoveAt) return false; return blocks.remove(p) == expected;
        }
        @Override public boolean restore(GridVector p, ModularBlockSnapshot snapshot) {
            if (++restoreCalls == failRestoreAt || blocks.containsKey(p)) return false; blocks.put(p, snapshot); return true;
        }
        @Override public boolean isLoadedAndEmpty(GridVector p) { return !blocks.containsKey(p); }
        @Override public boolean spawnRobot(UUID id, UUID owner, GridVector anchor, ModularRobotManifest manifest) {
            if (failSpawn || robots.containsKey(id)) return false; robots.put(id, manifest); return true;
        }
        @Override public boolean removeRobot(UUID id) { return !failEntityRemove && robots.remove(id) != null; }
        @Override public boolean robotExists(UUID id) { return robots.containsKey(id); }
    }
}
