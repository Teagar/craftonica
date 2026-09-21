package br.com.craftonica.robot;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraftforge.event.world.ChunkEvent;
import br.com.craftonica.robot.modular.EntityModularRobot;

import java.util.List;

/** Forge 1.7.10 entities have no chunk-unload callback, so suspension is adapted here. */
public final class RobotEntityEvents {
    @SubscribeEvent public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.world.isRemote) return;
        for (List list : event.getChunk().entityLists) {
            for (Object value : list) {
                if (value instanceof EntityMobileRobot) {
                    ((EntityMobileRobot) value).prepareForChunkUnload();
                } else if (value instanceof EntityModularRobot) {
                    ((EntityModularRobot) value).prepareForChunkUnload();
                }
            }
        }
    }
}
