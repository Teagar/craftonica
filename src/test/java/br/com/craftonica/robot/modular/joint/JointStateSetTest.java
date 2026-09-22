package br.com.craftonica.robot.modular.joint;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.assembly.KinematicAssembly;
import br.com.craftonica.robot.modular.assembly.KinematicAssemblyAnalyzer;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.*;

public final class JointStateSetTest {
    @Test public void initialStateRoundTripsThroughNbtAndBoundedClientProjection() {
        KinematicAssembly assembly = KinematicAssemblyAnalyzer.analyze(manifest(), StandardComponentCatalog.create());
        JointStateSet initial = JointStateSet.initial(assembly);
        assertEquals(1, initial.getEntries().size());
        assertEquals(0.0, initial.getEntries().get(0).state.position, 0.0);

        JointStateSet persisted = JointStateSet.read(initial.write(), assembly);
        assertEquals(new GridVector(0, 0, 1), persisted.getEntries().get(0).position);
        ByteBuf bytes = Unpooled.buffer(); persisted.writeClient(bytes);
        JointStateSet client = JointStateSet.readClient(bytes);
        assertEquals(1, client.getEntries().size());
        assertEquals(0, bytes.readableBytes());
    }

    @Test public void rejectsCoordinatesOutsideJointContractAndTrailingClientBytes() {
        KinematicAssembly assembly = KinematicAssemblyAnalyzer.analyze(manifest(), StandardComponentCatalog.create());
        NBTTagCompound invalid = JointStateSet.initial(assembly).write();
        invalid.getTagList("Values", 10).getCompoundTagAt(0).setDouble("Position", 100.0);
        try { JointStateSet.read(invalid, assembly); fail("out-of-range coordinate accepted"); }
        catch (IllegalArgumentException expected) { }

        ByteBuf bytes = Unpooled.buffer(); JointStateSet.initial(assembly).writeClient(bytes); bytes.writeByte(1);
        try { JointStateSet.readClient(bytes); fail("trailing client state accepted"); }
        catch (IllegalArgumentException expected) { }
    }

    private static ModularRobotManifest manifest() {
        GridVector parent = new GridVector(0, 0, 0), joint = new GridVector(0, 0, 1), child = new GridVector(0, 0, 2);
        return new ModularRobotManifest(new UUID(80, 81), Arrays.asList(
                module(StandardComponentCatalog.CHASSIS, parent),
                module(StandardComponentCatalog.REVOLUTE_JOINT, joint),
                module(StandardComponentCatalog.CHASSIS, child)), Arrays.asList(
                new AssemblyEdge(AssemblyEdge.Kind.JOINT, parent, "mount_south", joint, "parent"),
                new AssemblyEdge(AssemblyEdge.Kind.JOINT, joint, "child", child, "mount_north")));
    }
    private static ModularBlockSnapshot module(String type, GridVector position) {
        return new ModularBlockSnapshot(type, 1, position, ComponentOrientation.NORTH_UP, type, 0, null);
    }
}
