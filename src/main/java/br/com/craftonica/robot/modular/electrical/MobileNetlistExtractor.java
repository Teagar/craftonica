package br.com.craftonica.robot.modular.electrical;

import br.com.craftonica.robot.modular.ComponentCatalog;
import br.com.craftonica.robot.modular.ComponentType;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.ElectricalPort;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Forge-free topology extraction from captured component descriptors and TileEntity state. */
public final class MobileNetlistExtractor {
    private MobileNetlistExtractor() { }

    public static MobileElectricalNetlist extract(List<ModularBlockSnapshot> modules, ComponentCatalog catalog) {
        if (modules == null || catalog == null) throw new IllegalArgumentException("netlist input");
        Set<UUID> boardIds = boardIds(modules);
        if (boardIds.size() > 1) throw new IllegalArgumentException("multiple RoboBoards are unsupported");
        Map<GridVector, ModularBlockSnapshot> moduleByPosition = modulesByPosition(modules);
        List<RawTerminal> terminals = new ArrayList<RawTerminal>(); Map<GridVector, List<Integer>> byPosition = new HashMap<GridVector, List<Integer>>();
        for (ModularBlockSnapshot module : modules) {
            ComponentType type = catalog.require(module.componentTypeId); List<ElectricalPort> ports = type.getElectricalPorts();
            if (StandardComponentCatalog.WIRE.equals(module.componentTypeId)) {
                NBTTagCompound wire = module.getTileData();
                if (wire == null || wire.getInteger("WireSchema") != 1)
                    throw new IllegalArgumentException("invalid wire state");
                int blocked = wire.getByte("BlockedFaces") & 63;
                for (ElectricalPort port : ports) {
                    Direction localFace = port.pose.face; Direction face = module.localOrientation.toWorld(localFace);
                    int originalSide = localFace.ordinal(); if ((blocked & 1 << originalSide) != 0) continue;
                    add(terminals, byPosition, module, module.localPosition, port, face, "");
                }
            } else {
                String role = role(module, boardIds);
                for (ElectricalPort port : ports) {
                    Direction face = module.localOrientation.toWorld(port.pose.face);
                    GridVector contact = module.localPosition.add(module.localOrientation.toWorld(port.pose.cell));
                    if (StandardComponentCatalog.H_BRIDGE.equals(module.componentTypeId))
                        requireBridgeTerminal(contact, face, moduleByPosition);
                    add(terminals, byPosition, module, contact, port, face,
                            StandardComponentCatalog.ROBO_PORT.equals(module.componentTypeId) ? role : "");
                }
            }
        }
        if (terminals.size() > MobileElectricalNetlist.MAX_TERMINALS) throw new IllegalArgumentException("terminal count");
        requireUniqueDigitalRoles(terminals);
        UnionFind union = new UnionFind(terminals.size());
        for (List<Integer> indexes : byPosition.values()) {
            if (indexes.isEmpty() || !StandardComponentCatalog.WIRE.equals(terminals.get(indexes.get(0)).componentTypeId)) continue;
            for (int i = 1; i < indexes.size(); i++) union.join(indexes.get(0).intValue(), indexes.get(i).intValue());
        }
        for (int i = 0; i < terminals.size(); i++) {
            RawTerminal terminal = terminals.get(i); GridVector neighbor = terminal.contactPosition.add(terminal.face.vector);
            List<Integer> candidates = byPosition.get(neighbor); if (candidates == null) continue;
            for (Integer candidateIndex : candidates) {
                RawTerminal candidate = terminals.get(candidateIndex.intValue());
                if (candidate.face == terminal.face.opposite() && compatible(terminal, candidate))
                    union.join(i, candidateIndex.intValue());
            }
        }
        Map<Integer, String> rootKeys = new HashMap<Integer, String>();
        for (int i = 0; i < terminals.size(); i++) {
            int root = union.root(i); String key = terminals.get(i).key(); String previous = rootKeys.get(Integer.valueOf(root));
            if (previous == null || key.compareTo(previous) < 0) rootKeys.put(Integer.valueOf(root), key);
        }
        List<Map.Entry<Integer, String>> ordered = new ArrayList<Map.Entry<Integer, String>>(rootKeys.entrySet());
        Collections.sort(ordered, new Comparator<Map.Entry<Integer, String>>() {
            @Override public int compare(Map.Entry<Integer, String> a, Map.Entry<Integer, String> b) { return a.getValue().compareTo(b.getValue()); }
        });
        Map<Integer, Integer> ids = new LinkedHashMap<Integer, Integer>();
        for (int i = 0; i < ordered.size(); i++) ids.put(ordered.get(i).getKey(), Integer.valueOf(i));
        List<MobileTerminal> result = new ArrayList<MobileTerminal>();
        for (int i = 0; i < terminals.size(); i++) {
            RawTerminal value = terminals.get(i); result.add(new MobileTerminal(value.componentPosition, value.componentTypeId,
                    value.portId, value.face, value.role, ids.get(Integer.valueOf(union.root(i))).intValue()));
        }
        return new MobileElectricalNetlist(result);
    }

