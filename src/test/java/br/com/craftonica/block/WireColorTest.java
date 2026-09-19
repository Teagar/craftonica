package br.com.craftonica.block;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public final class WireColorTest {
    @Test
    public void mapsVanillaDyeDamageToDistinctCableColors() {
        assertEquals(0x1D1D21, WireColor.rgb(0));
        assertEquals(0xB02E26, WireColor.rgb(1));
        assertEquals(0x3AB3DA, WireColor.rgb(12));
        assertEquals(0xF9FFFE, WireColor.rgb(15));
    }

    @Test
    public void masksInvalidMetadataToFourBits() {
        assertEquals(WireColor.rgb(0), WireColor.rgb(16));
        assertEquals(WireColor.rgb(15), WireColor.rgb(31));
    }

    @Test
    public void exposesAllSixteenDyeColors() {
        Set<Integer> colors = new HashSet<Integer>();
        for (int damage = 0; damage < 16; damage++) {
            colors.add(WireColor.rgb(damage));
        }
        assertEquals(16, colors.size());
    }

    @Test
    public void defaultWireInheritsOnlyOneUnambiguousNeighborColor() {
        assertEquals(12, WireColor.inherit(0, -1, 12, -1));
        assertEquals(12, WireColor.inherit(0, 12, 12, -1));
        assertEquals(0, WireColor.inherit(0, 12, 4, -1));
        assertEquals(0, WireColor.inherit(0, -1, -1));
    }

    @Test
    public void explicitlyColoredWireKeepsItsItemColor() {
        assertEquals(4, WireColor.inherit(4, 12, 12));
    }
}
