package br.com.craftonica.showcase;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class UnoR3GeneratorTest {
    @Test
    public void planContainsCompleteUniqueUnoTerminalSet() {
        assertEquals(22, UnoR3Generator.terminalCount());
        assertTrue(UnoR3Generator.hasUniqueSignalRoles());
        assertEquals(13, UnoR3Generator.WIDTH);
        assertEquals(19, UnoR3Generator.DEPTH);
    }
}
