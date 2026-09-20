package br.com.craftonica.showcase;

import br.com.craftonica.registry.ModBlocks;
import br.com.craftonica.sensor.AcousticMaterialProfile;
import br.com.craftonica.sensor.UltrasonicMeasurementModel;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public final class RobotArenaLayoutTest {
    @Test public void deterministicPlanHasBoundaryMaterialsBaysAndSmallTargets() {
        RobotArenaLayout first = new RobotArenaLayout(), second = new RobotArenaLayout();
        assertEquals(signature(first), signature(second));
        int mdf = 0, plastic = 0, absorbent = 0, targets = 0;
        for (int x = 0; x < RobotArenaLayout.LOGICAL_SIZE; x++)
            for (int z = 0; z < RobotArenaLayout.LOGICAL_SIZE; z++) {
                RobotArenaLayout.Cell cell = first.cell(x, z);
                if (x == 0 || z == 0 || x == RobotArenaLayout.LOGICAL_SIZE - 1
                        || z == RobotArenaLayout.LOGICAL_SIZE - 1) {
                    boolean exit = x == RobotArenaLayout.LOGICAL_SIZE - 1
                            && z == RobotArenaLayout.LOGICAL_SIZE - 2;
                    assertEquals(exit ? RobotArenaLayout.Cell.EXIT : RobotArenaLayout.Cell.WALL, cell);
                }
                if (cell == RobotArenaLayout.Cell.WALL) {
                    RobotArenaLayout.WallMaterial material = first.wallMaterial(x, z);
                    if (material == RobotArenaLayout.WallMaterial.MDF) mdf++;
                    if (material == RobotArenaLayout.WallMaterial.RIGID_PLASTIC) plastic++;
                    if (material == RobotArenaLayout.WallMaterial.ABSORBENT) absorbent++;
                } else if (cell == RobotArenaLayout.Cell.SMALL_MDF
                        || cell == RobotArenaLayout.Cell.SMALL_FOAM) targets++;
            }
        assertTrue(mdf > 0); assertTrue(plastic > 0); assertTrue(absorbent > 0);
        assertEquals(4, targets);
        assertEquals(RobotArenaLayout.Cell.START, first.cell(1, 1));
        assertEquals(RobotArenaLayout.Cell.RECOVERY,
                first.cell(RobotArenaLayout.LOGICAL_SIZE - 2, RobotArenaLayout.LOGICAL_SIZE - 2));
        assertEquals(RobotArenaLayout.Cell.EXIT,
                first.cell(RobotArenaLayout.LOGICAL_SIZE - 1, RobotArenaLayout.LOGICAL_SIZE - 2));
        assertEquals(51, RobotArenaLayout.WIDTH); assertEquals(3, RobotArenaLayout.SCALE);
    }

    @Test public void everyPassageIsConnectedAndMazeContainsTurnsAndDeadEnds() {
        RobotArenaLayout layout = new RobotArenaLayout();
        Set<String> open = new HashSet<String>();
        int turns = 0, targets = 0;
        for (int x = 0; x < RobotArenaLayout.LOGICAL_SIZE; x++)
            for (int z = 0; z < RobotArenaLayout.LOGICAL_SIZE; z++) if (layout.cell(x, z) != RobotArenaLayout.Cell.WALL) {
                open.add(key(x, z));
                boolean horizontal = passable(layout, x - 1, z) || passable(layout, x + 1, z);
                boolean vertical = passable(layout, x, z - 1) || passable(layout, x, z + 1);
                if (horizontal && vertical) turns++;
                if (layout.cell(x, z) == RobotArenaLayout.Cell.SMALL_MDF
                        || layout.cell(x, z) == RobotArenaLayout.Cell.SMALL_FOAM) targets++;
            }
        Set<String> reached = new HashSet<String>(); Queue<int[]> queue = new ArrayDeque<int[]>();
        queue.add(new int[] { 1, 1 }); reached.add(key(1, 1));
        int[][] directions = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        while (!queue.isEmpty()) {
            int[] point = queue.remove();
            for (int[] direction : directions) {
                int x = point[0] + direction[0], z = point[1] + direction[1]; String key = key(x, z);
                if (passable(layout, x, z) && reached.add(key)) queue.add(new int[] { x, z });
            }
        }
        assertEquals(open, reached);
        assertTrue(turns >= 12); assertEquals(4, targets);
    }

    @Test public void customSmallTargetsKeepTheirAcousticProfilesInWorldMode() {
        assertEquals(AcousticMaterialProfile.MDF, AcousticMaterialProfile.forBlock(ModBlocks.TARGET_MDF));
        assertEquals(AcousticMaterialProfile.FOAM, AcousticMaterialProfile.forBlock(ModBlocks.TARGET_FOAM));
    }

    @Test public void rigidAndAbsorbentArenaRegionsProduceMeasurablyDifferentEchoes() {
        double plasticError = 0.0, absorbentError = 0.0; int plasticValid = 0, absorbentValid = 0;
        for (long seed = 0; seed < 1000; seed++) {
            UltrasonicMeasurementModel.Measurement plastic = UltrasonicMeasurementModel.measure(
                    150.0, 0.0, AcousticMaterialProfile.RIGID_PLASTIC, seed);
            UltrasonicMeasurementModel.Measurement absorbent = UltrasonicMeasurementModel.measure(
                    150.0, 0.0, AcousticMaterialProfile.ABSORBENT_WORLD, seed);
            if (plastic.echo) { plasticValid++; plasticError += StrictMath.abs(plastic.measuredCentimeters - 150.0); }
            if (absorbent.echo) { absorbentValid++; absorbentError += StrictMath.abs(absorbent.measuredCentimeters - 150.0); }
        }
        assertTrue(plasticValid > absorbentValid);
        assertTrue(absorbentError / absorbentValid > plasticError / plasticValid * 4.0);
    }

    @Test public void completeBlueprintIsIdempotentAndNeverAddressesOutsideReservedVolume() {
        RobotArenaBlueprint blueprint = new RobotArenaBlueprint();
        Map<String, String> world = new HashMap<String, String>();
        world.put(key(-1, 0), "SENTINEL_WEST");
        world.put(key(RobotArenaLayout.WIDTH, 0), "SENTINEL_EAST");
        apply(world, blueprint); Map<String, String> once = new HashMap<String, String>(world);
        apply(world, blueprint);
        assertEquals(once, world);
        assertEquals("SENTINEL_WEST", world.get(key(-1, 0)));
        assertEquals("SENTINEL_EAST", world.get(key(RobotArenaLayout.WIDTH, 0)));
        assertEquals(RobotArenaLayout.WIDTH * RobotArenaLayout.DEPTH
                        * (RobotArenaBlueprint.CLEAR_HEIGHT + 2) + 2, world.size());
    }

    @Test public void arenaOriginPersistsAcrossWorldRestart() {
        RobotArenaData original = new RobotArenaData();
        original.setOrigin(0, 120, 64, -80);
        original.setOrigin(-1, 10, 40, 20);
        NBTTagCompound tag = new NBTTagCompound(); original.writeToNBT(tag);
        RobotArenaData restored = new RobotArenaData(); restored.readFromNBT(tag);
        assertEquals(120, restored.getOrigin(0).x);
        assertEquals(64, restored.getOrigin(0).y);
        assertEquals(-80, restored.getOrigin(0).z);
        assertEquals(10, restored.getOrigin(-1).x);
    }

    @Test public void documentedFrontSensorPolicyReachesExitWithinBound() {
        RobotArenaLayout layout = new RobotArenaLayout();
        int x = 1, z = 1, heading = 1; // yaw 0: south
        int[][] directions = { { 1, 0 }, { 0, 1 }, { -1, 0 }, { 0, -1 } };
        int steps;
        for (steps = 0; steps < 4096 && layout.cell(x, z) != RobotArenaLayout.Cell.EXIT; steps++) {
            int forward = heading;
            if (!navigable(layout, x + directions[forward][0], z + directions[forward][1])) {
                int right = (heading + 1) & 3, left = (heading + 3) & 3;
                if (navigable(layout, x + directions[right][0], z + directions[right][1])) heading = right;
                else if (navigable(layout, x + directions[left][0], z + directions[left][1])) heading = left;
                else heading = (heading + 2) & 3;
            }
            x += directions[heading][0]; z += directions[heading][1];
        }
        assertEquals("stopped at " + x + "," + z + " heading " + heading + " after " + steps,
                RobotArenaLayout.Cell.EXIT, layout.cell(x, z));
        assertTrue(steps <= 1024);
    }

    @Test public void unsupportedArenaSchemaRestoresNoOrigin() {
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("Schema", 99);
        RobotArenaData restored = new RobotArenaData(); restored.readFromNBT(tag);
        assertNull(restored.getOrigin(0));
    }

    @Test public void regenerationProtectsRobotOnlyWhenFutureGeometryWouldTrapIt() {
        RobotArenaLayout layout = new RobotArenaLayout();
        int center = RobotArenaLayout.blockCenter(1);
        assertTrue(RobotArenaGenerator.robotFootprintCompatible(
                AxisAlignedBB.getBoundingBox(center - 0.8, 0, center - 0.8,
                        center + 0.8, 1, center + 0.8), 0, 0, layout));
        assertFalse(RobotArenaGenerator.robotFootprintCompatible(
                AxisAlignedBB.getBoundingBox(0.1, 0, center - 0.8,
                        1.9, 1, center + 0.8), 0, 0, layout));
    }

    private void apply(Map<String, String> world, RobotArenaBlueprint blueprint) {
        for (int x = 0; x < RobotArenaLayout.WIDTH; x++)
            for (int z = 0; z < RobotArenaLayout.DEPTH; z++)
                for (int y = -1; y <= RobotArenaBlueprint.CLEAR_HEIGHT; y++)
                    world.put(x + ":" + y + ":" + z, blueprint.voxel(x, y, z).name());
    }

    private boolean passable(RobotArenaLayout layout, int x, int z) {
        return x >= 0 && z >= 0 && x < RobotArenaLayout.LOGICAL_SIZE && z < RobotArenaLayout.LOGICAL_SIZE
                && layout.cell(x, z) != RobotArenaLayout.Cell.WALL;
    }
    private boolean navigable(RobotArenaLayout layout, int x, int z) {
        if (!passable(layout, x, z)) return false;
        RobotArenaLayout.Cell cell = layout.cell(x, z);
        return cell != RobotArenaLayout.Cell.SMALL_MDF && cell != RobotArenaLayout.Cell.SMALL_FOAM;
    }
    private String signature(RobotArenaLayout layout) {
        StringBuilder value = new StringBuilder();
        for (int z = 0; z < RobotArenaLayout.LOGICAL_SIZE; z++)
            for (int x = 0; x < RobotArenaLayout.LOGICAL_SIZE; x++)
                value.append((char) ('A' + layout.cell(x, z).ordinal()));
        return value.toString();
    }
    private String key(int x, int z) { return x + ":" + z; }
}
