package br.com.craftonica.robot.modular.drive.forge;

import br.com.craftonica.robot.modular.EntityModularRobot;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Deterministically admits at most 64 modular robots per dimension tick, without a queue. */
public final class ModularRobotTickBudget {
    public static final int MAX_ROBOTS_PER_DIMENSION_TICK = 64;
    private static final WeakHashMap<World, Snapshot> SNAPSHOTS = new WeakHashMap<World, Snapshot>();
    private ModularRobotTickBudget() { }

    @SuppressWarnings("unchecked") public static boolean allows(EntityModularRobot robot) {
        if (robot == null || robot.worldObj == null) return false;
        World world = robot.worldObj; long tick = world.getTotalWorldTime(); Snapshot snapshot;
        synchronized (SNAPSHOTS) {
            snapshot = SNAPSHOTS.get(world);
            if (snapshot == null || snapshot.tick != tick) {
                List<UUID> ids = new ArrayList<UUID>();
                for (Entity entity : (List<Entity>) world.loadedEntityList)
                    if (entity instanceof EntityModularRobot
                            && ((EntityModularRobot) entity).getRobotState() != null)
                        ids.add(((EntityModularRobot) entity).getRobotState().getRobotId());
                snapshot = new Snapshot(tick, select(ids, tick)); SNAPSHOTS.put(world, snapshot);
            }
        }
        return robot.getRobotState() != null && snapshot.selected.contains(robot.getRobotState().getRobotId());
    }

    static Set<UUID> select(List<UUID> source, long tick) {
        List<UUID> ids = new ArrayList<UUID>(source); Collections.sort(ids);
        Set<UUID> selected = new HashSet<UUID>();
        if (ids.isEmpty()) return selected;
        int start = (int) (tick % ids.size()); if (start < 0) start += ids.size();
        for (int i = 0; i < ids.size() && i < MAX_ROBOTS_PER_DIMENSION_TICK; i++)
            selected.add(ids.get((start + i) % ids.size()));
        return selected;
    }
    private static final class Snapshot {
        final long tick; final Set<UUID> selected;
        Snapshot(long tick, Set<UUID> selected) { this.tick = tick; this.selected = selected; }
    }
}
