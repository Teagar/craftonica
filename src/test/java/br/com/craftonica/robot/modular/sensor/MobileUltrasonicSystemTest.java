package br.com.craftonica.robot.modular.sensor;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.Direction;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.assembly.AssemblyEdge;
import br.com.craftonica.robot.modular.electrical.MobileElectricalNetlist;
import br.com.craftonica.robot.modular.electrical.MobileTerminal;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.sensor.UltrasonicSensorPose;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

public final class MobileUltrasonicSystemTest {
    @Test public void installedFrontSideAndHeightProduceDistinctWorldPoses() {
        MobileUltrasonicSystem system = system();
        List<MobileUltrasonicSystem.Sensor> sensors = system.getSensors();
        UltrasonicSensorPose front = sensors.get(0).pose(10.5, 20.0, 30.5, 0.0);
        UltrasonicSensorPose elevated = sensors.get(1).pose(10.5, 20.0, 30.5, StrictMath.PI / 2.0);
        UltrasonicSensorPose side = sensors.get(2).pose(10.5, 20.0, 30.5, 0.0);
        assertEquals(10.5, front.x, 0.0); assertEquals(30.249, front.z, 1.0e-12);
        assertEquals(-180.0, front.yawDegrees, 0.0);
        assertEquals(11.751, side.x, 1.0e-12); assertEquals(-90.0, side.yawDegrees, 0.0);
        assertEquals(22.5, elevated.y, 0.0);
        assertEquals(90.0, elevated.yawDegrees, 1.0e-12);
    }

    @Test public void simultaneousTriggersAreReturnedInCanonicalPositionOrder() {
        MobileUltrasonicSystem system = system();
        int outputs = (1 << 2) | (1 << 4) | (1 << 6);
        List<MobileUltrasonicSystem.Sensor> active = system.triggered(outputs, outputs, true);
        assertEquals(3, active.size());
        assertEquals(new GridVector(0, 0, 0), active.get(0).position);
        assertEquals(new GridVector(0, 2, 0), active.get(1).position);
        assertEquals(new GridVector(1, 0, 0), active.get(2).position);
        assertTrue(system.triggered(outputs, outputs, false).isEmpty());
    }

    @Test public void missingPowerOrSignalKeepsPhysicalSensorDisabled() {
        ModularBlockSnapshot sensor = module(new GridVector(0,0,0), ComponentOrientation.NORTH_UP);
        MobileUltrasonicSystem system = new MobileUltrasonicSystem(new ModularRobotManifest(
                new UUID(9, 10), Collections.singletonList(sensor), Collections.<AssemblyEdge>emptyList(),
                MobileElectricalNetlist.EMPTY));
        assertEquals(1, system.getSensors().size()); assertFalse(system.getSensors().get(0).enabled);
        assertTrue(system.triggered(1 << 2, 1 << 2, true).isEmpty());
    }

    private static MobileUltrasonicSystem system() {
        List<ModularBlockSnapshot> modules = Arrays.asList(
                module(new GridVector(0,0,0), ComponentOrientation.NORTH_UP),
                module(new GridVector(1,0,0), new ComponentOrientation(Direction.EAST, Direction.UP)),
                module(new GridVector(0,2,0), ComponentOrientation.NORTH_UP));
        List<MobileTerminal> terminals = new ArrayList<MobileTerminal>();
        bind(terminals, new GridVector(0,0,0), 0, 2, 3);
        bind(terminals, new GridVector(1,0,0), 4, 4, 5);
        bind(terminals, new GridVector(0,2,0), 8, 6, 7);
        return new MobileUltrasonicSystem(new ModularRobotManifest(new UUID(7,8), modules,
                Collections.<AssemblyEdge>emptyList(), new MobileElectricalNetlist(terminals)));
    }

    private static void bind(List<MobileTerminal> values, GridVector sensor, int base, int trigger, int echo) {
        values.add(t(sensor,"vcc",base,"")); values.add(other(base,StandardComponentCatalog.POWER_SOURCE,"positive",""));
        values.add(t(sensor,"gnd",base+1,"")); values.add(other(base+1,StandardComponentCatalog.GROUND,"ground",""));
        values.add(t(sensor,"trig",base+2,"")); values.add(other(base+2,StandardComponentCatalog.ROBO_PORT,"terminal","D"+trigger));
        values.add(t(sensor,"echo",base+3,"")); values.add(other(base+3,StandardComponentCatalog.ROBO_PORT,"terminal","D"+echo));
    }
    private static MobileTerminal t(GridVector p,String port,int network,String role) {
        return new MobileTerminal(p,StandardComponentCatalog.HC_SR04,port,Direction.NORTH,role,network);
    }
    private static MobileTerminal other(int network,String type,String port,String role) {
        return new MobileTerminal(new GridVector(20+network,0,0),type,port,Direction.SOUTH,role,network);
    }
    private static ModularBlockSnapshot module(GridVector p, ComponentOrientation orientation) {
        return new ModularBlockSnapshot(StandardComponentCatalog.HC_SR04,1,p,orientation,
                StandardComponentCatalog.HC_SR04,0,null);
    }
}
