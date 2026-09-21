package br.com.craftonica.robot.modular.physics;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import org.junit.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CompoundCollisionProbeTest {
    @Test public void unloadedBoundaryStopsBeforeAnyCollisionRead() {
        RigidBodyProperties body = body(); FakeWorld world = new FakeWorld(); world.loaded = false;
        assertEquals(CompoundCollisionProbe.Result.UNLOADED,
                CompoundCollisionProbe.test(body, world, 0.5, 10.0, 0.5, 0.0));
        assertFalse(world.collisionRead);
    }

    @Test public void compoundVolumeReportsWallAndClearSpaceDeterministically() {
        RigidBodyProperties body = body(); FakeWorld world = new FakeWorld();
        world.wallX = 1.4;
        assertEquals(CompoundCollisionProbe.Result.CLEAR,
                CompoundCollisionProbe.test(body, world, 0.5, 10.0, 0.5, 0.0));
        assertEquals(CompoundCollisionProbe.Result.BLOCKED,
                CompoundCollisionProbe.test(body, world, 1.0, 10.0, 0.5, 0.0));
        assertTrue(world.collisionRead);
    }

    @Test public void sweptVolumeCannotTunnelAcrossWall() {
        RigidBodyProperties body = body(); FakeWorld world = new FakeWorld(); world.wallX = 1.4;
        TerrestrialRigidBodyModel.State from = new TerrestrialRigidBodyModel.State(
                0.5, 10.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0);
        TerrestrialRigidBodyModel.State to = new TerrestrialRigidBodyModel.State(
                2.5, 10.0, 0.5, 0.0, 0.0, 0.0, 0.0, 0.0);
        assertEquals(CompoundCollisionProbe.Result.BLOCKED,
                CompoundCollisionProbe.sweep(body, world, from, to));
    }

    private static RigidBodyProperties body() {
        ModularBlockSnapshot module = new ModularBlockSnapshot(StandardComponentCatalog.CHASSIS, 1,
                GridVector.ZERO, ComponentOrientation.NORTH_UP, "craftonica:robot_chassis", 0, null);
        return RigidBodyProperties.derive(new ModularRobotManifest(new UUID(5L, 6L),
                Collections.singletonList(module), Collections.<AssemblyEdge>emptyList()),
                StandardComponentCatalog.create());
    }

    private static final class FakeWorld implements CompoundCollisionProbe.WorldView {
        boolean loaded = true, collisionRead; double wallX = Double.POSITIVE_INFINITY;
        @Override public boolean isLoaded(AxisAlignedVolume worldVolume) { return loaded; }
        @Override public boolean collides(AxisAlignedVolume worldVolume) {
            collisionRead = true; return worldVolume.maximum.x > wallX;
        }
    }
}
