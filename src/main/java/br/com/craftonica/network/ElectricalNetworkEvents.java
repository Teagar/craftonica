package br.com.craftonica.network;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

public final class ElectricalNetworkEvents {
    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.PlaceEvent event) {
        if (!event.world.isRemote) {
            ElectricalNetworkManager.forWorld(event.world)
                    .invalidateAround(new BlockPosition(event.x, event.y, event.z));
        }
    }

    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!event.world.isRemote) {
            ElectricalNetworkManager.forWorld(event.world)
                    .invalidateAround(new BlockPosition(event.x, event.y, event.z));
        }
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.world.isRemote) {
            ElectricalNetworkManager.forWorld(event.world).tick();
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!event.world.isRemote) {
            ElectricalNetworkManager.forWorld(event.world).loadChunk(event.getChunk());
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!event.world.isRemote) {
            ElectricalNetworkManager.forWorld(event.world).unloadChunk(event.getChunk());
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        ElectricalNetworkManager.unload(event.world);
    }
}
