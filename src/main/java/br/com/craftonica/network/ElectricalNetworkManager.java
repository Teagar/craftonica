package br.com.craftonica.network;

import br.com.craftonica.electrical.CircuitResult;
import br.com.craftonica.electrical.CircuitStatus;
import br.com.craftonica.electrical.nodal.*;
import br.com.craftonica.electrical.nodal.forge.ForgeNodalSnapshotExtractor;
import br.com.craftonica.electrical.nodal.forge.NodalExtractionResult;
import br.com.craftonica.electrical.nodal.forge.WorldForgeAccess;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import br.com.craftonica.block.IElectricalBlock;
import br.com.craftonica.tile.TileEntityCircuitBreaker;

import java.util.*;

/** Server-owned network cache. A cache is only visible after its complete solve. */
public final class ElectricalNetworkManager {
    static final int MAX_NETWORKS_PER_TICK_FOR_TEST = 4;
    static final int MAX_UNKNOWNS_PER_TICK_FOR_TEST = 512;
    private static final Map<World, ElectricalNetworkManager> MANAGERS =
            new WeakHashMap<World, ElectricalNetworkManager>();
    private static final Comparator<BlockPosition> POSITION_ORDER = new Comparator<BlockPosition>() {
        @Override public int compare(BlockPosition a, BlockPosition b) {
            return a.compareTo(b);
        }
    };

    private final World world;
    private final ForgeNodalSnapshotExtractor extractor;
    private final DcNodalSolver solver = new DcNodalSolver();
    private final NavigableSet<BlockPosition> dirty = new TreeSet<BlockPosition>(POSITION_ORDER);
    private final Map<BlockPosition, CircuitResult> lastResults = new HashMap<BlockPosition, CircuitResult>();
    private volatile Map<BlockPosition, NetworkCache> published = Collections.emptyMap();
    private long generation;
    private long solveCount;

    private ElectricalNetworkManager(World world) {
        this.world = world;
        this.extractor = new ForgeNodalSnapshotExtractor(new WorldForgeAccess(world));
    }

    public static ElectricalNetworkManager forWorld(World world) {
        ElectricalNetworkManager manager = MANAGERS.get(world);
        if (manager == null) {
            manager = new ElectricalNetworkManager(world);
            MANAGERS.put(world, manager);
        }
        return manager;
    }

    public static void unload(World world) { MANAGERS.remove(world); }

    public synchronized void invalidateAround(BlockPosition position) {
        if (world.isRemote) return;
        Set<BlockPosition> seeds = new TreeSet<BlockPosition>(POSITION_ORDER);
        seeds.add(position);
        for (int side = 0; side < 6; side++) seeds.add(offset(position, side));
        Map<BlockPosition, NetworkCache> next = new HashMap<BlockPosition, NetworkCache>(published);
        for (BlockPosition seed : seeds) {
            NetworkCache cache = published.get(seed);
            if (cache != null) {
                for (BlockPosition member : cache.members) {
                    next.remove(member);
                    dirty.add(member);
                }
            }
            if (isElectricalAndLoaded(seed)) dirty.add(seed);
        }
        generation++;
        published = immutable(next);
    }

