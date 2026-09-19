package br.com.craftonica.showcase;

import br.com.craftonica.registry.ModBlocks;
import br.com.craftonica.tile.TileEntityLed;
import br.com.craftonica.tile.TileEntityRoboBoard;
import br.com.craftonica.tile.TileEntityRoboPort;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.world.WorldServer;

import java.util.HashSet;
import java.util.Set;

/** Deterministic, functional Arduino Uno R3-inspired board built from world blocks. */
public final class UnoR3Generator {
    public static final int WIDTH = 13, DEPTH = 19, HEIGHT = 6;
    private static final TileEntityRoboPort.Role[] ROLES = {
            TileEntityRoboPort.Role.D0, TileEntityRoboPort.Role.D1,
            TileEntityRoboPort.Role.D2, TileEntityRoboPort.Role.D3,
            TileEntityRoboPort.Role.D4, TileEntityRoboPort.Role.D5,
            TileEntityRoboPort.Role.D6, TileEntityRoboPort.Role.D7,
            TileEntityRoboPort.Role.D8, TileEntityRoboPort.Role.D9,
            TileEntityRoboPort.Role.D10, TileEntityRoboPort.Role.D11,
            TileEntityRoboPort.Role.D12, TileEntityRoboPort.Role.D13,
            TileEntityRoboPort.Role.A0, TileEntityRoboPort.Role.A1,
            TileEntityRoboPort.Role.A2, TileEntityRoboPort.Role.A3,
            TileEntityRoboPort.Role.A4, TileEntityRoboPort.Role.A5,
            TileEntityRoboPort.Role.POWER_5V, TileEntityRoboPort.Role.GROUND
    };

    private UnoR3Generator() { }

    public static int terminalCount() { return ROLES.length; }
    public static boolean hasUniqueSignalRoles() {
        Set<TileEntityRoboPort.Role> roles = new HashSet<TileEntityRoboPort.Role>();
        for (TileEntityRoboPort.Role role : ROLES) if (!roles.add(role)) return false;
        return true;
    }

    public static void generate(WorldServer world, EntityPlayerMP owner, int ox, int oy, int oz) {
        if (world == null || owner == null) throw new IllegalArgumentException("World and owner are required");
        clear(world, ox, oy, oz);
        base(world, ox, oy, oz);

        int bx = ox + 6, bz = oz + 9;
        set(world, bx, oy + 1, bz, ModBlocks.ROBO_BOARD, 0);
        TileEntity tile = world.getTileEntity(bx, oy + 1, bz);
        if (!(tile instanceof TileEntityRoboBoard)) return;
        TileEntityRoboBoard board = (TileEntityRoboBoard) tile;
        board.claimOwner(owner.getUniqueID());

        for (int pin = 0; pin < 10; pin++)
            terminal(world, board, ox, oy + 1, oz + 3 + pin, 4, ROLES[pin]);
        for (int pin = 10; pin < 14; pin++)
            terminal(world, board, ox + 4 + pin - 10, oy + 1, oz, 2, ROLES[pin]);
        for (int analog = 0; analog < 6; analog++)
            terminal(world, board, ox + WIDTH - 1, oy + 1, oz + 3 + analog, 5, ROLES[14 + analog]);
        terminal(world, board, ox + WIDTH - 1, oy + 1, oz + 11, 5, TileEntityRoboPort.Role.POWER_5V);
        terminal(world, board, ox + WIDTH - 1, oy + 1, oz + 12, 5, TileEntityRoboPort.Role.GROUND);

        decorate(world, ox, oy, oz);
        world.getWorldInfo().setSpawnPosition(ox + 6, oy + 2, oz + DEPTH + 2);
    }

    private static void clear(WorldServer world, int ox, int oy, int oz) {
        for (int x = -2; x < WIDTH + 2; x++) for (int z = -2; z < DEPTH + 3; z++) {
            set(world, ox + x, oy - 1, oz + z, Blocks.quartz_block, 0);
            for (int y = 0; y <= HEIGHT; y++) set(world, ox + x, oy + y, oz + z, Blocks.air, 0);
        }
    }

