package br.com.craftonica.robot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Canonical eight-module teaching layout, rotated from one chassis-facing value. */
public final class RobotAssemblyLayout {
    private RobotAssemblyLayout() { }

    public static List<Slot> slots(int facing) {
        if (facing < 2 || facing > 5) throw new IllegalArgumentException("facing");
        List<Slot> values = new ArrayList<Slot>();
        values.add(slot(0, RobotModuleSnapshot.Type.CHASSIS_CORE, 0, 0, 0, facing));
        values.add(slot(1, RobotModuleSnapshot.Type.LEFT_MOTOR, -1, 0, 0, facing));
        values.add(slot(2, RobotModuleSnapshot.Type.RIGHT_MOTOR, 1, 0, 0, facing));
        values.add(slot(3, RobotModuleSnapshot.Type.ROBO_BOARD, 0, 1, 0, facing));
        values.add(slot(4, RobotModuleSnapshot.Type.ULTRASONIC_SENSOR, 0, 1, -1, facing));
        values.add(slot(5, RobotModuleSnapshot.Type.H_BRIDGE, 0, 1, 1, facing));
        values.add(slot(6, RobotModuleSnapshot.Type.POWER_5V, -1, 1, 1, facing));
        values.add(slot(7, RobotModuleSnapshot.Type.GROUND, 1, 1, 1, facing));
        return Collections.unmodifiableList(values);
    }

    public static List<RobotModuleSnapshot> manifest(int facing) {
        List<RobotModuleSnapshot> result = new ArrayList<RobotModuleSnapshot>();
        for (Slot slot : slots(facing)) result.add(new RobotModuleSnapshot(slot.id, slot.type,
                slot.localX, slot.localY, slot.localZ, sideOrdinal(facing)));
        return result;
    }

    private static Slot slot(int id, RobotModuleSnapshot.Type type, int x, int y, int z, int facing) {
        int frontX = facing == 4 ? -1 : facing == 5 ? 1 : 0;
        int frontZ = facing == 2 ? -1 : facing == 3 ? 1 : 0;
        int rightX = -frontZ, rightZ = frontX;
        return new Slot(id, type, x, y, z, rightX * x + frontX * -z, y,
                rightZ * x + frontZ * -z);
    }

    private static int sideOrdinal(int facing) { return facing == 2 ? 0 : facing == 5 ? 1 : facing == 3 ? 2 : 3; }

    public static final class Slot {
        public final int id, localX, localY, localZ, dx, dy, dz;
        public final RobotModuleSnapshot.Type type;
        Slot(int id, RobotModuleSnapshot.Type type, int localX, int localY, int localZ,
             int dx, int dy, int dz) {
            this.id = id; this.type = type; this.localX = localX; this.localY = localY;
            this.localZ = localZ; this.dx = dx; this.dy = dy; this.dz = dz;
        }
    }
}
