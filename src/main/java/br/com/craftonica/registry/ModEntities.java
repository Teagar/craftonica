package br.com.craftonica.registry;

import br.com.craftonica.Craftonica;
import br.com.craftonica.robot.EntityMobileRobot;
import cpw.mods.fml.common.registry.EntityRegistry;

public final class ModEntities {
    private ModEntities() { }

    public static void register() {
        EntityRegistry.registerModEntity(EntityMobileRobot.class, "mobile_robot", 0,
                Craftonica.instance, 96, 2, true);
    }
}
