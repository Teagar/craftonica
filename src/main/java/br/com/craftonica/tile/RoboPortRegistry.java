package br.com.craftonica.tile;

import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Loaded-port index. Persistent ownership remains authoritative in each RoboPort NBT. */
public final class RoboPortRegistry {
    private static final Map<World, Map<UUID, Set<TileEntityRoboPort>>> BY_WORLD =
            new WeakHashMap<World, Map<UUID, Set<TileEntityRoboPort>>>();

    private RoboPortRegistry() { }

    public static synchronized void register(TileEntityRoboPort port) {
        if (port == null || port.getWorldObj() == null || port.getWorldObj().isRemote
                || port.getOwnerBoardId() == null || port.isInvalid()) return;
        Map<UUID, Set<TileEntityRoboPort>> worldPorts = BY_WORLD.get(port.getWorldObj());
        if (worldPorts == null) {
            worldPorts = new java.util.HashMap<UUID, Set<TileEntityRoboPort>>();
            BY_WORLD.put(port.getWorldObj(), worldPorts);
        }
        Set<TileEntityRoboPort> ports = worldPorts.get(port.getOwnerBoardId());
        if (ports == null) {
            ports = new LinkedHashSet<TileEntityRoboPort>();
            worldPorts.put(port.getOwnerBoardId(), ports);
        }
        ports.add(port);
    }

    public static synchronized void unregister(TileEntityRoboPort port) {
        if (port == null || port.getWorldObj() == null) return;
        Map<UUID, Set<TileEntityRoboPort>> worldPorts = BY_WORLD.get(port.getWorldObj());
        if (worldPorts == null) return;
        for (java.util.Iterator<Map.Entry<UUID, Set<TileEntityRoboPort>>> groups =
             worldPorts.entrySet().iterator(); groups.hasNext();) {
            Map.Entry<UUID, Set<TileEntityRoboPort>> group = groups.next();
            group.getValue().remove(port);
            if (group.getValue().isEmpty()) groups.remove();
        }
        if (worldPorts.isEmpty()) BY_WORLD.remove(port.getWorldObj());
    }

    public static synchronized List<TileEntityRoboPort> loadedPorts(TileEntityRoboBoard board) {
        if (board == null || board.getWorldObj() == null || board.getWorldObj().isRemote)
            return Collections.emptyList();
        Map<UUID, Set<TileEntityRoboPort>> worldPorts = BY_WORLD.get(board.getWorldObj());
        if (worldPorts == null) return Collections.emptyList();
        Set<TileEntityRoboPort> ports = worldPorts.get(board.getBoardId());
        if (ports == null) return Collections.emptyList();
        List<TileEntityRoboPort> loaded = new ArrayList<TileEntityRoboPort>();
        for (TileEntityRoboPort port : new ArrayList<TileEntityRoboPort>(ports)) {
            if (port == null || port.isInvalid() || port.getWorldObj() != board.getWorldObj()) {
                ports.remove(port);
            } else if (port.isBoundTo(board)) loaded.add(port);
        }
        return loaded;
    }
}
