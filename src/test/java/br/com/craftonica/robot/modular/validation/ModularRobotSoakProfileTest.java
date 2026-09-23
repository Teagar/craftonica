package br.com.craftonica.robot.modular.validation;

import br.com.craftonica.robot.modular.ComponentCatalog;
import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.ModularRobotPersistence;
import br.com.craftonica.robot.modular.ModularRobotState;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.assembly.KinematicAssembly;
import br.com.craftonica.robot.modular.assembly.KinematicAssemblyAnalyzer;
import br.com.craftonica.robot.modular.drive.CoupledDriveLoop;
import br.com.craftonica.robot.modular.joint.JointStateSet;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.physics.AxisAlignedVolume;
import br.com.craftonica.robot.modular.physics.CompoundCollisionProbe;
import br.com.craftonica.robot.modular.physics.RigidBodyProperties;
import br.com.craftonica.robot.modular.physics.TerrestrialRigidBodyModel;
import br.com.craftonica.robot.modular.physics.articulated.ArticulatedMechanism;
import br.com.craftonica.robot.modular.physics.articulated.ArticulatedSolver;
import br.com.craftonica.robot.modular.physics.articulated.ArticulatedTickBudget;
import br.com.craftonica.robot.modular.physics.articulated.RigidTransform3;
import br.com.craftonica.robot.modular.visual.ModularRobotVisualState;
import br.com.craftonica.runtime.server.RuntimeSupervisor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Reproducible fleet soak with measured host cost and deterministic operation budgets. */
public final class ModularRobotSoakProfileTest {
    private static final int MAX_RETAINED_HEAP_BYTES = 256 * 1024 * 1024;
    private static final long MAX_CPU_NANOS_PER_ROBOT_TICK = 2_000_000L;
    private static final long MAX_WALL_NANOS_PER_ROBOT_TICK = 5_000_000L;
    private static final ComponentCatalog CATALOG = StandardComponentCatalog.create();

    @Test public void smallMediumAndLimitFleetsStayInsidePublishedBudgets() throws Exception {
        List<ProfileResult> results = Arrays.asList(run("small", 4, 1000),
                run("medium", 16, 2000), run("limit", 64, 4000));
        for (ProfileResult result : results) {
            long robotTicks = (long) result.robots * result.ticks;
            assertTrue(result.cpuNanos <= robotTicks * MAX_CPU_NANOS_PER_ROBOT_TICK);
            assertTrue(result.wallNanos <= robotTicks * MAX_WALL_NANOS_PER_ROBOT_TICK);
            assertTrue(result.heapGrowthBytes <= MAX_RETAINED_HEAP_BYTES);
            assertTrue(result.maxVisualBytes <= ModularRobotVisualState.MAX_PAYLOAD_BYTES);
            assertTrue(result.twoClientFleetBytes
                    <= 2L * result.robots * ModularRobotVisualState.MAX_PAYLOAD_BYTES);
            assertTrue(result.maxContacts <= 4);
            assertTrue(result.maxCollisionVolumes <= ArticulatedTickBudget.MAX_COLLISION_TESTS_PER_SUBSTEP);
        }
        writeReport(results);
    }

    @Test public void prolongedCollisionUnloadedChunksAndInvalidNbtStayIsolated() {
        ModularRobotManifest manifest = TerrestrialBlueprintFixtures.fourWheel();
        RigidBodyProperties body = RigidBodyProperties.derive(manifest, CATALOG);
        TerrestrialRigidBodyModel.State parked = new TerrestrialRigidBodyModel.State(0, 64, 0, 0, 0, 0, 0, 0);
        CountingWorld blocked = CountingWorld.blocked();
        for (int tick = 0; tick < 20000; tick++)
            assertEquals(CompoundCollisionProbe.Result.BLOCKED,
                    CompoundCollisionProbe.sweep(body, blocked, parked, parked));
        assertTrue(blocked.collisionChecks >= 20000);

        CountingWorld unloaded = CountingWorld.unloaded();
        assertEquals(CompoundCollisionProbe.Result.UNLOADED,
                CompoundCollisionProbe.sweep(body, unloaded, parked, parked));
        assertEquals(0, unloaded.collisionChecks);
        assertEquals(0, unloaded.chunkLoadRequests);

        UUID robotId = new UUID(0x43524c95L, 1L);
        ModularRobotState robot = new ModularRobotState(robotId, new UUID(0x43524c95L, 2L), GridVector.ZERO,
                ComponentOrientation.NORTH_UP, manifest);
        CoupledDriveLoop drive = new CoupledDriveLoop(manifest, CATALOG);
        NBTTagCompound valid = ModularRobotPersistence.write(robot, parked, drive, drive.initialState(), null, 0);
        for (int attempt = 0; attempt < 256; attempt++) {
            NBTTagCompound corrupt = (NBTTagCompound) valid.copy();
            corrupt.getCompoundTag("Payload").setLong("SensorCounter", -1L - attempt);
            try {
                ModularRobotPersistence.read(corrupt, CATALOG);
                fail("tampered NBT accepted");
            } catch (IllegalArgumentException expected) { }
            assertEquals(robotId, ModularRobotPersistence.read(valid, CATALOG).robot.getRobotId());
        }
    }

