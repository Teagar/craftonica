package br.com.craftonica.robot.modular.visual;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.electrical.MobileElectricalNetlist;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public final class ModularRobotVisualStateTest {
    @Test public void threeDistinctAssembliesRetainGeometryOrientationAndMaterial() {
        ModularRobotVisualState line = state(module(StandardComponentCatalog.CHASSIS, "craftonica:robot_chassis",
                0,0,0, ComponentOrientation.NORTH_UP, 2));
        ModularRobotVisualState side = state(module(StandardComponentCatalog.WHEEL, "craftonica:robot_wheel",
                2,0,-1, new ComponentOrientation(Direction.EAST, Direction.UP), 5));
        ModularRobotVisualState tower = state(module(StandardComponentCatalog.HC_SR04,
                "craftonica:modular_ultrasonic_sensor", 0,3,0,
                new ComponentOrientation(Direction.SOUTH, Direction.UP), 3));

        assertEquals(new GridVector(0,0,0), line.getModules().get(0).localPosition);
        assertEquals(new GridVector(2,0,-1), side.getModules().get(0).localPosition);
        assertEquals(Direction.EAST, side.getModules().get(0).orientation.getForward());
        assertEquals("craftonica:robot_wheel", side.getModules().get(0).blockRegistryName);
        assertEquals(3, tower.getModules().get(0).localPosition.y);
        assertTrue((side.getModules().get(0).flags & ModularRobotVisualState.FLAG_WHEEL) != 0);
    }

    @Test public void compactRoundTripContainsNoTileOrElectricalPayload() {
        NBTTagCompoundMarker marker = new NBTTagCompoundMarker();
        net.minecraft.nbt.NBTTagCompound tile = new net.minecraft.nbt.NBTTagCompound();
        tile.setString("SecretFirmware", marker.value); tile.setByteArray("Checkpoint", new byte[4096]);
        ModularBlockSnapshot module = new ModularBlockSnapshot(StandardComponentCatalog.ROBO_BOARD, 1,
                new GridVector(1,2,3), ComponentOrientation.NORTH_UP, "craftonica:robo_board", 2, tile);
        ModularRobotVisualState original = state(module); ByteBuf buffer = Unpooled.buffer(); original.write(buffer);
        byte[] encoded = new byte[buffer.readableBytes()]; buffer.getBytes(buffer.readerIndex(), encoded);
        String bytes = new String(encoded, java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(bytes.contains(marker.value)); assertTrue(encoded.length < 128);
        ModularRobotVisualState restored = ModularRobotVisualState.read(buffer);
        assertEquals(original.encodedSize(), encoded.length);
        assertEquals(new GridVector(1,2,3), restored.getModules().get(0).localPosition);
        assertEquals(2, restored.getModules().get(0).metadata);
    }

    @Test public void maximumProfileRemainsInsidePublishedSpawnBudget() {
        List<ModularBlockSnapshot> modules = new ArrayList<ModularBlockSnapshot>();
        for (int index = 0; index < ModularRobotVisualState.MAX_MODULES; index++) {
            int x = index % 16, y = index / 16;
            modules.add(module(StandardComponentCatalog.CHASSIS, "craftonica:robot_chassis",
                    x, y, 0, ComponentOrientation.NORTH_UP, index & 15));
        }
        ModularRobotVisualState state = state(modules); ByteBuf buffer = Unpooled.buffer(); state.write(buffer);
        assertEquals(state.encodedSize(), buffer.readableBytes());
        assertTrue(buffer.readableBytes() <= ModularRobotVisualState.MAX_PAYLOAD_BYTES);
        assertEquals(256, ModularRobotVisualState.read(buffer).getModules().size());
    }

    private static ModularRobotVisualState state(ModularBlockSnapshot module) {
        return state(Collections.singletonList(module));
    }
    private static ModularRobotVisualState state(List<ModularBlockSnapshot> modules) {
        return ModularRobotVisualState.fromManifest(new ModularRobotManifest(UUID.randomUUID(), modules,
                Collections.<AssemblyEdge>emptyList(), MobileElectricalNetlist.EMPTY));
    }
    private static ModularBlockSnapshot module(String type, String block, int x, int y, int z,
            ComponentOrientation orientation, int metadata) {
        return new ModularBlockSnapshot(type, 1, new GridVector(x,y,z), orientation, block, metadata, null);
    }
    private static final class NBTTagCompoundMarker { final String value = "DO_NOT_SYNC_FIRMWARE_81"; }
}
