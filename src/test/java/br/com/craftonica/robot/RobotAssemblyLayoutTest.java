package br.com.craftonica.robot;

import org.junit.Test;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import static org.junit.Assert.*;

public final class RobotAssemblyLayoutTest {
    @Test public void layoutHasEightUniqueBoundedSlotsInEveryFacing() {
        for (int facing = 2; facing <= 5; facing++) {
            List<RobotAssemblyLayout.Slot> slots = RobotAssemblyLayout.slots(facing);
            assertEquals(8, slots.size());
            Set<String> positions = new HashSet<String>();
            for (RobotAssemblyLayout.Slot slot : slots) {
                assertTrue(positions.add(slot.dx + ":" + slot.dy + ":" + slot.dz));
                assertTrue(Math.abs(slot.dx) <= 1 && slot.dy >= 0 && slot.dy <= 1 && Math.abs(slot.dz) <= 1);
            }
        }
    }

    @Test public void sensorAlwaysOccupiesTheFrontSlot() {
        RobotAssemblyLayout.Slot north = RobotAssemblyLayout.slots(2).get(4);
        RobotAssemblyLayout.Slot east = RobotAssemblyLayout.slots(5).get(4);
        assertEquals(-1, north.dz); assertEquals(0, north.dx);
        assertEquals(1, east.dx); assertEquals(0, east.dz);
    }

    @Test public void orientationContractRotatesMotorsAndPowerTerminals() {
        for (int facing = 2; facing <= 5; facing++) {
            List<RobotAssemblyLayout.Slot> slots = RobotAssemblyLayout.slots(facing);
            assertEquals(facing, RobotAssemblyService.expectedMetadata(slots.get(0), facing));
            assertEquals(facing == 2 || facing == 3 ? 1 : 0,
                    RobotAssemblyService.expectedMetadata(slots.get(1), facing));
            RobotAssemblyLayout.Slot power = slots.get(6);
            assertEquals(RobotAssemblyService.sideForVector(-power.dx, -power.dz),
                    RobotAssemblyService.expectedMetadata(power, facing));
        }
    }

    @Test public void cardinalFacingAndYawRoundTrip() {
        for (int facing = 2; facing <= 5; facing++)
            assertEquals(facing, RobotAssemblyService.facingForYaw(
                    RobotAssemblyService.yawForFacing(facing)));
    }

    @Test public void onlyCompleteManifestCanBecomePhysicalBlocks() {
        MobileRobotState complete = MobileRobotState.assembled(new UUID(1, 2), new UUID(3, 4), 3,
                new br.com.craftonica.tile.RoboBoardState(new UUID(1, 2)));
        assertTrue(complete.hasPhysicalAssemblyManifest());
        assertFalse(MobileRobotState.minimal(new UUID(1, 2), new UUID(3, 4)).hasPhysicalAssemblyManifest());
    }

    @Test public void templateSketchSurvivesMobileStatePersistence() {
        byte[] source = "void setup() {}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MobileRobotState state = MobileRobotState.assembled(new UUID(1, 2), new UUID(3, 4), 3,
                new br.com.craftonica.tile.RoboBoardState(new UUID(1, 2)), source);
        assertArrayEquals(source, MobileRobotState.read(state.write()).getBoardTemplateSketch());
    }
}