    private static ProfileResult run(String name, int robots, int ticks) {
        List<SoakRobot> fleet = new ArrayList<SoakRobot>();
        List<ModularRobotManifest> manifests = Arrays.asList(TerrestrialBlueprintFixtures.twoWheelCaster(),
                TerrestrialBlueprintFixtures.fourWheel(), AdvancedMechanismFixtures.trackedBase(),
                AdvancedMechanismFixtures.mecanumBase(), AdvancedMechanismFixtures.armWithGripper(),
                AdvancedMechanismFixtures.linearMechanism());
        int maxVisual = 0, maxContacts = 0, maxVolumes = 0; long fleetBytes = 0;
        for (int i = 0; i < robots; i++) {
            SoakRobot robot = new SoakRobot(manifests.get(i % manifests.size())); fleet.add(robot);
            maxVisual = Math.max(maxVisual, robot.visualBytes); fleetBytes += robot.visualBytes;
            maxContacts = Math.max(maxContacts, robot.contacts); maxVolumes = Math.max(maxVolumes, robot.volumes);
        }

        forceGc(); long heapBefore = usedHeap(), gcBefore = gcCollections();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        boolean cpuSupported = threads.isCurrentThreadCpuTimeSupported();
        long cpuBefore = cpuSupported ? threads.getCurrentThreadCpuTime() : 0L;
        long wallBefore = System.nanoTime(); CountingWorld clear = CountingWorld.clear();
        for (int tick = 0; tick < ticks; tick++) for (SoakRobot robot : fleet) robot.step(clear);
        long wallNanos = System.nanoTime() - wallBefore;
        long cpuNanos = cpuSupported ? threads.getCurrentThreadCpuTime() - cpuBefore : wallNanos;
        forceGc(); long heapGrowth = Math.max(0L, usedHeap() - heapBefore), selfCollisionStops = 0L;
        for (SoakRobot robot : fleet) selfCollisionStops += robot.selfCollisionStops;
        return new ProfileResult(name, robots, ticks, cpuNanos, wallNanos, heapGrowth,
                gcCollections() - gcBefore, maxVisual, fleetBytes * 2L, maxContacts, maxVolumes,
                clear.loadedChecks, clear.collisionChecks, selfCollisionStops);
    }

    private static void writeReport(List<ProfileResult> results) throws Exception {
        String path = System.getProperty("craftonica.soak.report");
        if (path == null || path.length() == 0) return;
        File report = new File(path), parent = report.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IllegalStateException("report directory");
        FileWriter writer = new FileWriter(report);
        try {
            writer.write("Craftonica modular robotics soak profile v1\n");
            writer.write("maxRobotsPerDimensionTick=64\nmaxWorkers=" + RuntimeSupervisor.MAX_WORKERS
                    + "\nmaxQueued=" + RuntimeSupervisor.MAX_QUEUED + "\n");
            writer.write("maxCpuNanosPerRobotTick=" + MAX_CPU_NANOS_PER_ROBOT_TICK
                    + "\nmaxWallNanosPerRobotTick=" + MAX_WALL_NANOS_PER_ROBOT_TICK
                    + "\nmaxRetainedHeapBytes=" + MAX_RETAINED_HEAP_BYTES + "\n");
            for (ProfileResult result : results) writer.write(result.line());
            writer.write("fault.invalidNbtAttempts=256\nfault.prolongedCollisionTicks=20000\n");
            writer.write("fault.unloadedChunkPolicy=stop_without_load\nfault.runtimeBusyCapacity="
                    + (RuntimeSupervisor.MAX_WORKERS + RuntimeSupervisor.MAX_QUEUED) + "\n");
            writer.write("fault.workerReplacement=RuntimeSupervisorIntegrationTest.timeoutKillsTheWorkerAndTheNextSubmissionGetsAReplacement\n");
        } finally { writer.close(); }
    }

