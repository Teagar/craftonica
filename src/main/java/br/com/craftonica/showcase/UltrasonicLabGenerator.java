package br.com.craftonica.showcase;

import br.com.craftonica.registry.ModBlocks;
import br.com.craftonica.registry.ModItems;
import br.com.craftonica.tile.TileEntityRoboBoard;
import br.com.craftonica.tile.TileEntityRoboPort;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.world.WorldServer;

/** Builds four ready-wired HC-SR04 stations for manual metrology exercises. */
public final class UltrasonicLabGenerator {
    public static final int WIDTH = 35, DEPTH = 29, HEIGHT = 8;
    private static final int RESERVED_HEIGHT = 40;
    private static final Station[] STATIONS = {
            new Station("MDF", ModBlocks.TARGET_MDF, 3),
            new Station("PLASTICO", ModBlocks.TARGET_PLASTIC, 4),
            new Station("ISOPOR", ModBlocks.TARGET_STYROFOAM, 5),
            new Station("ESPUMA", ModBlocks.TARGET_FOAM, 10)
    };

    private UltrasonicLabGenerator() { }

    public static int stationCount() { return STATIONS.length; }

    public static void generate(WorldServer world, EntityPlayerMP owner, int ox, int oy, int oz) {
        if (world == null || owner == null) throw new IllegalArgumentException("World and owner are required");
        clearAndBuildRoom(world, ox, oy, oz);
        sign(world, ox + 14, oy, oz + 1, "CRAFTONICA", "LAB HC-SR04", "4 MATERIAIS", "Sketch pronto");
        sign(world, ox + 18, oy, oz + 1, "USO", "Ctrl+S compila", "F5 executa", "F6 Serial");
        station(world, owner, ox + 3, oy, oz + 4, STATIONS[0]);
        station(world, owner, ox + 19, oy, oz + 4, STATIONS[1]);
        station(world, owner, ox + 3, oy, oz + 17, STATIONS[2]);
        station(world, owner, ox + 19, oy, oz + 17, STATIONS[3]);
        supplyChest(world, ox + 31, oy, oz + 1);
    }

    public static String sketch(String material) {
        return "const byte TRIG_PIN=7,ECHO_PIN=6;\n"
                + "const char MATERIAL[]=\"" + material + "\";\n"
                + "const float NOMINAL_CM=5.0;\n"
                + "void setup(){pinMode(TRIG_PIN,OUTPUT);pinMode(ECHO_PIN,INPUT);Serial.begin(9600);"
                + "Serial.println(\"material,nominal_cm,amostra,medida_cm,eco\");"
                + "for(byte i=1;i<=10;i++){digitalWrite(TRIG_PIN,LOW);delayMicroseconds(2);"
                + "digitalWrite(TRIG_PIN,HIGH);delayMicroseconds(10);digitalWrite(TRIG_PIN,LOW);"
                + "unsigned long t=pulseIn(ECHO_PIN,HIGH,30000UL);Serial.print(MATERIAL);"
                + "Serial.print(',');Serial.print(NOMINAL_CM,2);Serial.print(\",\");Serial.print(i);Serial.print(',');"
                + "if(t==0)Serial.println(\"NA,0\");else{Serial.print(t/58.0,3);Serial.println(\",1\");}delay(100);}}\n"
                + "void loop(){}\n";
    }

    private static void station(WorldServer world, EntityPlayerMP owner, int x, int y, int z, Station station) {
        for (int dx = 0; dx < 13; dx++) for (int dz = 0; dz < 10; dz++)
            set(world, x + dx, y - 1, z + dz, Blocks.stained_hardened_clay, station.color);
        sign(world, x, y, z, station.material, "5-50 cm", "Clique: +5 cm", "Agache: angulo");

        int sx = x + 6, sy = y + 2, sz = z + 5;
        int bx = x + 6, bz = z + 1;
        set(world, bx, y, bz, ModBlocks.ROBO_BOARD, 0);
        TileEntity boardTile = world.getTileEntity(bx, y, bz);
        if (!(boardTile instanceof TileEntityRoboBoard)) return;
        TileEntityRoboBoard board = (TileEntityRoboBoard) boardTile;
        board.claimOwner(owner.getUniqueID());
        board.setTemplateSketchSource(sketch(station.material));

        set(world, sx, sy, sz, ModBlocks.ULTRASONIC_SENSOR, 3);
        set(world, sx, sy, sz + 1, station.target, 0);
        set(world, sx, y, sz, Blocks.iron_block, 0);
        remotePort(world, sx, sy + 1, sz, board, 0, TileEntityRoboPort.Role.POWER_5V);
        remotePort(world, sx, sy - 1, sz, board, 1, TileEntityRoboPort.Role.GROUND);
        remotePort(world, sx + 1, sy, sz, board, 4, TileEntityRoboPort.Role.D7);
        remotePort(world, sx - 1, sy, sz, board, 5, TileEntityRoboPort.Role.D6);

        sign(world, x + 10, y, z + 2, "Pinos", "D7 = TRIG", "D6 = ECHO", "VCC 5V / GND");
        sign(world, x + 1, y, z + 7, "ROTEIRO", "Abra a placa", "Ctrl+S e F5", "F6 ve CSV");
    }

