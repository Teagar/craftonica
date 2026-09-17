package br.com.craftonica.electrical.nodal.forge;

import br.com.craftonica.block.*;
import br.com.craftonica.electrical.nodal.*;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.tile.TileEntityLed;
import br.com.craftonica.tile.TileEntityCircuitBreaker;
import br.com.craftonica.tile.TileEntityElectricalLever;
import br.com.craftonica.tile.TileEntityPotentiometer;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import java.util.*;

/** Extracts one connected electrical component set without loading chunks. */
public final class ForgeNodalSnapshotExtractor {
    private static final int MAX_BLOCKS = 1024;
    private final ForgeWorldAccess world;

    public ForgeNodalSnapshotExtractor(ForgeWorldAccess world) {
        if (world == null) throw new IllegalArgumentException("Acesso nulo");
        this.world = world;
    }

    public NodalExtractionResult extract(BlockPosition origin) {
        List<ComponentSnapshot> snapshots = new ArrayList<ComponentSnapshot>();
        List<CircuitDiagnostic> diagnostics = new ArrayList<CircuitDiagnostic>();
        if (!loaded(origin)) return result(snapshots, incomplete(origin));
        if (!(world.getBlock(origin) instanceof IElectricalBlock)) return result(snapshots, diagnostics);

        Queue<BlockPosition> pending = new ArrayDeque<BlockPosition>();
        Set<BlockPosition> visited = new HashSet<BlockPosition>();
        pending.add(origin);
        while (!pending.isEmpty()) {
            BlockPosition position = pending.remove();
            if (!visited.add(position)) continue;
            if (visited.size() > MAX_BLOCKS) {
                diagnostics.add(new CircuitDiagnostic(DiagnosticCode.NETWORK_TOO_LARGE,
                        CircuitDiagnostic.Severity.ERROR, Collections.singletonList(position)));
                break;
            }
            if (!loaded(position)) {
                diagnostics.add(incomplete(position));
                continue;
            }
            Block block = world.getBlock(position);
            if (!(block instanceof IElectricalBlock)) continue;
            try {
                ComponentSnapshot extracted = snapshot(position, block);
                snapshots.add(extracted);
                if (!validParameters(extracted)) {
                    diagnostics.add(new CircuitDiagnostic(DiagnosticCode.INVALID_COMPONENT_DATA,
                            CircuitDiagnostic.Severity.ERROR, Collections.singletonList(position)));
                }
            } catch (IllegalArgumentException invalidMetadata) {
                diagnostics.add(new CircuitDiagnostic(DiagnosticCode.INVALID_COMPONENT_DATA,
                        CircuitDiagnostic.Severity.ERROR, Collections.singletonList(position)));
            }
            for (int side = 0; side < 6; side++) {
                if (!world.canConnectOnSide(block, position, side)) continue;
                BlockPosition neighbor = offset(position, side);
                if (!loaded(neighbor)) {
                    diagnostics.add(incomplete(neighbor));
                    continue;
                }
                Block neighborBlock = world.getBlock(neighbor);
                if (neighborBlock instanceof IElectricalBlock
                        && world.canConnectOnSide(neighborBlock, neighbor, opposite(side))) {
                    pending.add(neighbor);
                }
            }
        }
        Collections.sort(diagnostics);
        return result(snapshots, diagnostics);
    }

    private NodalExtractionResult result(List<ComponentSnapshot> s, CircuitDiagnostic d) {
        return result(s, Collections.singletonList(d));
    }
    private NodalExtractionResult result(List<ComponentSnapshot> s, List<CircuitDiagnostic> d) {
        return new NodalExtractionResult(s, d);
    }
    private boolean loaded(BlockPosition p) { return world.isChunkLoaded(p.x >> 4, p.z >> 4); }
    private CircuitDiagnostic incomplete(BlockPosition p) {
        return new CircuitDiagnostic(DiagnosticCode.INCOMPLETE_NETWORK,
                CircuitDiagnostic.Severity.ERROR, Collections.singletonList(p));
    }

    private boolean validParameters(ComponentSnapshot snapshot) {
        for (Map.Entry<String, Double> entry : snapshot.getParameters().entrySet()) {
            Double value = entry.getValue();
            if (value == null || value.isNaN() || value.isInfinite()) return false;
            if ("resistance".equals(entry.getKey()) && value.doubleValue() <= 0.0) return false;
        }
        return true;
    }

