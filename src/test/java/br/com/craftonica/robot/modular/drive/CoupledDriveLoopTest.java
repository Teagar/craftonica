package br.com.craftonica.robot.modular.drive;

import br.com.craftonica.robot.modular.*;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.electrical.MobileElectricalNetlist;
import br.com.craftonica.robot.modular.electrical.MobileTerminal;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.physics.TerrestrialRigidBodyModel;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public final class CoupledDriveLoopTest {
    @Test public void lockedAndMovingWheelFeedDifferentFiniteCurrentThroughBackEmf() {
        CoupledDriveLoop loop = new CoupledDriveLoop(manifest(), StandardComponentCatalog.create());
        CoupledDriveLoop.ControlFrame frame = frame(0);
        CoupledDriveLoop.Result locked = loop.step(loop.initialState(), frame, body(0.0),
                Collections.singletonList(Integer.valueOf(0)), new SimulationTickBudget(), 0.025);
        CoupledDriveLoop.Result moving = loop.step(loop.initialState(), frame, body(2.5),
                Collections.singletonList(Integer.valueOf(0)), new SimulationTickBudget(), 0.025);
        assertFalse(locked.delayed); assertEquals(1, locked.forces.size());
        assertTrue(locked.state.getChannels().get(0).currentAmps
                > moving.state.getChannels().get(0).currentAmps);
        assertTrue(Double.isFinite(moving.state.getChannels().get(0).currentAmps));
        assertTrue(locked.state.getChannels().get(0).loadTorqueNm > 0.0);
    }

    @Test public void identicalLongTraceIsBitStable() {
        CoupledDriveLoop loop = new CoupledDriveLoop(manifest(), StandardComponentCatalog.create());
        CoupledDriveLoop.State a = loop.initialState(), b = loop.initialState();
        for (int sequence = 0; sequence < 500; sequence++) {
            CoupledDriveLoop.ControlFrame frame = frame(sequence);
            a = loop.step(a, frame, body((sequence % 20) * 0.02), Collections.singletonList(0),
                    new SimulationTickBudget(), 0.025).state;
            b = loop.step(b, frame, body((sequence % 20) * 0.02), Collections.singletonList(0),
                    new SimulationTickBudget(), 0.025).state;
        }
        CoupledDriveLoop.ChannelState ca = a.getChannels().get(0), cb = b.getChannels().get(0);
        assertEquals(a.nextSequence, b.nextSequence);
        assertEquals(Double.doubleToLongBits(ca.currentAmps), Double.doubleToLongBits(cb.currentAmps));
        assertEquals(Double.doubleToLongBits(ca.drive.motorTemperatureCelsius),
                Double.doubleToLongBits(cb.drive.motorTemperatureCelsius));
        assertEquals(Double.doubleToLongBits(ca.loadTorqueNm), Double.doubleToLongBits(cb.loadTorqueNm));
    }

    @Test public void exhaustedBudgetDelaysFrameWithoutSkippingSequenceOrPartialState() {
        CoupledDriveLoop loop = new CoupledDriveLoop(manifest(), StandardComponentCatalog.create());
        CoupledDriveLoop.State initial = loop.initialState(); SimulationTickBudget budget = new SimulationTickBudget();
        assertTrue(budget.reserveFrame(0, 4));
        CoupledDriveLoop.Result delayed = loop.step(initial, frame(0), body(0.0),
                Collections.singletonList(0), budget, 0.025);
        assertTrue(delayed.delayed); assertSame(initial, delayed.state);
        assertEquals(0L, delayed.state.nextSequence); assertTrue(delayed.forces.isEmpty());
        CoupledDriveLoop.Result committed = loop.step(initial, frame(0), body(0.0),
                Collections.singletonList(0), new SimulationTickBudget(), 0.025);
        assertFalse(committed.delayed); assertEquals(1L, committed.state.nextSequence);
    }

    @Test public void electricallyPoweredMotorWithOpenMechanicalPathSpinsButCannotCreateTraction() {
        CoupledDriveLoop loop = new CoupledDriveLoop(manifest(false), StandardComponentCatalog.create());
        CoupledDriveLoop.Result result = loop.step(loop.initialState(), frame(0), body(0.0),
                Collections.singletonList(0), new SimulationTickBudget(), 0.025);
        assertTrue(result.forces.isEmpty());
        assertTrue(result.state.getChannels().get(0).currentAmps > 0.0);
        assertEquals(DriveStep.Diagnostic.OPEN_CIRCUIT,
                result.state.getChannels().get(0).diagnostic);
    }

    @Test public void dynamicChannelStateRoundTripsWithoutResettingSequenceOrTemperature() {
        CoupledDriveLoop loop = new CoupledDriveLoop(manifest(), StandardComponentCatalog.create());
        CoupledDriveLoop.State stepped = loop.step(loop.initialState(), frame(0), body(0.4),
                Collections.singletonList(0), new SimulationTickBudget(), 0.025).state;
        CoupledDriveLoop.State restored = loop.readState(loop.writeState(stepped));
        CoupledDriveLoop.ChannelState expected = stepped.getChannels().get(0);
        CoupledDriveLoop.ChannelState actual = restored.getChannels().get(0);
        assertEquals(stepped.nextSequence, restored.nextSequence);
        assertEquals(Double.doubleToLongBits(expected.drive.angularVelocityRadPerSecond),
                Double.doubleToLongBits(actual.drive.angularVelocityRadPerSecond));
        assertEquals(Double.doubleToLongBits(expected.drive.motorTemperatureCelsius),
                Double.doubleToLongBits(actual.drive.motorTemperatureCelsius));
        assertEquals(Double.doubleToLongBits(expected.loadTorqueNm), Double.doubleToLongBits(actual.loadTorqueNm));
        assertEquals(expected.diagnostic, actual.diagnostic);
    }

    @Test public void reductionChangesRuntimeTorqueDirectionAndMotorFeedback() {
        CoupledDriveLoop direct = new CoupledDriveLoop(manifest(), StandardComponentCatalog.create());
        CoupledDriveLoop reduced = new CoupledDriveLoop(reductionManifest(), StandardComponentCatalog.create());
        CoupledDriveLoop.Result directStep = direct.step(direct.initialState(), frame(0), body(1.0),
                Collections.singletonList(0), new SimulationTickBudget(), 0.025);
        CoupledDriveLoop.Result reducedStep = reduced.step(reduced.initialState(), frame(0), body(1.0),
                Collections.singletonList(0), new SimulationTickBudget(), 0.025);
        assertEquals(1, reducedStep.forces.size());
        assertTrue(directStep.forces.get(0).longitudinalForceNewtons > 0.0);
        assertTrue(reducedStep.forces.get(0).longitudinalForceNewtons < 0.0);
        assertTrue(StrictMath.abs(reducedStep.forces.get(0).longitudinalForceNewtons)
                > StrictMath.abs(directStep.forces.get(0).longitudinalForceNewtons) * 2.5);
        assertTrue(StrictMath.abs(reducedStep.state.getChannels().get(0).drive.angularVelocityRadPerSecond)
                > StrictMath.abs(directStep.state.getChannels().get(0).drive.angularVelocityRadPerSecond));
    }

    private static CoupledDriveLoop.ControlFrame frame(long sequence) {
        Map<GridVector, DriveInput> commands = new HashMap<GridVector, DriveInput>();
        commands.put(p(0,0,0), new DriveInput(true, 5.0, 255, DriveInput.Mode.FORWARD, 0.0));
        return new CoupledDriveLoop.ControlFrame(sequence, commands);
    }
    private static TerrestrialRigidBodyModel.State body(double velocityZ) {
        return new TerrestrialRigidBodyModel.State(0, 0, 0, 0, -velocityZ, 0, 0, 0);
    }
    private static ModularRobotManifest manifest() {
        return manifest(true);
    }
    private static ModularRobotManifest manifest(boolean completeMechanicalPath) {
        GridVector bridge=p(0,0,0), motor=p(3,0,0), axle=p(3,0,1), wheel=p(3,0,2);
        List<ModularBlockSnapshot> modules = Arrays.asList(module(StandardComponentCatalog.H_BRIDGE, bridge,
                        ComponentOrientation.NORTH_UP), module(StandardComponentCatalog.DC_MOTOR, motor,
                        ComponentOrientation.NORTH_UP), module(StandardComponentCatalog.AXLE, axle,
                        ComponentOrientation.NORTH_UP), module(StandardComponentCatalog.WHEEL, wheel,
                        new ComponentOrientation(Direction.EAST, Direction.UP)));
        List<AssemblyEdge> edges = new ArrayList<AssemblyEdge>();
        edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,motor,"shaft",axle,"shaft_in"));
        if (completeMechanicalPath) edges.add(new AssemblyEdge(
                AssemblyEdge.Kind.MECHANICAL,axle,"shaft_out",wheel,"hub"));
        return new ModularRobotManifest(new UUID(11,12), modules, edges, netlist(bridge,motor));
    }
    private static ModularRobotManifest reductionManifest() {
        GridVector bridge=p(0,0,0), motor=p(3,0,0), small=p(3,0,1), large=p(4,0,1);
        GridVector axle=p(4,0,2), wheel=p(4,0,3);
        List<ModularBlockSnapshot> modules = Arrays.asList(module(StandardComponentCatalog.H_BRIDGE, bridge,
                        ComponentOrientation.NORTH_UP), module(StandardComponentCatalog.DC_MOTOR, motor,
                        ComponentOrientation.NORTH_UP), module(StandardComponentCatalog.GEAR_12, small,
                        ComponentOrientation.NORTH_UP), module(StandardComponentCatalog.GEAR_36, large,
                        new ComponentOrientation(Direction.SOUTH, Direction.UP)),
                module(StandardComponentCatalog.AXLE, axle, ComponentOrientation.NORTH_UP),
                module(StandardComponentCatalog.WHEEL, wheel,
                        new ComponentOrientation(Direction.EAST, Direction.UP)));
        List<AssemblyEdge> edges = new ArrayList<AssemblyEdge>();
        edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,motor,"shaft",small,"shaft_in"));
        edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,small,"mesh",large,"mesh"));
        edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,large,"shaft_out",axle,"shaft_in"));
        edges.add(new AssemblyEdge(AssemblyEdge.Kind.MECHANICAL,axle,"shaft_out",wheel,"hub"));
        return new ModularRobotManifest(new UUID(13,14), modules, edges, netlist(bridge,motor));
    }
    private static MobileElectricalNetlist netlist(GridVector bridge, GridVector motor) {
        List<MobileTerminal> t = new ArrayList<MobileTerminal>();
        t.add(term(bridge,StandardComponentCatalog.H_BRIDGE,"vcc",0,""));
        t.add(term(p(0,1,0),StandardComponentCatalog.POWER_SOURCE,"positive",0,""));
        t.add(term(bridge,StandardComponentCatalog.H_BRIDGE,"gnd",1,""));
        t.add(term(p(0,-1,0),StandardComponentCatalog.GROUND,"ground",1,""));
        t.add(term(bridge,StandardComponentCatalog.H_BRIDGE,"pwm",2,""));
        t.add(term(p(1,0,0),StandardComponentCatalog.ROBO_PORT,"terminal",2,"D5"));
        t.add(term(bridge,StandardComponentCatalog.H_BRIDGE,"direction",3,""));
        t.add(term(p(-1,0,0),StandardComponentCatalog.ROBO_PORT,"terminal",3,"D8"));
        t.add(term(bridge,StandardComponentCatalog.H_BRIDGE,"out_a",4,""));
        t.add(term(motor,StandardComponentCatalog.DC_MOTOR,"motor_positive",4,""));
        t.add(term(bridge,StandardComponentCatalog.H_BRIDGE,"out_b",5,""));
        t.add(term(motor,StandardComponentCatalog.DC_MOTOR,"motor_negative",5,""));
        return new MobileElectricalNetlist(t);
    }
    private static MobileTerminal term(GridVector p,String type,String port,int network,String role) {
        return new MobileTerminal(p,type,port,Direction.NORTH,role,network);
    }
    private static ModularBlockSnapshot module(String type,GridVector p,ComponentOrientation o) {
        return new ModularBlockSnapshot(type,1,p,o,type,0,null);
    }
    private static GridVector p(int x,int y,int z){return new GridVector(x,y,z);}
}
