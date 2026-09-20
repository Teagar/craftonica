package br.com.craftonica.tile;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class CalibrationTargetStateTest {
    @Test public void distanceAndAngleCycleWithinDocumentedRangesAndPersist() {
        TileEntityCalibrationTarget target = new TileEntityCalibrationTarget();
        for (int i = 0; i < 9; i++) target.cycleDistance();
        assertEquals(50, target.getDistanceCentimeters());
        target.cycleDistance();
        assertEquals(5, target.getDistanceCentimeters());
        for (int i = 0; i < 4; i++) target.cycleAngle();
        assertEquals(60, target.getAngleDegrees());

        NBTTagCompound tag = new NBTTagCompound();
        target.writeState(tag);
        TileEntityCalibrationTarget restored = new TileEntityCalibrationTarget();
        restored.readState(tag);
        assertEquals(5, restored.getDistanceCentimeters());
        assertEquals(60, restored.getAngleDegrees());
    }

    @Test public void malformedStateFallsBackSafely() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("DataVersion", TileEntityCalibrationTarget.DATA_VERSION);
        tag.setByte("DistanceCm", (byte) 47);
        tag.setByte("Angle", (byte) 19);
        TileEntityCalibrationTarget target = new TileEntityCalibrationTarget();
        target.readState(tag);
        assertEquals(5, target.getDistanceCentimeters());
        assertEquals(0, target.getAngleDegrees());
    }
}