    private ComponentSnapshot snapshot(BlockPosition p, Block block) {
        int metadata = world.getMetadata(p);
        List<TerminalSnapshot> terminals = terminals(p, block, metadata);
        Map<String, Double> parameters = new HashMap<String, Double>();
        Map<String, String> state = new HashMap<String, String>();
        List<int[]> groups = Collections.emptyList();
        String kind;
        if (block instanceof BlockElectricalWire) {
            kind = "wire";
            groups = Collections.singletonList(new int[]{0, 1, 2, 3, 4, 5});
        } else if (block instanceof BlockPowerSource) {
            kind = "source";
            parameters.put("voltage", br.com.craftonica.block.BlockPowerSource.VOLTAGE);
            parameters.put("internalResistance", br.com.craftonica.block.BlockPowerSource.INTERNAL_RESISTANCE_OHMS);
        } else if (block instanceof BlockGround) {
            kind = "ground";
        } else if (block instanceof BlockElectricalButton) {
            kind = "switch";
            state.put("closed", Boolean.toString((metadata & 2) != 0));
            if ((metadata & 2) != 0) groups = Collections.singletonList(new int[]{0, 1});
        } else if (block instanceof BlockElectricalLever) {
            kind = "switch";
            TileEntity tile = world.getTileEntity(p);
            boolean closed = tile instanceof TileEntityElectricalLever
                    ? ((TileEntityElectricalLever) tile).isClosed() : (metadata & 2) != 0;
            state.put("closed", Boolean.toString(closed));
            if (closed) groups = Collections.singletonList(new int[]{0, 1});
        } else if (block instanceof BlockCircuitBreaker) {
            kind = "breaker";
            TileEntity tile = world.getTileEntity(p);
            state.put("closed", Boolean.toString(!(tile instanceof TileEntityCircuitBreaker)
                    || !((TileEntityCircuitBreaker) tile).isTripped()));
        } else if (block instanceof BlockResistor) {
            kind = "resistor";
            parameters.put("resistance", ((BlockResistor) block).getResistanceOhms());
        } else if (block instanceof BlockPotentiometer) {
            kind = "potentiometer";
            TileEntity tile = world.getTileEntity(p);
            int step = tile instanceof TileEntityPotentiometer ? ((TileEntityPotentiometer) tile).getCursorStep() : 50;
            parameters.put("nominalResistance", PotentiometerModel.NOMINAL_OHMS);
            parameters.put("resistanceA", PotentiometerModel.resistanceA(step));
            parameters.put("resistanceB", PotentiometerModel.resistanceB(step));
            state.put("cursorStep", Integer.toString(step));
            state.put("stateVersion", Integer.toString(tile instanceof TileEntityPotentiometer
                    ? ((TileEntityPotentiometer) tile).getStateVersion() : TileEntityPotentiometer.STATE_VERSION));
        } else if (block instanceof BlockLed) {
            kind = "led";
            parameters.put("forwardVoltage", 2.0);
            TileEntity tile = world.getTileEntity(p);
            state.put("burned", Boolean.toString(tile instanceof TileEntityLed && ((TileEntityLed) tile).isBurned()));
        } else if (block instanceof BlockDiode) {
            kind = "diode";
            parameters.put("forwardVoltage", DcComponentParameters.DIODE_FORWARD_VOLTAGE);
            parameters.put("dynamicResistance", DcComponentParameters.DIODE_DYNAMIC_RESISTANCE_OHMS);
        } else {
            throw new IllegalArgumentException("Bloco eletrico desconhecido: " + block.getClass().getName());
        }
        return new ComponentSnapshot(p, kind, terminals, parameters, state, groups);
    }

    private List<TerminalSnapshot> terminals(BlockPosition p, Block block, int metadata) {
        List<TerminalSnapshot> terminals = new ArrayList<TerminalSnapshot>();
        if (block instanceof BlockElectricalWire) {
            for (int side = 0; side < 6; side++) terminals.add(new TerminalSnapshot(p, face(side), side));
        } else if (block instanceof BlockSingleTerminal) {
            terminals.add(new TerminalSnapshot(p, face(metadata & 7), 0));
        } else if (block instanceof BlockDiode) {
            int anode = metadata & 7;
            terminals.add(new TerminalSnapshot(p, face(anode), 0));
            terminals.add(new TerminalSnapshot(p, face(opposite(anode)), 1));
        } else if (block instanceof BlockPotentiometer) {
            int axis = metadata & 1;
            int a = axis == 0 ? 2 : 4;
            terminals.add(new TerminalSnapshot(p, face(a), 0));
            terminals.add(new TerminalSnapshot(p, Face.UP, 1));
            terminals.add(new TerminalSnapshot(p, face(opposite(a)), 2));
        } else if (block instanceof BlockTwoTerminal || block instanceof BlockCircuitBreaker) {
            int first = (metadata & 1) == 0 ? 2 : 4;
            terminals.add(new TerminalSnapshot(p, face(first), 0));
            terminals.add(new TerminalSnapshot(p, face(opposite(first)), 1));
        } else if (block instanceof BlockLed) {
            int anode = metadata & 7;
            terminals.add(new TerminalSnapshot(p, face(anode), 0));
            terminals.add(new TerminalSnapshot(p, face(opposite(anode)), 1));
        }
        return terminals;
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
    private int opposite(int side) { return side == 0 ? 1 : side == 1 ? 0 : side == 2 ? 3 : side == 3 ? 2 : side == 4 ? 5 : 4; }
    private Face face(int side) {
        if (side < 0 || side >= Face.values().length) throw new IllegalArgumentException("Face invalida");
        return Face.values()[side];
    }
}
