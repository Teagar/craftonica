package br.com.craftonica.electrical.nodal.forge;

import br.com.craftonica.block.*;
import br.com.craftonica.electrical.nodal.*;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.tile.TileEntityLed;
import net.minecraft.block.Block;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class ForgeNodalSnapshotExtractorTest {
    @Test public void mapsMvpComponentsAndOrientationIntoTopology() {
        FakeWorld world = new FakeWorld();
        world.put(0, 0, 0, new BlockPowerSource(), 5);
        world.put(1, 0, 0, new BlockResistor("r", "craftonica:r", 220.0), 1);
        world.put(2, 0, 0, new BlockElectricalButton(), 1 | 2);
        world.put(3, 0, 0, new BlockLed(), 5);
        world.put(4, 0, 0, new BlockGround(), 4);

        NodalExtractionResult result = new ForgeNodalSnapshotExtractor(world).extract(new BlockPosition(0, 0, 0));
        assertTrue(result.isComplete());
        assertEquals(5, result.getSnapshots().size());
        ComponentSnapshot source = result.getSnapshots().get(0);
        assertEquals("source", source.getKind());
        assertEquals(Face.EAST, source.getTerminals().get(0).getId().getFace());
        ComponentSnapshot led = find(result, "led");
        assertEquals(Face.EAST, led.getTerminals().get(0).getId().getFace());
        assertEquals(Face.WEST, led.getTerminals().get(1).getId().getFace());
        assertEquals(Double.valueOf(220.0), find(result, "resistor").getParameters().get("resistance"));
        assertEquals("true", find(result, "switch").getState().get("closed"));
        NodalCircuitBuilder builder = new NodalCircuitBuilder();
        for (ComponentSnapshot snapshot : result.getSnapshots()) builder.add(snapshot);
        NodalCircuit circuit = builder.build();
        assertEquals(5, circuit.getSnapshots().size());
        assertEquals(3, circuit.getBranches().size());
        ComponentSnapshot closedSwitch = find(result, "switch");
        assertNotEquals(circuit.getNode(closedSwitch.getTerminals().get(0).getId()),
                circuit.getNode(closedSwitch.getTerminals().get(1).getId()));
        assertTrue(closedSwitch.getConductorGroups().isEmpty());
        assertTrue(circuit.getTopologyFingerprint().length() > 0);
    }

    @Test public void preservesBurnedLedStateFromTileEntity() {
        FakeWorld world = new FakeWorld();
        BlockPosition position = new BlockPosition(0, 0, 0);
        world.put(position.x, position.y, position.z, new BlockLed(), 5);
        TileEntityLed led = new TileEntityLed();
        NBTTagCompound tag = new NBTTagCompound();
        tag.setBoolean("Burned", true);
        led.readFromNBT(tag);
        world.tiles.put(position, led);

        ComponentSnapshot snapshot = new ForgeNodalSnapshotExtractor(world).extract(position).getSnapshots().get(0);
        assertEquals("true", snapshot.getState().get("burned"));
    }

    @Test public void reportsUnloadedChunkWithoutReadingIt() {
        FakeWorld world = new FakeWorld();
        world.put(15, 0, 0, new BlockElectricalWire(), 0);
        world.loadedChunks.remove("1,0");
        NodalExtractionResult result = new ForgeNodalSnapshotExtractor(world).extract(new BlockPosition(15, 0, 0));
        assertFalse(result.isComplete());
        assertEquals(DiagnosticCode.INCOMPLETE_NETWORK, result.getDiagnostics().get(0).getCode());
        assertEquals(0, world.readsAtUnloadedChunk);
    }

    @Test public void rejectsInvalidMetadataAndComponentParameters() {
        FakeWorld world = new FakeWorld();
        BlockPosition position = new BlockPosition(0, 0, 0);
        world.put(position.x, position.y, position.z, new BlockResistor("bad", "craftonica:bad", -1.0), 1);
        NodalExtractionResult badResistance = new ForgeNodalSnapshotExtractor(world).extract(position);
        assertEquals(DiagnosticCode.INVALID_COMPONENT_DATA, badResistance.getDiagnostics().get(0).getCode());

        world.put(position.x, position.y, position.z, new BlockPowerSource(), 7);
        NodalExtractionResult badFace = new ForgeNodalSnapshotExtractor(world).extract(position);
        assertEquals(DiagnosticCode.INVALID_COMPONENT_DATA, badFace.getDiagnostics().get(0).getCode());
    }

    private ComponentSnapshot find(NodalExtractionResult result, String kind) {
        for (ComponentSnapshot snapshot : result.getSnapshots()) if (kind.equals(snapshot.getKind())) return snapshot;
        throw new AssertionError(kind);
    }

    private static final class FakeWorld implements ForgeWorldAccess {
        private final Map<BlockPosition, Block> blocks = new HashMap<BlockPosition, Block>();
        private final Map<BlockPosition, Integer> metadata = new HashMap<BlockPosition, Integer>();
        private final Map<BlockPosition, TileEntity> tiles = new HashMap<BlockPosition, TileEntity>();
        private final Set<String> loadedChunks = new HashSet<String>(Arrays.asList("0,0", "-1,0", "0,-1", "0,1", "-1,-1", "-1,1", "1,-1"));
        private int readsAtUnloadedChunk;

        void put(int x, int y, int z, Block block, int data) {
            BlockPosition position = new BlockPosition(x, y, z);
            blocks.put(position, block); metadata.put(position, data);
        }
        public boolean isChunkLoaded(int x, int z) { return loadedChunks.contains(x + "," + z); }
        public Block getBlock(BlockPosition p) {
            if (!isChunkLoaded(p.x >> 4, p.z >> 4)) readsAtUnloadedChunk++;
            return blocks.get(p);
        }
        public int getMetadata(BlockPosition p) { return metadata.containsKey(p) ? metadata.get(p) : 0; }
        public TileEntity getTileEntity(BlockPosition p) { return tiles.get(p); }
        public boolean canConnectOnSide(Block block, BlockPosition p, int side) {
            if (block instanceof BlockElectricalWire) return true;
            if (block instanceof BlockSingleTerminal || block instanceof BlockLed) return (getMetadata(p) & 7) == side
                    || block instanceof BlockLed && ((getMetadata(p) & 7) == opposite(side));
            if (block instanceof BlockTwoTerminal) return (getMetadata(p) & 1) == 0 ? side == 2 || side == 3 : side == 4 || side == 5;
            return false;
        }
        private int opposite(int side) { return side == 0 ? 1 : side == 1 ? 0 : side == 2 ? 3 : side == 3 ? 2 : side == 4 ? 5 : 4; }
    }
}