    private static void add(List<RawTerminal> terminals, Map<GridVector, List<Integer>> byPosition,
            ModularBlockSnapshot module, GridVector contactPosition, ElectricalPort port,
            Direction face, String role) {
        int index = terminals.size(); terminals.add(new RawTerminal(contactPosition, module.localPosition,
                module.componentTypeId, port.id, face, role, port.domain));
        List<Integer> values = byPosition.get(contactPosition);
        if (values == null) { values = new ArrayList<Integer>(); byPosition.put(contactPosition, values); }
        values.add(Integer.valueOf(index));
    }

    private static void requireBridgeTerminal(GridVector position, Direction outward,
                                               Map<GridVector, ModularBlockSnapshot> modules) {
        ModularBlockSnapshot terminal = modules.get(position);
        if (terminal == null || !StandardComponentCatalog.H_BRIDGE_TERMINAL.equals(terminal.componentTypeId)
                || terminal.localOrientation.toWorld(Direction.NORTH) != outward)
            throw new IllegalArgumentException("missing or misoriented H-bridge terminal");
    }

    private static Map<GridVector, ModularBlockSnapshot> modulesByPosition(List<ModularBlockSnapshot> modules) {
        Map<GridVector, ModularBlockSnapshot> values = new HashMap<GridVector, ModularBlockSnapshot>();
        for (ModularBlockSnapshot module : modules)
            if (values.put(module.localPosition, module) != null)
                throw new IllegalArgumentException("duplicate module position");
        return values;
    }

    private static boolean compatible(RawTerminal a, RawTerminal b) {
        return StandardComponentCatalog.WIRE.equals(a.componentTypeId)
                || StandardComponentCatalog.WIRE.equals(b.componentTypeId) || a.domain == b.domain;
    }

    private static void requireUniqueDigitalRoles(List<RawTerminal> terminals) {
        Set<String> roles = new HashSet<String>();
        for (RawTerminal terminal : terminals) {
            if (!StandardComponentCatalog.ROBO_PORT.equals(terminal.componentTypeId)
                    || !terminal.role.matches("D(?:[0-9]|1[0-3])")) continue;
            if (!roles.add(terminal.role)) throw new IllegalArgumentException("duplicate RoboPort role " + terminal.role);
        }
    }

    private static String role(ModularBlockSnapshot module, Set<UUID> boardIds) {
        NBTTagCompound tile = module.getTileData();
        if (tile == null || tile.getInteger("PortSchema") != 1 || !tile.hasKey("Role")
                || !tile.hasKey("BoardMost") || !tile.hasKey("BoardLeast")) return "UNBOUND";
        UUID boardId = new UUID(tile.getLong("BoardMost"), tile.getLong("BoardLeast"));
        if (boardIds.size() != 1 || !boardIds.contains(boardId)) return "UNBOUND";
        int ordinal = tile.getByte("Role");
        String[] roles = { "D0", "D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8", "D9",
                "D10", "D11", "D12", "D13", "A0", "A1", "A2", "A3", "A4", "A5", "POWER_5V", "GROUND" };
        if (ordinal < 0 || ordinal >= roles.length) throw new IllegalArgumentException("invalid RoboPort role");
        return roles[ordinal];
    }

    private static Set<UUID> boardIds(List<ModularBlockSnapshot> modules) {
        Set<UUID> values = new HashSet<UUID>();
        for (ModularBlockSnapshot module : modules) {
            if (!StandardComponentCatalog.ROBO_BOARD.equals(module.componentTypeId)) continue;
            NBTTagCompound tile = module.getTileData();
            if (tile == null || !tile.hasKey("BoardMost") || !tile.hasKey("BoardLeast"))
                throw new IllegalArgumentException("invalid RoboBoard identity");
            values.add(new UUID(tile.getLong("BoardMost"), tile.getLong("BoardLeast")));
        }
        return values;
    }

    private static final class RawTerminal {
        final GridVector contactPosition, componentPosition;
        final String componentTypeId, portId, role; final Direction face;
        final ElectricalPort.Domain domain;
        RawTerminal(GridVector contactPosition, GridVector componentPosition, String componentTypeId,
                    String portId, Direction face, String role, ElectricalPort.Domain domain) {
            this.contactPosition = contactPosition; this.componentPosition = componentPosition;
            this.componentTypeId = componentTypeId; this.portId = portId;
            this.face = face; this.role = role; this.domain = domain;
        }
        String key() { return componentPosition + "/" + portId; }
    }

    private static final class UnionFind {
        final int[] parent;
        UnionFind(int size) { parent = new int[size]; for (int i = 0; i < size; i++) parent[i] = i; }
        int root(int value) { while (parent[value] != value) { parent[value] = parent[parent[value]]; value = parent[value]; } return value; }
        void join(int a, int b) { int ar = root(a), br = root(b); if (ar != br) parent[br] = ar; }
    }
}
