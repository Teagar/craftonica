package br.com.craftonica.server;

import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.management.ServerConfigurationManager;

/** Raises the integrated LAN capacity for the COUDE classroom world. */
public final class LanPlayerCapacity {
    public static final int COUDE_MAX_PLAYERS = 20;

    private LanPlayerCapacity() { }

    public static void configure(MinecraftServer server) {
        if (server == null || !appliesTo(server.isDedicatedServer(), server.getFolderName())) return;
        ServerConfigurationManager players = server.getConfigurationManager();
        if (players == null) return;
        ReflectionHelper.setPrivateValue(ServerConfigurationManager.class, players,
                Integer.valueOf(COUDE_MAX_PLAYERS), "maxPlayers", "field_72405_c");
    }

    static boolean appliesTo(boolean dedicatedServer, String saveFolder) {
        return !dedicatedServer && saveFolder != null && "COUDE".equalsIgnoreCase(saveFolder.trim());
    }
}