    public synchronized void loadChunk(Chunk chunk) {
        if (world.isRemote) return;
        long loadedChunk = chunkKey(chunk.xPosition, chunk.zPosition);
        Set<NetworkCache> invalidated = new HashSet<NetworkCache>();
        for (NetworkCache cache : published.values()) if (cache.frontierChunks.contains(loadedChunk)) invalidated.add(cache);
        if (!invalidated.isEmpty()) removeCaches(invalidated, false, 0, 0);
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (ExtendedBlockStorage section : sections) {
            if (section == null || section.isEmpty()) continue;
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if (section.getBlockByExtId(x, y, z) instanceof IElectricalBlock)
                    dirty.add(new BlockPosition((chunk.xPosition << 4) + x, section.getYLocation() + y,
                            (chunk.zPosition << 4) + z));
            }
        }
    }

    public synchronized void unloadChunk(Chunk chunk) {
        if (world.isRemote) return;
        int minX = chunk.xPosition << 4, minZ = chunk.zPosition << 4;
        removeChunkPositions(dirty, minX, minZ);
        Map<BlockPosition, NetworkCache> next = new HashMap<BlockPosition, NetworkCache>(published);
        Set<NetworkCache> removed = new HashSet<NetworkCache>();
        for (NetworkCache cache : published.values()) {
            for (BlockPosition member : cache.members) {
                if (inChunk(member, minX, minZ)) { removed.add(cache); break; }
            }
        }
        for (NetworkCache cache : removed) for (BlockPosition member : cache.members) {
            next.remove(member);
            lastResults.remove(member);
            if (!inChunk(member, minX, minZ) && isElectricalAndLoaded(member)) dirty.add(member);
        }
        removeChunkPositions(lastResults.keySet(), minX, minZ);
        generation++;
        published = immutable(next);
    }

    public synchronized void tick() {
        if (world.isRemote || dirty.isEmpty()) return;
        int solvedNetworks = 0, usedUnknowns = 0;
        while (solvedNetworks < MAX_NETWORKS_PER_TICK_FOR_TEST && !dirty.isEmpty()) {
            BlockPosition root = dirty.first();
            dirty.remove(root);
            if (!isElectricalAndLoaded(root)) continue;
            NodalExtractionResult extraction = extractor.extract(root);
            Set<BlockPosition> members = positions(extraction);
            dirty.removeAll(members);
            Prepared prepared = prepare(extraction);
            int unknowns = prepared.unknowns;
            if (prepared.preflight == null && solvedNetworks > 0 && usedUnknowns + unknowns > MAX_UNKNOWNS_PER_TICK_FOR_TEST) {
                dirty.add(root);
                dirty.addAll(members);
                break;
            }
            NodalCircuitResult nodal = prepared.preflight == null ? solver.solve(prepared.system) : prepared.preflight;
            if (prepared.preflight == null) usedUnknowns += Math.min(unknowns, MAX_UNKNOWNS_PER_TICK_FOR_TEST);
            CircuitResult legacy = project(nodal, prepared.circuit, extraction);
            NetworkCache cache = new NetworkCache(++generation, members, nodal, legacy, prepared.circuit,
                    prepared.system, frontierChunks(extraction));
            publish(cache);
            for (BlockPosition member : members) {
                CircuitResult local = getLocalResult(member);
                ElectricalFeedback.networkTransition(world, member, lastResults.get(member), local);
                if (local == null) lastResults.remove(member);
                else lastResults.put(member, local);
                observeProtection(member, cache);
            }
            solvedNetworks++;
            solveCount++;
        }
    }

    public CircuitResult getResult(BlockPosition position) {
        NetworkCache cache = published.get(position);
        return cache == null ? null : cache.legacy;
    }

    public boolean shareNetwork(BlockPosition first, BlockPosition second) {
        NetworkCache cache = published.get(first);
        return cache != null && cache.members.contains(second);
    }

    public long getSolveCount() { return solveCount; }

    public NodalCircuitResult getNodalResult(BlockPosition position) {
        NetworkCache cache = published.get(position);
        return cache == null ? null : cache.nodal;
    }

    public BranchResult getBranchResult(BlockPosition position) {
        NetworkCache cache = published.get(position);
        if (cache == null || !cache.nodal.isSolved()) return null;
        BranchResult found = null;
        for (BranchResult branch : cache.nodal.getBranchResults().values()) {
            if (!position.equals(branch.getBranch().getPosition())) continue;
            if ("source_internal".equals(branch.getBranch().getComponentKind())) continue;
            if (found != null) return null;
            found = branch;
        }
        return found;
    }

    public Double getTerminalVoltage(BlockPosition position, int side) {
        NetworkCache cache = published.get(position);
        if (cache == null || side < 0 || side >= Face.values().length) return null;
        NodeId nodeId = terminalNode(cache, position, side);
        if (nodeId == null) return null;
        if (nodeId.isReference()) return 0.0;
        NodeResult node = cache.nodal.getNodeResult(nodeId);
        return node == null || node.getValidity() != ValueValidity.VALID ? null : node.getVoltage();
    }

    public ResistanceMeasurement measureResistance(BlockPosition first, int firstSide,
                                                   BlockPosition second, int secondSide) {
        NetworkCache cache = published.get(first);
        if (cache == null || cache != published.get(second) || cache.system == null)
            return ResistanceMeasurement.unavailable();
        NodeId positive = terminalNode(cache, first, firstSide);
        NodeId negative = terminalNode(cache, second, secondSide);
        if (positive == null || negative == null) return ResistanceMeasurement.unavailable();
        if (positive.equals(negative)) return ResistanceMeasurement.valid(0.0);
        if (cache.system.hasNonlinearElements()) return ResistanceMeasurement.nonlinearUnsupported();
        BranchId testId = new BranchId(first, "measurement_test", firstSide * 6 + secondSide);
        MnaSystem auxiliary = cache.system.deenergizedWithTestSource(testId, positive, negative);
        if (auxiliary == null || auxiliary.getUnknownCount() > NodalLimits.MAX_UNKNOWNS)
            return ResistanceMeasurement.unavailable();
        NodalCircuitResult measured = solver.solve(auxiliary);
        if (!measured.isSolved()) {
            return measured.getStatus() == SolveStatus.FLOATING_NODE || measured.getStatus() == SolveStatus.SINGULAR_MATRIX
                    ? ResistanceMeasurement.openCircuit() : ResistanceMeasurement.unavailable();
        }
        BranchResult test = measured.getBranchResult(testId);
        if (test == null || test.getValidity() != ValueValidity.VALID) return ResistanceMeasurement.unavailable();
        double current = Math.abs(test.getCurrent());
        return current <= 1e-12 ? ResistanceMeasurement.openCircuit() : ResistanceMeasurement.valid(1.0 / current);
    }

    private NodeId terminalNode(NetworkCache cache, BlockPosition position, int side) {
        if (side < 0 || side >= Face.values().length) return null;
        for (Map.Entry<TerminalId, NodeId> entry : cache.circuit.getTerminalToNode().entrySet()) {
            TerminalId terminal = entry.getKey();
            if (position.equals(terminal.getPosition()) && terminal.getFace() == Face.values()[side]) return entry.getValue();
        }
        return null;
    }

    public CircuitResult getLocalResult(BlockPosition position) {
        NetworkCache cache = published.get(position);
        if (cache == null || !cache.nodal.isSolved()) return null;
        BranchResult branch = getBranchResult(position);
        if (branch == null) return cache.legacy;
        CircuitStatus status = Math.abs(branch.getCurrent()) > 1e-12
                ? CircuitStatus.CLOSED : CircuitStatus.OPEN_CIRCUIT;
        String detail = "nodal_branch";
        if ("led".equals(branch.getBranch().getComponentKind()) || "diode".equals(branch.getBranch().getComponentKind())) {
            boolean burned = false;
            for (ComponentSnapshot snapshot : cache.circuit.getSnapshots()) {
                if (position.equals(snapshot.getPosition()) && Boolean.parseBoolean(snapshot.getState().get("burned"))) {
                    burned = true;
                }
            }
            if (burned) {
                status = CircuitStatus.OPEN_CIRCUIT;
                detail = "led_burned";
            } else if (branch.getVoltage() < -1e-12) {
                status = CircuitStatus.REVERSED_POLARITY;
                detail = "led_reversed";
            } else if (branch.getCurrent() > 0.03) {
                status = CircuitStatus.OVERCURRENT;
                detail = "led_overcurrent";
            }
        }
        return new CircuitResult(status, branch.getVoltage(), Math.abs(branch.getCurrent()),
                Math.abs(branch.getCurrent()) < 1e-15 ? Double.POSITIVE_INFINITY
                        : Math.abs(branch.getVoltage() / branch.getCurrent()), detail);
    }

    private Prepared prepare(NodalExtractionResult extraction) {
        NodalCircuitBuilder builder = new NodalCircuitBuilder();
        for (ComponentSnapshot snapshot : extraction.getSnapshots()) builder.add(snapshot);
        NodalCircuit circuit = builder.build();
        MnaSystem.Builder system = MnaSystem.builder();
        List<CircuitDiagnostic> errors = new ArrayList<CircuitDiagnostic>(extraction.getDiagnostics());
        errors.addAll(circuit.getDiagnostics());
        if (hasError(errors)) return new Prepared(null, circuit, 0, NodalCircuitResult.unsolved(status(errors), errors));
        try {
            for (ComponentSnapshot snapshot : circuit.getSnapshots()) {
                List<TerminalSnapshot> terminals = snapshot.getTerminals();
                if (terminals.isEmpty()) continue;
                NodeId a = circuit.getNode(terminals.get(0).getId());
                NodeId b = terminals.size() > 1 ? circuit.getNode(terminals.get(1).getId()) : NodeId.REFERENCE;
                BranchId id = new BranchId(snapshot.getPosition(), snapshot.getKind(), 0);
                if ("source".equals(snapshot.getKind())) system.powerSource(id, a, NodeId.named("source:" + snapshot.getPosition()),
                        snapshot.getParameters().get("voltage"), snapshot.getParameters().get("internalResistance"));
                else if ("resistor".equals(snapshot.getKind())) system.resistor(id, a, b, snapshot.getParameters().get("resistance"));
                else if ("potentiometer".equals(snapshot.getKind())) {
                    NodeId cursor = circuit.getNode(terminals.get(1).getId());
                    NodeId terminalB = circuit.getNode(terminals.get(2).getId());
                    system.resistor(id, a, cursor, snapshot.getParameters().get("resistanceA"));
                    system.resistor(new BranchId(snapshot.getPosition(), snapshot.getKind(), 1), cursor, terminalB,
                            snapshot.getParameters().get("resistanceB"));
                }
                else if ("switch".equals(snapshot.getKind())) system.switchBranch(id, a, b, Boolean.parseBoolean(snapshot.getState().get("closed")));
                else if ("breaker".equals(snapshot.getKind())) system.breaker(id, a, b, Boolean.parseBoolean(snapshot.getState().get("closed")));
                else if ("led".equals(snapshot.getKind())) system.led(id, a, b, Boolean.parseBoolean(snapshot.getState().get("burned")));
                else if ("diode".equals(snapshot.getKind())) system.diode(id, a, b,
                        snapshot.getParameters().get("forwardVoltage"), snapshot.getParameters().get("dynamicResistance"));
            }
        } catch (IllegalArgumentException invalidComponent) {
            errors.add(new CircuitDiagnostic(DiagnosticCode.INVALID_COMPONENT_DATA,
                    CircuitDiagnostic.Severity.ERROR, Collections.<BlockPosition>emptyList()));
            return new Prepared(null, circuit, 0, NodalCircuitResult.unsolved(SolveStatus.INVALID_COMPONENT_DATA, errors));
        }
        MnaSystem built = system.build();
        return new Prepared(built, circuit, built.getUnknownCount(), null);
    }

    private boolean hasError(List<CircuitDiagnostic> diagnostics) {
        for (CircuitDiagnostic diagnostic : diagnostics)
            if (diagnostic.getSeverity() == CircuitDiagnostic.Severity.ERROR) return true;
        return false;
    }

    private SolveStatus status(List<CircuitDiagnostic> diagnostics) {
        for (CircuitDiagnostic diagnostic : diagnostics) {
            switch (diagnostic.getCode()) {
                case INCOMPLETE_NETWORK: return SolveStatus.INCOMPLETE_NETWORK;
                case NETWORK_TOO_LARGE: return SolveStatus.NETWORK_TOO_LARGE;
                case TERMINAL_LIMIT: return SolveStatus.TERMINAL_LIMIT;
                case BRANCH_LIMIT: return SolveStatus.BRANCH_LIMIT;
                case MISSING_REFERENCE: return SolveStatus.MISSING_REFERENCE;
                case INVALID_COMPONENT_DATA: return SolveStatus.INVALID_COMPONENT_DATA;
                default: break;
            }
        }
        return SolveStatus.INVALID_COMPONENT_DATA;
    }

    private void observeProtection(BlockPosition position, NetworkCache cache) {
        TileEntityCircuitBreaker breaker = null;
        if (world.getTileEntity(position.x, position.y, position.z) instanceof TileEntityCircuitBreaker)
            breaker = (TileEntityCircuitBreaker) world.getTileEntity(position.x, position.y, position.z);
        if (breaker == null) return;
        BranchResult branch = null;
        for (BranchResult candidate : cache.nodal.getBranchResults().values())
            if (position.equals(candidate.getBranch().getPosition()) && "breaker".equals(candidate.getBranch().getComponentKind())) { branch = candidate; break; }
        if (branch != null) breaker.observe(branch.getCurrent(), branch.getAbsorbedPower());
    }

    private CircuitResult project(NodalCircuitResult result, NodalCircuit circuit, NodalExtractionResult extraction) {
        double current = 0.0, voltage = 5.0, resistance = 0.0;
        CircuitStatus status = CircuitStatus.UNSUPPORTED_TOPOLOGY;
        String detail = "nodal_" + result.getStatus().name().toLowerCase(Locale.ENGLISH);
        for (ComponentSnapshot snapshot : circuit.getSnapshots()) {
            if ("resistor".equals(snapshot.getKind())) resistance += snapshot.getParameters().get("resistance");
            if ("potentiometer".equals(snapshot.getKind())) resistance += snapshot.getParameters().get("nominalResistance");
            if ("source".equals(snapshot.getKind())) {
                BranchResult branch = result.getBranchResult(new BranchId(snapshot.getPosition(), "source", 0));
                if (branch != null) current = Math.abs(branch.getCurrent());
            }
        }
        for (CircuitDiagnostic diagnostic : extraction.getDiagnostics()) {
            if (diagnostic.getCode() == DiagnosticCode.NETWORK_TOO_LARGE) status = CircuitStatus.NETWORK_TOO_LARGE;
            detail = diagnostic.getCode().name().toLowerCase(Locale.ENGLISH);
        }
        for (CircuitDiagnostic diagnostic : result.getDiagnostics()) {
            switch (diagnostic.getCode()) {
                case POLARITY_INCORRECT: status = CircuitStatus.REVERSED_POLARITY; break;
                case LED_OVERCURRENT: status = CircuitStatus.OVERCURRENT; break;
                default: break;
            }
            detail = diagnostic.getCode().name().toLowerCase(Locale.ENGLISH);
        }
        if (result.isSolved() && status == CircuitStatus.UNSUPPORTED_TOPOLOGY
                && extraction.isComplete() && circuit.isValid()) {
            status = current > 1e-12 ? CircuitStatus.CLOSED : CircuitStatus.OPEN_CIRCUIT;
            detail = status == CircuitStatus.CLOSED ? "nodal_solved" : "switch_open_or_no_current";
        }
        if (result.getStatus() == SolveStatus.MATRIX_LIMIT) status = CircuitStatus.NETWORK_TOO_LARGE;
        if (status == CircuitStatus.OVERCURRENT) detail = "led_overcurrent";
        if (current > ProtectionModel.MAX_CURRENT_AMPS) {
            status = CircuitStatus.OVERCURRENT;
            detail = "source_protection_limit";
        }
        if (resistance == 0.0 && current > 0.0) resistance = voltage / current;
        return new CircuitResult(status, voltage, current, resistance, detail);
    }

    private void publish(NetworkCache cache) {
        Map<BlockPosition, NetworkCache> next = new HashMap<BlockPosition, NetworkCache>(published);
        for (BlockPosition member : cache.members) next.put(member, cache);
        published = immutable(next);
    }

    private Set<BlockPosition> positions(NodalExtractionResult extraction) {
        Set<BlockPosition> result = new TreeSet<BlockPosition>(POSITION_ORDER);
        for (ComponentSnapshot snapshot : extraction.getSnapshots()) result.add(snapshot.getPosition());
        for (CircuitDiagnostic diagnostic : extraction.getDiagnostics())
            if (diagnostic.getCode() == DiagnosticCode.NETWORK_TOO_LARGE) result.addAll(diagnostic.getPositions());
        return result;
    }

    private Set<Long> frontierChunks(NodalExtractionResult extraction) {
        Set<Long> result = new HashSet<Long>();
        for (CircuitDiagnostic diagnostic : extraction.getDiagnostics())
            if (diagnostic.getCode() == DiagnosticCode.INCOMPLETE_NETWORK)
                for (BlockPosition position : diagnostic.getPositions()) result.add(chunkKey(position.x >> 4, position.z >> 4));
        return result;
    }

    private long chunkKey(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }

    private void removeCaches(Set<NetworkCache> caches, boolean unloading, int minX, int minZ) {
        Map<BlockPosition, NetworkCache> next = new HashMap<BlockPosition, NetworkCache>(published);
        for (NetworkCache cache : caches) for (BlockPosition member : cache.members) {
            next.remove(member);
            lastResults.remove(member);
            if ((!unloading || !inChunk(member, minX, minZ)) && isElectricalAndLoaded(member)) dirty.add(member);
        }
        generation++;
        published = immutable(next);
    }

    private boolean isElectricalAndLoaded(BlockPosition p) {
        return world.getChunkProvider().chunkExists(p.x >> 4, p.z >> 4)
                && world.getBlock(p.x, p.y, p.z) instanceof IElectricalBlock;
    }

    private Map<BlockPosition, NetworkCache> immutable(Map<BlockPosition, NetworkCache> source) {
        return Collections.unmodifiableMap(new HashMap<BlockPosition, NetworkCache>(source));
    }

    private boolean inChunk(BlockPosition p, int minX, int minZ) {
        return p.x >= minX && p.x < minX + 16 && p.z >= minZ && p.z < minZ + 16;
    }

    private void removeChunkPositions(Set<BlockPosition> positions, int minX, int minZ) {
        for (Iterator<BlockPosition> iterator = positions.iterator(); iterator.hasNext();)
            if (inChunk(iterator.next(), minX, minZ)) iterator.remove();
    }

    private BlockPosition offset(BlockPosition p, int side) {
        switch (side) {
            case 0: return new BlockPosition(p.x, p.y - 1, p.z);
            case 1: return new BlockPosition(p.x, p.y + 1, p.z);
            case 2: return new BlockPosition(p.x, p.y, p.z - 1);
            case 3: return new BlockPosition(p.x, p.y, p.z + 1);
            case 4: return new BlockPosition(p.x - 1, p.y, p.z);
            default: return new BlockPosition(p.x + 1, p.y, p.z);
        }
    }

    private static final class Prepared {
        private final MnaSystem system; private final NodalCircuit circuit; private final int unknowns; private final NodalCircuitResult preflight;
        private Prepared(MnaSystem system, NodalCircuit circuit, int unknowns, NodalCircuitResult preflight) { this.system = system; this.circuit = circuit; this.unknowns = unknowns; this.preflight = preflight; }
    }
    private static final class NetworkCache {
        private final long generation; private final Set<BlockPosition> members;
        private final NodalCircuitResult nodal; private final CircuitResult legacy; private final NodalCircuit circuit; private final MnaSystem system; private final Set<Long> frontierChunks;
        private NetworkCache(long generation, Set<BlockPosition> members, NodalCircuitResult nodal, CircuitResult legacy, NodalCircuit circuit, MnaSystem system, Set<Long> frontierChunks) {
            this.generation = generation;
            this.members = Collections.unmodifiableSet(new TreeSet<BlockPosition>(members));
            this.nodal = nodal; this.legacy = legacy; this.circuit = circuit; this.system = system;
            this.frontierChunks = Collections.unmodifiableSet(new HashSet<Long>(frontierChunks));
        }
    }
}
