package br.com.craftonica.network;

import br.com.craftonica.block.BlockElectricalButton;
import br.com.craftonica.block.BlockGround;
import br.com.craftonica.block.BlockLed;
import br.com.craftonica.block.BlockPowerSource;
import br.com.craftonica.block.BlockResistor;
import br.com.craftonica.block.IElectricalBlock;
import br.com.craftonica.electrical.BasicElectricalComponent;
import br.com.craftonica.electrical.CircuitGraph;
import br.com.craftonica.electrical.CircuitResult;
import br.com.craftonica.electrical.CircuitStatus;
import br.com.craftonica.electrical.SimpleCircuitSolver;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class ElectricalNetworkManager {
    private static final Map<World, ElectricalNetworkManager> MANAGERS =
            new WeakHashMap<World, ElectricalNetworkManager>();

    private final World world;
    private final WorldElectricalNetworkFinder finder = new WorldElectricalNetworkFinder();
    private final SimpleCircuitSolver solver = new SimpleCircuitSolver();
    private final Set<BlockPosition> dirty = new LinkedHashSet<BlockPosition>();
    private final Map<BlockPosition, CircuitResult> results = new HashMap<BlockPosition, CircuitResult>();
    private final Map<BlockPosition, Set<BlockPosition>> networks =
            new HashMap<BlockPosition, Set<BlockPosition>>();
    private long solveCount;

    private ElectricalNetworkManager(World world) {
        this.world = world;
    }

    public static ElectricalNetworkManager forWorld(World world) {
        ElectricalNetworkManager manager = MANAGERS.get(world);
        if (manager == null) {
            manager = new ElectricalNetworkManager(world);
            MANAGERS.put(world, manager);
        }
        return manager;
    }

    public static void unload(World world) {
        MANAGERS.remove(world);
    }

    public void invalidateAround(BlockPosition position) {
        if (world.isRemote) {
            return;
        }
        dirty.add(position);
        invalidateCachedNetwork(position);
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            BlockPosition neighbor = offset(position, direction);
            if (world.blockExists(neighbor.x, neighbor.y, neighbor.z)
                    && world.getBlock(neighbor.x, neighbor.y, neighbor.z) instanceof IElectricalBlock) {
                dirty.add(neighbor);
                invalidateCachedNetwork(neighbor);
            }
        }
    }

    public void loadChunk(Chunk chunk) {
        if (world.isRemote) {
            return;
        }
        ExtendedBlockStorage[] sections = chunk.getBlockStorageArray();
        for (ExtendedBlockStorage section : sections) {
            if (section == null || section.isEmpty()) {
                continue;
            }
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        if (section.getBlockByExtId(localX, localY, localZ) instanceof IElectricalBlock) {
                            dirty.add(new BlockPosition((chunk.xPosition << 4) + localX,
                                    section.getYLocation() + localY, (chunk.zPosition << 4) + localZ));
                        }
                    }
                }
            }
        }
    }

    public void unloadChunk(Chunk chunk) {
        int minX = chunk.xPosition << 4;
        int minZ = chunk.zPosition << 4;
        removeChunkPositions(dirty, minX, minZ);
        removeChunkPositions(results.keySet(), minX, minZ);
        removeChunkPositions(networks.keySet(), minX, minZ);
    }

    public void tick() {
        if (world.isRemote || dirty.isEmpty()) {
            return;
        }
        List<BlockPosition> roots = new ArrayList<BlockPosition>(dirty);
        dirty.clear();
        Set<BlockPosition> processed = new HashSet<BlockPosition>();
        for (BlockPosition root : roots) {
            if (processed.contains(root) || !world.blockExists(root.x, root.y, root.z)
                    || !(world.getBlock(root.x, root.y, root.z) instanceof IElectricalBlock)) {
                continue;
            }
            BoundedNetworkSearch.Result<BlockPosition> network = finder.discover(world, root);
            processed.addAll(network.getNodes());
            CircuitResult result = network.isLimitExceeded()
                    ? new CircuitResult(CircuitStatus.NETWORK_TOO_LARGE, 0.0, 0.0, 0.0, "network_limit")
                    : solve(network.getNodes());
            Set<BlockPosition> snapshot = new HashSet<BlockPosition>(network.getNodes());
            for (BlockPosition position : network.getNodes()) {
                results.put(position, result);
                networks.put(position, snapshot);
            }
            solveCount++;
        }
    }

    public CircuitResult getResult(BlockPosition position) {
        return results.get(position);
    }

    public boolean shareNetwork(BlockPosition first, BlockPosition second) {
        Set<BlockPosition> network = networks.get(first);
        return network != null && network.contains(second);
    }

    public long getSolveCount() {
        return solveCount;
    }

    private CircuitResult solve(Set<BlockPosition> positions) {
        try {
            CircuitGraph graph = new CircuitGraph();
            for (BlockPosition position : positions) {
                graph.add(componentAt(position));
            }
            for (BlockPosition position : positions) {
                for (BlockPosition neighbor : finder.connectedNeighbors(world, position)) {
                    if (positions.contains(neighbor) && position.toString().compareTo(neighbor.toString()) < 0) {
                        graph.connect(position.toString(), neighbor.toString());
                    }
                }
            }
            return solver.solve(graph);
        } catch (IllegalArgumentException error) {
            return new CircuitResult(CircuitStatus.UNSUPPORTED_TOPOLOGY, 0.0, 0.0, 0.0, "invalid_world_graph");
        }
    }

    private BasicElectricalComponent componentAt(BlockPosition position) {
        Block block = world.getBlock(position.x, position.y, position.z);
        String id = position.toString();
        if (block instanceof BlockPowerSource) {
            return BasicElectricalComponent.source(id, 5.0);
        }
        if (block instanceof BlockGround) {
            return BasicElectricalComponent.ground(id);
        }
        if (block instanceof BlockElectricalButton) {
            boolean closed = ((BlockElectricalButton) block).isClosed(world, position.x, position.y, position.z);
            return BasicElectricalComponent.electricalSwitch(id, closed);
        }
        if (block instanceof BlockResistor) {
            return BasicElectricalComponent.resistor(id, ((BlockResistor) block).getResistanceOhms());
        }
        if (block instanceof BlockLed) {
            int side = ((BlockLed) block).getAnodeSide(world, position.x, position.y, position.z);
            ForgeDirection direction = ForgeDirection.getOrientation(side);
            TileEntity tile = world.getTileEntity(position.x, position.y, position.z);
            boolean functional = !(tile instanceof br.com.craftonica.tile.TileEntityLed)
                    || !((br.com.craftonica.tile.TileEntityLed) tile).isBurned();
            return BasicElectricalComponent.led(id, 2.0, offset(position, direction).toString(), functional);
        }
        return BasicElectricalComponent.wire(id);
    }

    private BlockPosition offset(BlockPosition position, ForgeDirection direction) {
        return new BlockPosition(position.x + direction.offsetX, position.y + direction.offsetY,
                position.z + direction.offsetZ);
    }

    private void removeChunkPositions(Set<BlockPosition> positions, int minX, int minZ) {
        List<BlockPosition> copy = new ArrayList<BlockPosition>(positions);
        for (BlockPosition position : copy) {
            if (position.x >= minX && position.x < minX + 16
                    && position.z >= minZ && position.z < minZ + 16) {
                positions.remove(position);
            }
        }
    }

    private void invalidateCachedNetwork(BlockPosition position) {
        Set<BlockPosition> network = networks.remove(position);
        if (network == null) {
            results.remove(position);
            return;
        }
        for (BlockPosition member : network) {
            results.remove(member);
            networks.remove(member);
        }
    }
}