    private static void remotePort(WorldServer world, int x, int y, int z, TileEntityRoboBoard board,
                                   int side, TileEntityRoboPort.Role role) {
        set(world, x, y, z, ModBlocks.ROBO_PORT, 0);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityRoboPort)) return;
        TileEntityRoboPort port = (TileEntityRoboPort) tile;
        if (!port.bindToBoard(board, side)) return;
        port.setRole(role, port.getRevision());
        world.markBlockForUpdate(x, y, z);
    }

    private static void clearAndBuildRoom(WorldServer world, int ox, int oy, int oz) {
        for (int x = 0; x < WIDTH; x++) for (int z = 0; z < DEPTH; z++) {
            for (int y = 0; y <= RESERVED_HEIGHT; y++) set(world, ox + x, oy + y, oz + z, Blocks.air, 0);
            set(world, ox + x, oy - 1, oz + z, Blocks.quartz_block, 0);
        }
        for (int x = 0; x < WIDTH; x++) for (int y = 0; y < 4; y++) {
            if (x < 13 || x > 21 || y > 2) set(world, ox + x, oy + y, oz, Blocks.stained_hardened_clay, 9);
            set(world, ox + x, oy + y, oz + DEPTH - 1, Blocks.stained_hardened_clay, 9);
        }
        for (int z = 0; z < DEPTH; z++) for (int y = 0; y < 4; y++) {
            set(world, ox, oy + y, oz + z, Blocks.stained_hardened_clay, 9);
            set(world, ox + WIDTH - 1, oy + y, oz + z, Blocks.stained_hardened_clay, 9);
        }
        for (int x = 1; x < WIDTH - 1; x++) for (int z = 1; z < DEPTH - 1; z++)
            set(world, ox + x, oy + 7, oz + z, (x % 7 == 3 && z % 7 == 3) ? Blocks.glowstone : Blocks.glass, 0);
        for (int x = 13; x <= 21; x++) set(world, ox + x, oy - 1, oz + 1, Blocks.gold_block, 0);
    }

    private static void supplyChest(WorldServer world, int x, int y, int z) {
        set(world, x, y, z, Blocks.chest, 0);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IInventory)) return;
        IInventory inventory = (IInventory) tile;
        inventory.setInventorySlotContents(0, instructionBook());
        inventory.setInventorySlotContents(1, new ItemStack(ModItems.ROBO_PORT_CONFIGURATOR));
        inventory.setInventorySlotContents(2, new ItemStack(ModItems.MULTIMETER));
        inventory.setInventorySlotContents(3, new ItemStack(ModItems.WRENCH));
        inventory.setInventorySlotContents(4, new ItemStack(ModItems.MANUAL));
        inventory.setInventorySlotContents(5, new ItemStack(ModBlocks.WIRE, 32));
        inventory.setInventorySlotContents(6, new ItemStack(ModBlocks.ULTRASONIC_SENSOR, 4));
        inventory.setInventorySlotContents(7, new ItemStack(ModBlocks.TARGET_MDF, 4));
        inventory.setInventorySlotContents(8, new ItemStack(ModBlocks.TARGET_PLASTIC, 4));
        inventory.setInventorySlotContents(9, new ItemStack(ModBlocks.TARGET_STYROFOAM, 4));
        inventory.setInventorySlotContents(10, new ItemStack(ModBlocks.TARGET_FOAM, 4));
    }

    private static ItemStack instructionBook() {
        ItemStack book = new ItemStack(Items.written_book);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", "Laboratorio HC-SR04"); tag.setString("author", "Craftonica Lab");
        NBTTagList pages = new NBTTagList();
        pages.appendTag(new NBTTagString("Quatro estacoes independentes: MDF, plastico, isopor e espuma. Cada RoboBoard possui seu sketch carregado."));
        pages.appendTag(new NBTTagString("Abra a RoboBoard, use Ctrl+S para compilar, F5 para executar e F6 para abrir o monitor Serial."));
        pages.appendTag(new NBTTagString("Clique no alvo para variar 5-50 cm. Ajuste NOMINAL_CM, compile e rode: cada firmware coleta exatamente 10 leituras."));
        pages.appendTag(new NBTTagString("Apos cada lote use /craftonica sonar export. Ao completar 400 linhas, o save recebe metricas, regressao e R2."));
        tag.setTag("pages", pages); book.setTagCompound(tag);
        return book;
    }

    private static void sign(WorldServer world, int x, int y, int z, String a, String b, String c, String d) {
        set(world, x, y, z, Blocks.standing_sign, 8);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntitySign) {
            TileEntitySign sign = (TileEntitySign) tile;
            sign.signText[0] = fit(a); sign.signText[1] = fit(b); sign.signText[2] = fit(c); sign.signText[3] = fit(d);
            sign.markDirty(); world.markBlockForUpdate(x, y, z);
        }
    }

    private static String fit(String value) { return value.length() <= 15 ? value : value.substring(0, 15); }
    private static void set(WorldServer world, int x, int y, int z, Block block, int metadata) {
        world.setBlock(x, y, z, block, metadata, 2);
    }

    private static final class Station {
        final String material;
        final Block target;
        final int color;
        Station(String material, Block target, int color) {
            this.material = material; this.target = target; this.color = color;
        }
    }
}
