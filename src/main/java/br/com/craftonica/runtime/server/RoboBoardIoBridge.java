package br.com.craftonica.runtime.server;

import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import br.com.craftonica.runtime.core.AvrInputs;
import br.com.craftonica.runtime.core.UltrasonicPeripheral;
import br.com.craftonica.sensor.UltrasonicMeasurementModel;
import br.com.craftonica.tile.RoboBoardState;
import br.com.craftonica.tile.RoboPortRegistry;
import br.com.craftonica.tile.TileEntityRoboBoard;
import br.com.craftonica.tile.TileEntityRoboPort;
import br.com.craftonica.tile.TileEntityUltrasonicSensor;
import br.com.craftonica.tile.UltrasonicSensorRegistry;
import br.com.craftonica.block.BlockUltrasonicSensor;
import net.minecraft.world.World;

/** Loaded-chunk-only adapter between physical RoboPorts and one runtime input snapshot. */
public final class RoboBoardIoBridge {
    public static final double DIGITAL_LOW_MAX_VOLTS = 1.5;
    public static final double DIGITAL_HIGH_MIN_VOLTS = 3.0;
    public static final int MAX_ANALOG_MICROVOLTS = 5000000;

    private RoboBoardIoBridge() { }

    public static Snapshot sample(TileEntityRoboBoard board, int stableMask) {
        if (board == null || board.getWorldObj() == null || board.getWorldObj().isRemote)
            return Snapshot.empty(stableMask);
        World world = board.getWorldObj();
        ElectricalNetworkManager manager = ElectricalNetworkManager.forWorld(world);
        TileEntityRoboPort[] ports = new TileEntityRoboPort[RoboBoardState.OUTPUT_PIN_COUNT];
        int duplicateMask = 0;
        for (TileEntityRoboPort port : RoboPortRegistry.loadedPorts(board)) {
            if (!port.getRole().isDigital() || !port.isInputPin()) continue;
            int pin = port.getLogicalPin();
            int bit = 1 << pin;
            if (ports[pin] != null) {
                duplicateMask |= bit;
                ports[pin] = null;
            } else if ((duplicateMask & bit) == 0) ports[pin] = port;
        }

        boolean[] digital = new boolean[AvrInputs.DIGITAL_PIN_COUNT];
        int[] analog = new int[AvrInputs.ANALOG_CHANNEL_COUNT];
        int nextStable = stableMask & ((1 << RoboBoardState.OUTPUT_PIN_COUNT) - 1);
        int indeterminate = 0;
        int available = 0;
        for (int pin = 0; pin < ports.length; pin++) {
            TileEntityRoboPort port = ports[pin];
            int bit = 1 << pin;
            if (port == null) {
                digital[pin] = (nextStable & bit) != 0;
                if ((duplicateMask & bit) != 0) indeterminate |= bit;
                continue;
            }
            Double voltage = sampleTerminalVoltage(manager, port);
            if (voltage == null || voltage.isNaN() || voltage.isInfinite()) {
                digital[pin] = (nextStable & bit) != 0;
                indeterminate |= bit;
                continue;
            }
            available |= bit;
            Transfer transfer = transferDigital(voltage.doubleValue(), (nextStable & bit) != 0);
            if (transfer.high) nextStable |= bit; else nextStable &= ~bit;
            if (transfer.indeterminate) indeterminate |= bit;
            digital[pin] = transfer.high;
            if (pin >= 14 && pin <= 19) analog[pin - 14] = clampMicrovolts(voltage.doubleValue());
        }
        UltrasonicPeripheral ultrasonic = sampleUltrasonic(board, manager);
        return new Snapshot(new AvrInputs(digital, analog, ultrasonic), nextStable, indeterminate, available, duplicateMask);
    }

    public static void invalidateAdjacentPorts(TileEntityRoboBoard board) {
        if (board == null || board.getWorldObj() == null || board.getWorldObj().isRemote) return;
        World world = board.getWorldObj();
        ElectricalNetworkManager manager = ElectricalNetworkManager.forWorld(world);
        for (TileEntityRoboPort port : RoboPortRegistry.loadedPorts(board))
            manager.invalidateAround(new BlockPosition(port.xCoord, port.yCoord, port.zCoord));
    }

    static Transfer transferDigital(double voltage, boolean previousHigh) {
        if (Double.isNaN(voltage) || Double.isInfinite(voltage)) return new Transfer(previousHigh, true);
        if (voltage <= DIGITAL_LOW_MAX_VOLTS) return new Transfer(false, false);
        if (voltage >= DIGITAL_HIGH_MIN_VOLTS) return new Transfer(true, false);
        return new Transfer(previousHigh, true);
    }

