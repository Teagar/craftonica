package br.com.craftonica.electrical;

import br.com.craftonica.block.BlockEducationalActuator;
import br.com.craftonica.tile.TileEntityEducationalActuator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EducationalActuatorTransferTest {
    @Test public void fixedLoadsArePlausibleAndBounded() {
        assertEquals(220, BlockEducationalActuator.Type.BUZZER.getResistanceOhms());
        assertEquals(100, BlockEducationalActuator.Type.DC_MOTOR.getResistanceOhms());
    }

    @Test public void voltageAndPowerProduceDeterministicActivity() {
        assertEquals(0, TileEntityEducationalActuator.activityLevel(0.0, 0.0, 100.0));
        assertEquals(8, TileEntityEducationalActuator.activityLevel(-2.5, 0.0, 100.0));
        assertEquals(15, TileEntityEducationalActuator.activityLevel(5.0, 0.25, 100.0));
        assertEquals(15, TileEntityEducationalActuator.activityLevel(50.0, 25.0, 100.0));
    }

    @Test public void invalidSolverValuesFailInactive() {
        assertEquals(0, TileEntityEducationalActuator.activityLevel(Double.NaN, 0.0, 100.0));
        assertEquals(0, TileEntityEducationalActuator.activityLevel(5.0, Double.POSITIVE_INFINITY, 100.0));
        assertEquals(0, TileEntityEducationalActuator.activityLevel(5.0, 0.25, 0.0));
    }
}