    private static void base(WorldServer world, int ox, int oy, int oz) {
        for (int x = 0; x < WIDTH; x++) for (int z = 0; z < DEPTH; z++)
            set(world, ox + x, oy, oz + z, Blocks.lapis_block, 0);
        set(world, ox, oy, oz, Blocks.air, 0);
        set(world, ox + WIDTH - 1, oy, oz + DEPTH - 1, Blocks.air, 0);
        for (int x = 0; x < WIDTH; x++) {
            set(world, ox + x, oy - 1, oz - 1, Blocks.iron_block, 0);
            set(world, ox + x, oy - 1, oz + DEPTH, Blocks.iron_block, 0);
        }
        sign(world, ox + 5, oy + 1, oz + DEPTH - 2, "CRAFTONICA", "UNO R3", "20 IO + 5V GND", "Placa funcional");
    }

    private static void decorate(WorldServer world, int ox, int oy, int oz) {
        for (int z = 7; z <= 11; z++) set(world, ox + 5, oy + 1, oz + z, Blocks.obsidian, 0);
        for (int z = 7; z <= 11; z++) set(world, ox + 7, oy + 1, oz + z, Blocks.obsidian, 0);
        set(world, ox + 6, oy + 1, oz + 7, Blocks.obsidian, 0);
        set(world, ox + 6, oy + 1, oz + 11, Blocks.obsidian, 0);

        for (int z = 7; z <= 10; z++) set(world, ox - 1, oy + 1, oz + z, Blocks.iron_block, 0);
        set(world, ox - 2, oy + 1, oz + 8, Blocks.iron_block, 0);
        set(world, ox - 2, oy + 1, oz + 9, Blocks.iron_block, 0);
        set(world, ox + 2, oy + 1, oz + 15, Blocks.coal_block, 0);
        set(world, ox + 2, oy + 2, oz + 15, Blocks.iron_block, 0);
        set(world, ox + 9, oy + 1, oz + 14, Blocks.quartz_block, 0);
        set(world, ox + 10, oy + 1, oz + 14, Blocks.quartz_block, 0);
        set(world, ox + 9, oy + 1, oz + 16, Blocks.stone_pressure_plate, 0);

        led(world, ox + 9, oy + 1, oz + 10, 1);
        led(world, ox + 10, oy + 1, oz + 10, 2);
        for (int z = 2; z < DEPTH - 2; z++) {
            if (z != 9 && z != 10) set(world, ox + 3, oy + 1, oz + z, Blocks.gold_block, 0);
        }
    }

    private static void terminal(WorldServer world, TileEntityRoboBoard board, int x, int y, int z,
                                 int side, TileEntityRoboPort.Role role) {
        set(world, x, y, z, ModBlocks.ROBO_PORT, 0);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityRoboPort)) return;
        TileEntityRoboPort port = (TileEntityRoboPort) tile;
        if (!port.bindToBoard(board, side)) return;
        for (int guard = TileEntityRoboPort.Role.values().length + 1;
             port.getRole() != role && guard > 0; guard--) port.cycleRole(port.getRevision());
        sign(world, x, y + 1, z, role.name(), "RoboPort", "Face externa", "");
    }

    private static void led(WorldServer world, int x, int y, int z, int color) {
        set(world, x, y, z, ModBlocks.LED, 4);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityLed) {
            ((TileEntityLed) tile).setColor(color);
            world.markBlockForUpdate(x, y, z);
        }
    }

    private static void sign(WorldServer world, int x, int y, int z, String a, String b, String c, String d) {
        set(world, x, y, z, Blocks.standing_sign, 8);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntitySign) {
            TileEntitySign sign = (TileEntitySign) tile;
            sign.signText[0] = fit(a); sign.signText[1] = fit(b);
            sign.signText[2] = fit(c); sign.signText[3] = fit(d); sign.markDirty();
        }
    }

    private static String fit(String value) { return value.length() <= 15 ? value : value.substring(0, 15); }
    private static void set(WorldServer world, int x, int y, int z, Block block, int metadata) {
        world.setBlock(x, y, z, block, metadata, 2);
    }
}