    static int clampMicrovolts(double volts) {
        if (Double.isNaN(volts) || volts <= 0.0) return 0;
        if (volts >= 5.0) return MAX_ANALOG_MICROVOLTS;
        return (int) Math.round(volts * 1000000.0);
    }

    private static Double sampleTerminalVoltage(ElectricalNetworkManager manager, TileEntityRoboPort port) {
        int outward = port.getOutwardSide();
        if (outward < 0) return null;
        return manager.getTerminalVoltage(new BlockPosition(port.xCoord, port.yCoord, port.zCoord), outward);
    }

    private static UltrasonicPeripheral sampleUltrasonic(TileEntityRoboBoard board,
                                                         ElectricalNetworkManager manager) {
        UltrasonicPeripheral found = null;
        for (TileEntityUltrasonicSensor sensor : UltrasonicSensorRegistry.loaded(board.getWorldObj())) {
            double dx = sensor.xCoord - board.xCoord, dy = sensor.yCoord - board.yCoord,
                    dz = sensor.zCoord - board.zCoord;
            if (dx * dx + dy * dy + dz * dz > TileEntityRoboPort.MAX_LINK_DISTANCE
                    * TileEntityRoboPort.MAX_LINK_DISTANCE) continue;
            BlockPosition sensorPosition = new BlockPosition(sensor.xCoord, sensor.yCoord, sensor.zCoord);
            Double vcc = manager.getTerminalVoltage(sensorPosition, 1);
            Double ground = manager.getTerminalVoltage(sensorPosition, 0);
            if (vcc == null || ground == null || vcc < 4.5 || Math.abs(ground) > 0.25) continue;
            int front = BlockUltrasonicSensor.normalizeFront(
                    board.getWorldObj().getBlockMetadata(sensor.xCoord, sensor.yCoord, sensor.zCoord) & 7);
            int triggerSide = BlockUltrasonicSensor.leftOf(front);
            int echoSide = BlockUltrasonicSensor.rightOf(front);
            TileEntityRoboPort trigger = null, echo = null;
            int triggerMatches = 0, echoMatches = 0;
            for (TileEntityRoboPort port : RoboPortRegistry.loadedPorts(board)) {
                if (!port.getRole().isDigital()) continue;
                BlockPosition portPosition = new BlockPosition(port.xCoord, port.yCoord, port.zCoord);
                int outward = port.getOutwardSide();
                if (outward < 0) continue;
                if (manager.shareTerminalNode(sensorPosition, triggerSide, portPosition, outward)) {
                    triggerMatches++;
                    trigger = port;
                }
                if (manager.shareTerminalNode(sensorPosition, echoSide, portPosition, outward)) {
                    echoMatches++;
                    echo = port;
                }
            }
            if (triggerMatches != 1 || echoMatches != 1 || trigger == echo) continue;
            UltrasonicMeasurementModel.Measurement measurement = sensor.sample();
            UltrasonicPeripheral candidate = new UltrasonicPeripheral(true, trigger.getLogicalPin(),
                    echo.getLogicalPin(), measurement.echo ? measurement.echoCycles : 0L);
            if (found != null) return null;
            found = candidate;
        }
        return found;
    }

    static final class Transfer {
        final boolean high;
        final boolean indeterminate;
        Transfer(boolean high, boolean indeterminate) { this.high = high; this.indeterminate = indeterminate; }
    }

    public static final class Snapshot {
        public final AvrInputs inputs;
        public final int stableMask;
        public final int indeterminateMask;
        public final int availableMask;
        public final int duplicateMask;

        Snapshot(AvrInputs inputs, int stableMask, int indeterminateMask, int availableMask, int duplicateMask) {
            this.inputs = inputs;
            this.stableMask = stableMask;
            this.indeterminateMask = indeterminateMask;
            this.availableMask = availableMask;
            this.duplicateMask = duplicateMask;
        }

        static Snapshot empty(int stableMask) {
            boolean[] digital = new boolean[AvrInputs.DIGITAL_PIN_COUNT];
            for (int pin = 0; pin < digital.length; pin++) digital[pin] = (stableMask & (1 << pin)) != 0;
            return new Snapshot(new AvrInputs(digital, new int[AvrInputs.ANALOG_CHANNEL_COUNT], null),
                    stableMask, 0, 0, 0);
        }
    }
}
