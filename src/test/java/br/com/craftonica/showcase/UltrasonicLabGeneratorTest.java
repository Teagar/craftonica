package br.com.craftonica.showcase;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class UltrasonicLabGeneratorTest {
    @Test public void providesFourStationsWithRealPulseSketches() {
        assertEquals(4, UltrasonicLabGenerator.stationCount());
        String sketch = UltrasonicLabGenerator.sketch("ESPUMA");
        assertTrue(sketch.contains("TRIG_PIN=7"));
        assertTrue(sketch.contains("ECHO_PIN=6"));
        assertTrue(sketch.contains("pulseIn(ECHO_PIN,HIGH,30000UL)"));
        assertTrue(sketch.contains("material,nominal_cm,amostra,medida_cm,eco"));
        assertTrue(sketch.contains("const char MATERIAL[]=\"ESPUMA\""));
        assertTrue(sketch.contains("for(byte i=1;i<=10;i++)"));
        assertTrue(sketch.contains("const float NOMINAL_CM=5.0"));
        assertTrue(sketch.contains("void loop(){}"));
    }
}
