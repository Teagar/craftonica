package br.com.craftonica.robot.modular.transaction;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Atomic coordinator. The caller persists its journal before invoking a mutation. */
public final class AssemblyTransaction {
    private AssemblyTransaction() { }

    public static AssemblyTransactionResult assemble(AssemblyTransactionWorld world, UUID robotId,
            UUID ownerId, GridVector anchor, ComponentOrientation anchorOrientation,
            ModularRobotManifest manifest) {
        require(world, robotId, ownerId, anchor, anchorOrientation, manifest);
        List<Placement> placements = placements(anchor, anchorOrientation, manifest);
        for (Placement placement : placements) if (!world.matches(placement.worldPosition, placement.snapshot))
            return result(AssemblyTransactionResult.Status.PRECONDITION_FAILED, placement.worldPosition, "BLOCK_CHANGED");
        if (world.robotExists(robotId))
            return result(AssemblyTransactionResult.Status.PRECONDITION_FAILED, anchor, "ROBOT_ALREADY_EXISTS");
        List<Placement> removed = new ArrayList<Placement>();
        for (Placement placement : placements) {
            if (!world.remove(placement.worldPosition, placement.snapshot)) {
                return rollbackRemoved(world, removed, placement.worldPosition, "REMOVE_FAILED");
            }
            removed.add(placement);
        }
        if (!world.spawnRobot(robotId, ownerId, anchor, manifest))
            return rollbackRemoved(world, removed, anchor, "SPAWN_FAILED");
        return result(AssemblyTransactionResult.Status.COMMITTED, null, null);
    }

    public static AssemblyTransactionResult disassemble(AssemblyTransactionWorld world, UUID robotId,
            GridVector anchor, ComponentOrientation anchorOrientation, ModularRobotManifest manifest) {
        if (world == null || robotId == null || anchor == null || anchorOrientation == null || manifest == null)
            throw new IllegalArgumentException("transaction");
        if (!world.robotExists(robotId))
            return result(AssemblyTransactionResult.Status.PRECONDITION_FAILED, anchor, "ROBOT_MISSING");
        List<Placement> placements = placements(anchor, anchorOrientation, manifest);
        for (Placement placement : placements) if (!world.isLoadedAndEmpty(placement.worldPosition))
            return result(AssemblyTransactionResult.Status.PRECONDITION_FAILED, placement.worldPosition, "TARGET_BLOCKED");
        List<Placement> placed = new ArrayList<Placement>();
        for (Placement placement : placements) {
            if (!world.restore(placement.worldPosition, placement.snapshot))
                return rollbackPlaced(world, placed, placement.worldPosition, "PLACE_FAILED");
            placed.add(placement);
        }
        if (!world.removeRobot(robotId)) return rollbackPlaced(world, placed, anchor, "ENTITY_REMOVE_FAILED");
        return result(AssemblyTransactionResult.Status.COMMITTED, null, null);
    }

    private static List<Placement> placements(GridVector anchor, ComponentOrientation orientation,
                                               ModularRobotManifest manifest) {
        List<Placement> values = new ArrayList<Placement>();
        for (ModularBlockSnapshot snapshot : manifest.getModules())
            values.add(new Placement(anchor.add(orientation.toWorld(snapshot.localPosition)), snapshot));
        return values;
    }

    private static AssemblyTransactionResult rollbackRemoved(AssemblyTransactionWorld world,
            List<Placement> removed, GridVector failed, String detail) {
        boolean restored = true;
        for (int i = removed.size() - 1; i >= 0; i--) {
            Placement placement = removed.get(i);
            if (!world.restore(placement.worldPosition, placement.snapshot)) restored = false;
        }
        return result(restored ? AssemblyTransactionResult.Status.MUTATION_FAILED
                : AssemblyTransactionResult.Status.RECOVERY_REQUIRED, failed, detail);
    }

    private static AssemblyTransactionResult rollbackPlaced(AssemblyTransactionWorld world,
            List<Placement> placed, GridVector failed, String detail) {
        boolean removed = true;
        for (int i = placed.size() - 1; i >= 0; i--) {
            Placement placement = placed.get(i);
            if (!world.remove(placement.worldPosition, placement.snapshot)) removed = false;
        }
        return result(removed ? AssemblyTransactionResult.Status.MUTATION_FAILED
                : AssemblyTransactionResult.Status.RECOVERY_REQUIRED, failed, detail);
    }

    private static void require(AssemblyTransactionWorld world, UUID robotId, UUID ownerId,
            GridVector anchor, ComponentOrientation orientation, ModularRobotManifest manifest) {
        if (world == null || robotId == null || ownerId == null || anchor == null
                || orientation == null || manifest == null) throw new IllegalArgumentException("transaction");
    }

    private static AssemblyTransactionResult result(AssemblyTransactionResult.Status status,
                                                      GridVector position, String detail) {
        return AssemblyTransactionResult.of(status, position, detail);
    }

    private static final class Placement {
        final GridVector worldPosition; final ModularBlockSnapshot snapshot;
        Placement(GridVector worldPosition, ModularBlockSnapshot snapshot) {
            this.worldPosition = worldPosition; this.snapshot = snapshot;
        }
    }
}
