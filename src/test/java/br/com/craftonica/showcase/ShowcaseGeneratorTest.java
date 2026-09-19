package br.com.craftonica.showcase;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ShowcaseGeneratorTest {
    @Test public void planContainsSixUniqueProjectsInsideBoundedRoom() {
        assertEquals(6, ShowcaseGenerator.projectCount());
        assertTrue(ShowcaseGenerator.hasUniqueTitles());
        assertEquals(49, ShowcaseGenerator.WIDTH);
        assertEquals(39, ShowcaseGenerator.DEPTH);
        assertEquals(9, ShowcaseGenerator.HEIGHT);
        assertEquals(3, ShowcaseGenerator.trafficLightBranchCount());
    }
}
