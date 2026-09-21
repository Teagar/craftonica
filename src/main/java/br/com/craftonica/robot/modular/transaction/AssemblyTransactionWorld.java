package br.com.craftonica.robot.modular.transaction;

import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;

import java.util.UUID;

/** Mutation boundary used by the pure transaction coordinator and Forge adapter. */
public interface AssemblyTransactionWorld {
    boolean matches(GridVector worldPosition, ModularBlockSnapshot expected);
    boolean remove(GridVector worldPosition, ModularBlockSnapshot expected);
    boolean restore(GridVector worldPosition, ModularBlockSnapshot snapshot);
    boolean isLoadedAndEmpty(GridVector worldPosition);
    boolean spawnRobot(UUID robotId, UUID ownerId, GridVector anchor, ModularRobotManifest manifest);
    boolean removeRobot(UUID robotId);
    boolean robotExists(UUID robotId);
}
