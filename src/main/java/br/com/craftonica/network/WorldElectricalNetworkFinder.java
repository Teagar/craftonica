package br.com.craftonica.network;

import br.com.craftonica.block.IElectricalBlock;
import br.com.craftonica.electrical.SimpleCircuitSolver;
import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.ArrayList;
import java.util.List;

public final class WorldElectricalNetworkFinder {
    private final BoundedNetworkSearch<BlockPosition> search =
            new BoundedNetworkSearch<BlockPosition>(SimpleCircuitSolver.MAX_NETWORK_SIZE);

    public BoundedNetworkSearch.Result<BlockPosition> discover(final World world, BlockPosition start) {
        return search.discover(start, new BoundedNetworkSearch.NeighborProvider<BlockPosition>() {
            @Override
            public Iterable<BlockPosition> neighbors(BlockPosition position) {
                return connectedNeighbors(world, position);
            }
        });
    }

    public List<BlockPosition> connectedNeighbors(World world, BlockPosition position) {
        List<BlockPosition> result = new ArrayList<BlockPosition>();
        Block current = world.getBlock(position.x, position.y, position.z);
        if (!(current instanceof IElectricalBlock)) {
            return result;
        }
        IElectricalBlock electrical = (IElectricalBlock) current;
        for (ForgeDirection direction : ForgeDirection.VALID_DIRECTIONS) {
            if (!electrical.canConnectOnSide(world, position.x, position.y, position.z, direction.ordinal())) {
                continue;
            }
            int x = position.x + direction.offsetX;
            int y = position.y + direction.offsetY;
            int z = position.z + direction.offsetZ;
            if (!world.blockExists(x, y, z)) {
                continue;
            }
            Block neighbor = world.getBlock(x, y, z);
            if (neighbor instanceof IElectricalBlock
                    && ((IElectricalBlock) neighbor).canConnectOnSide(world, x, y, z, direction.getOpposite().ordinal())) {
                result.add(new BlockPosition(x, y, z));
            }
        }
        return result;
    }
}
