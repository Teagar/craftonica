package br.com.craftonica.sensor;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class UltrasonicBeamModelTest {
    @Test public void continuousYawProducesNormalizedCenterRayAndBoundedCone() {
        UltrasonicSensorPose pose = new UltrasonicSensorPose(1.25, 2.5, -3.0, 37.0, 0.0);
        UltrasonicSensorPose.Vector forward = pose.forward();
        UltrasonicBeamModel.Ray[] rays = UltrasonicBeamModel.rays(pose);

        assertEquals(UltrasonicBeamModel.RAY_COUNT, rays.length);
        assertEquals(forward.x, rays[0].direction.x, 1.0e-12);
        assertEquals(forward.y, rays[0].direction.y, 1.0e-12);
        assertEquals(forward.z, rays[0].direction.z, 1.0e-12);
        assertTrue(Math.abs(forward.x) > 0.1);
        assertTrue(Math.abs(forward.z) > 0.1);

        double weight = 0.0;
        double minimumDot = StrictMath.cos(StrictMath.toRadians(UltrasonicBeamModel.HALF_ANGLE_DEGREES));
        for (UltrasonicBeamModel.Ray ray : rays) {
            weight += ray.weight;
            assertTrue(ray.direction.dot(forward) >= minimumDot - 1.0e-12);
        }
        assertEquals(1.0, weight, 1.0e-12);
    }

    @Test public void apparentCoverageTracksHowMuchOfTheConeReturnsFromOneSurface() {
        List<UltrasonicBeamModel.RayReturn> full = new ArrayList<UltrasonicBeamModel.RayReturn>();
        for (int i = 0; i < UltrasonicBeamModel.RAY_COUNT; i++) {
            full.add(new UltrasonicBeamModel.RayReturn(i, "wall", 120.0 + i, 5.0,
                    AcousticMaterialProfile.MDF, "world"));
        }
        UltrasonicBeamModel.Echo wall = UltrasonicBeamModel.resolve(full);
        assertNotNull(wall);
        assertEquals(1.0, wall.apparentCoverage, 1.0e-12);
        assertEquals(120.0, wall.centimeters, 1.0e-12);

        List<UltrasonicBeamModel.RayReturn> small = new ArrayList<UltrasonicBeamModel.RayReturn>();
        small.add(new UltrasonicBeamModel.RayReturn(0, "small", 120.0, 5.0,
                AcousticMaterialProfile.MDF, "world"));
        UltrasonicBeamModel.Echo smallTarget = UltrasonicBeamModel.resolve(small);
        assertNotNull(smallTarget);
        assertEquals(0.20, smallTarget.apparentCoverage, 1.0e-12);
        assertTrue(smallTarget.apparentCoverage < wall.apparentCoverage);
    }

    @Test public void strongestSurfaceWinsIndependentOfInputOrder() {
        List<UltrasonicBeamModel.RayReturn> first = returnsInOrder(false);
        List<UltrasonicBeamModel.RayReturn> reversed = returnsInOrder(true);
        UltrasonicBeamModel.Echo a = UltrasonicBeamModel.resolve(first);
        UltrasonicBeamModel.Echo b = UltrasonicBeamModel.resolve(reversed);
        assertNotNull(a);
        assertNotNull(b);
        assertEquals(AcousticMaterialProfile.MDF, a.material);
        assertEquals(a.centimeters, b.centimeters, 0.0);
        assertEquals(a.apparentCoverage, b.apparentCoverage, 0.0);
    }

    private List<UltrasonicBeamModel.RayReturn> returnsInOrder(boolean reverse) {
        List<UltrasonicBeamModel.RayReturn> values = new ArrayList<UltrasonicBeamModel.RayReturn>();
        for (int i = 0; i < UltrasonicBeamModel.RAY_COUNT; i++) {
            String key = i < 9 ? "rigid-wall" : "near-foam";
            double distance = i < 9 ? 140.0 : 80.0;
            AcousticMaterialProfile profile = i < 9
                    ? AcousticMaterialProfile.MDF : AcousticMaterialProfile.FOAM;
            UltrasonicBeamModel.RayReturn value = new UltrasonicBeamModel.RayReturn(
                    i, key, distance, 0.0, profile, "world");
            if (reverse) values.add(0, value); else values.add(value);
        }
        return values;
    }
}