    private static long usedHeap() {
        Runtime runtime = Runtime.getRuntime(); return runtime.totalMemory() - runtime.freeMemory();
    }
    private static void forceGc() {
        System.gc(); System.runFinalization();
    }
    private static long gcCollections() {
        long count = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans())
            if (bean.getCollectionCount() > 0) count += bean.getCollectionCount();
        return count;
    }

    private static final class SoakRobot {
        final RigidBodyProperties body;
        final ArticulatedMechanism mechanism;
        TerrestrialRigidBodyModel.State rigidState = new TerrestrialRigidBodyModel.State(0, 64, 0, 0, 0, 0, 0, 0);
        JointStateSet joints;
        long selfCollisionStops;
        final List<Integer> supports;
        final int visualBytes, contacts, volumes;

        SoakRobot(ModularRobotManifest manifest) {
            body = RigidBodyProperties.derive(manifest, CATALOG);
            KinematicAssembly kinematic = KinematicAssemblyAnalyzer.analyze(manifest, CATALOG);
            mechanism = kinematic.getJoints().isEmpty() ? null : new ArticulatedMechanism(manifest, CATALOG);
            joints = mechanism == null ? JointStateSet.EMPTY : JointStateSet.initial(mechanism.getKinematic());
            List<Integer> values = new ArrayList<Integer>();
            for (int i = 0; i < body.getContacts().size(); i++) values.add(Integer.valueOf(i));
            supports = Collections.unmodifiableList(values); contacts = values.size();
            volumes = body.getCollisionVolumes().size();
            ByteBuf buffer = Unpooled.buffer(); ModularRobotVisualState.fromManifest(manifest).write(buffer);
            visualBytes = buffer.readableBytes(); buffer.release();
        }

        void step(CountingWorld world) {
            if (mechanism != null) {
                ArticulatedTickBudget budget = new ArticulatedTickBudget();
                ArticulatedSolver.Result result = ArticulatedSolver.step(mechanism, joints,
                        Collections.<GridVector, Double>emptyMap(), RigidTransform3.identity(), world, budget, 0.025);
                assertTrue(result.status == ArticulatedSolver.Status.ADVANCED
                        || result.status == ArticulatedSolver.Status.SELF_COLLISION);
                if (result.status == ArticulatedSolver.Status.SELF_COLLISION) selfCollisionStops++;
                joints = result.state;
                assertTrue(budget.getBodySteps() <= ArticulatedTickBudget.MAX_BODY_STEPS);
                assertTrue(budget.getJointSteps() <= ArticulatedTickBudget.MAX_JOINT_STEPS);
                assertTrue(budget.getCollisionTests() <= ArticulatedTickBudget.MAX_COLLISION_TESTS);
            } else {
                TerrestrialRigidBodyModel.State next = TerrestrialRigidBodyModel.step(body, rigidState,
                        Collections.<TerrestrialRigidBodyModel.AppliedForce>emptyList(), supports, 0.025);
                assertEquals(CompoundCollisionProbe.Result.CLEAR,
                        CompoundCollisionProbe.sweep(body, world, rigidState, next));
                rigidState = next;
            }
        }
    }

    private static final class CountingWorld implements CompoundCollisionProbe.WorldView {
        final boolean loaded, blocked; long loadedChecks, collisionChecks, chunkLoadRequests;
        private CountingWorld(boolean loaded, boolean blocked) { this.loaded = loaded; this.blocked = blocked; }
        static CountingWorld clear() { return new CountingWorld(true, false); }
        static CountingWorld blocked() { return new CountingWorld(true, true); }
        static CountingWorld unloaded() { return new CountingWorld(false, false); }
        @Override public boolean isLoaded(AxisAlignedVolume volume) { loadedChecks++; return loaded; }
        @Override public boolean collides(AxisAlignedVolume volume) { collisionChecks++; return blocked; }
    }

    private static final class ProfileResult {
        final String name; final int robots, ticks; final long cpuNanos, wallNanos, heapGrowthBytes, gcCollections;
        final int maxVisualBytes, maxContacts, maxCollisionVolumes;
        final long twoClientFleetBytes, loadedChecks, collisionChecks, selfCollisionStops;
        ProfileResult(String name, int robots, int ticks, long cpuNanos, long wallNanos, long heapGrowthBytes,
                long gcCollections, int maxVisualBytes, long twoClientFleetBytes, int maxContacts,
                int maxCollisionVolumes, long loadedChecks, long collisionChecks, long selfCollisionStops) {
            this.name = name; this.robots = robots; this.ticks = ticks; this.cpuNanos = cpuNanos;
            this.wallNanos = wallNanos; this.heapGrowthBytes = heapGrowthBytes; this.gcCollections = gcCollections;
            this.maxVisualBytes = maxVisualBytes; this.twoClientFleetBytes = twoClientFleetBytes;
            this.maxContacts = maxContacts; this.maxCollisionVolumes = maxCollisionVolumes;
            this.loadedChecks = loadedChecks; this.collisionChecks = collisionChecks;
            this.selfCollisionStops = selfCollisionStops;
        }
        String line() {
            return "profile." + name + ".robots=" + robots + " ticks=" + ticks + " cpuNanos=" + cpuNanos
                    + " wallNanos=" + wallNanos + " heapGrowthBytes=" + heapGrowthBytes
                    + " gcCollections=" + gcCollections + " maxVisualBytes=" + maxVisualBytes
                    + " twoClientFleetBytes=" + twoClientFleetBytes + " maxContacts=" + maxContacts
                    + " maxCollisionVolumes=" + maxCollisionVolumes + " loadedChecks=" + loadedChecks
                    + " collisionChecks=" + collisionChecks + " selfCollisionStops=" + selfCollisionStops + "\n";
        }
    }
}
