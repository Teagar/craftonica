package br.com.craftonica.registry;

import br.com.craftonica.Craftonica;
import br.com.craftonica.robot.EntityMobileRobot;
import cpw.mods.fml.common.registry.EntityRegistry;

public final class ModEntities {
    public static final int MOBILE_TRACKING_RANGE = 96;
    public static final int MOBILE_UPDATE_FREQUENCY = 2;
    private ModEntities() { }

    public static void register() {
        EntityRegistry.registerModEntity(EntityMobileRobot.class, "mobile_robot", 0,
                Craftonica.instance, MOBILE_TRACKING_RANGE, MOBILE_UPDATE_FREQUENCY, true);
    }
}
