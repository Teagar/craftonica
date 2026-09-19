package br.com.craftonica.showcase;

import br.com.craftonica.registry.ModBlocks;
import br.com.craftonica.registry.ModItems;
import br.com.craftonica.tile.TileEntityRoboBoard;
import br.com.craftonica.tile.TileEntityRoboPort;
import br.com.craftonica.tile.TileEntityLed;
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

import java.util.HashSet;
import java.util.Set;

/** Builds a bounded teaching room without requiring WorldEdit. */
public final class ShowcaseGenerator {
    public static final int WIDTH = 49, DEPTH = 39, HEIGHT = 9;
    private static final Project[] PROJECTS = {
            new Project("01 BLINK", "LED D13", TileEntityRoboPort.Role.D13,
                    "void setup(){pinMode(13,OUTPUT);}",
                    "void loop(){digitalWrite(13,HIGH);delay(500);digitalWrite(13,LOW);delay(500);}"),
            new Project("02 FADE PWM", "LED D9", TileEntityRoboPort.Role.D9,
                    "void setup(){pinMode(9,OUTPUT);}",
                    "void loop(){for(int v=0;v<256;v+=5){analogWrite(9,v);delay(30);}}"),
            new Project("03 SEMAFORO", "D10 D11 D12", TileEntityRoboPort.Role.D10,
                    "void setup(){pinMode(10,OUTPUT);pinMode(11,OUTPUT);pinMode(12,OUTPUT);}",
                    "void loop(){digitalWrite(10,HIGH);delay(3000);digitalWrite(10,LOW);digitalWrite(11,HIGH);delay(700);digitalWrite(11,LOW);digitalWrite(12,HIGH);delay(3000);digitalWrite(12,LOW);}"),
            new Project("04 SENSOR LUZ", "A0 + Serial", TileEntityRoboPort.Role.A0,
                    "void setup(){Serial.begin(9600);}",
                    "void loop(){Serial.println(analogRead(A0));delay(250);}"),
            new Project("05 POTENCIOMETRO", "A1 + PWM D9", TileEntityRoboPort.Role.A1,
                    "void setup(){pinMode(9,OUTPUT);Serial.begin(9600);}",
                    "void loop(){int v=analogRead(A1);analogWrite(9,v/4);Serial.println(v);delay(50);}"),
            new Project("06 TEMPERATURA", "A2 + D8", TileEntityRoboPort.Role.A2,
                    "void setup(){pinMode(8,OUTPUT);Serial.begin(9600);}",
                    "void loop(){int t=analogRead(A2);digitalWrite(8,t>600);Serial.println(t);delay(250);}")
    };

    private ShowcaseGenerator() {}
    public static int projectCount() { return PROJECTS.length; }
    public static int trafficLightBranchCount() { return 3; }
    public static boolean hasUniqueTitles() {
        Set<String> titles = new HashSet<String>();
        for (Project project : PROJECTS) if (!titles.add(project.title)) return false;
        return true;
    }

    public static void generate(WorldServer world, EntityPlayerMP owner, int ox, int oy, int oz) {
        if (world == null || owner == null) throw new IllegalArgumentException("World and owner are required");
        clearAndFloor(world, ox, oy, oz);
        buildRoom(world, ox, oy, oz);
        sign(world, ox + 22, oy, oz + 1, "CRAFTONICA", "LAB ARDUINO", "6 PROJETOS", "Leia os livros");
        sign(world, ox + 26, oy, oz + 1, "GERADOR", "/craftonica", "showcase create", "Somente OP");
        for (int i = 0; i < PROJECTS.length; i++)
            station(world, owner, ox + 3 + i % 3 * 15, oy, oz + 7 + i / 3 * 15, PROJECTS[i], i);
        world.getWorldInfo().setSpawnPosition(ox + 24, oy + 1, oz + 3);
    }

    private static void clearAndFloor(WorldServer world, int ox, int oy, int oz) {
        for (int x = 0; x < WIDTH; x++) for (int z = 0; z < DEPTH; z++) {
            for (int y = 0; y <= HEIGHT; y++) set(world, ox + x, oy + y, oz + z, Blocks.air, 0);
            set(world, ox + x, oy - 1, oz + z, Blocks.quartz_block, 0);
        }
    }

    private static void buildRoom(WorldServer world, int ox, int oy, int oz) {
        for (int x = 0; x < WIDTH; x++) for (int y = 0; y < 5; y++) {
            if (x < 21 || x > 27 || y > 2) set(world, ox + x, oy + y, oz, Blocks.stained_hardened_clay, 9);
            set(world, ox + x, oy + y, oz + DEPTH - 1, Blocks.stained_hardened_clay, 9);
        }
        for (int z = 0; z < DEPTH; z++) for (int y = 0; y < 5; y++) {
            set(world, ox, oy + y, oz + z, Blocks.stained_hardened_clay, 9);
            set(world, ox + WIDTH - 1, oy + y, oz + z, Blocks.stained_hardened_clay, 9);
        }
        for (int x = 2; x < WIDTH - 2; x += 6) for (int z = 3; z < DEPTH - 2; z += 6)
            set(world, ox + x, oy + 6, oz + z, Blocks.glowstone, 0);
        for (int x = 1; x < WIDTH - 1; x++) for (int z = 1; z < DEPTH - 1; z++)
            if ((x - 2) % 6 != 0 || (z - 3) % 6 != 0)
                set(world, ox + x, oy + 7, oz + z, Blocks.glass, 0);
        for (int x = 21; x <= 27; x++) set(world, ox + x, oy - 1, oz + 1, Blocks.gold_block, 0);
    }

    private static void station(WorldServer world, EntityPlayerMP owner, int x, int y, int z,
                                Project project, int index) {
        int color = new int[]{3, 4, 5, 9, 10, 14}[index];
        for (int dx = 0; dx < 12; dx++) for (int dz = 0; dz < 11; dz++)
            set(world, x + dx, y - 1, z + dz, Blocks.stained_hardened_clay, color);
        for (int dx = 0; dx < 12; dx++) set(world, x + dx, y, z, Blocks.iron_block, 0);
        sign(world, x, y + 1, z + 1, project.title, project.pins, "Sketch carregado", "Livro no bau");
        chest(world, x + 10, y, z + 1, project);

        int bx = x + 3, bz = z + 4;
        set(world, bx, y, bz, ModBlocks.ROBO_BOARD, 0);
        TileEntity board = world.getTileEntity(bx, y, bz);
        TileEntityRoboBoard roboBoard = board instanceof TileEntityRoboBoard
                ? (TileEntityRoboBoard) board : null;
        if (board instanceof TileEntityRoboBoard) {
            roboBoard.claimOwner(owner.getUniqueID());
            roboBoard.setTemplateSketchSource(project.setup + "\n\n" + project.loop + "\n");
        }
        port(world, bx + 1, y, bz, project.primaryRole);
        port(world, bx, y, bz + 1, TileEntityRoboPort.Role.GROUND);
        if (index <= 1) {
            outputLed(world, bx, y, bz, 1);
        }
        if (index == 2) {
            trafficLight(world, roboBoard, bx, y, bz);
        } else if (index == 3) sensorInput(world, bx, y, bz, ModBlocks.LIGHT_SENSOR);
        else if (index == 4) potentiometerInputAndLed(world, bx, y, bz);
        else if (index == 5) {
            sensorInput(world, bx, y, bz, ModBlocks.TEMPERATURE_SENSOR);
            port(world, bx - 1, y, bz, TileEntityRoboPort.Role.D8);
            set(world, bx - 2, y, bz, ModBlocks.RESISTOR_220, 1);
            set(world, bx - 3, y, bz, ModBlocks.BUZZER, 1);
            for (int dx = -4; dx <= 0; dx++) set(world, bx + dx, y, bz + 2, ModBlocks.WIRE, 0);
            set(world, bx - 4, y, bz, ModBlocks.WIRE, 0);
            set(world, bx - 4, y, bz + 1, ModBlocks.WIRE, 0);
        }
    }

    private static void trafficLight(WorldServer world, TileEntityRoboBoard board, int bx, int y, int bz) {
        TileEntityRoboPort.Role[] roles = {TileEntityRoboPort.Role.D10,
                TileEntityRoboPort.Role.D11, TileEntityRoboPort.Role.D12};
        int[] colors = {1, 11, 2};
        for (int branch = 0; branch < roles.length; branch++) {
            int z = bz + branch * 2;
            remotePort(world, bx + 1, y, z, board, 5, roles[branch]);
            set(world, bx + 2, y, z, ModBlocks.RESISTOR_220, 1);
            led(world, bx + 3, y, z, 4, colors[branch]);
            set(world, bx + 4, y, z, ModBlocks.WIRE, 0);
        }
        for (int dz = 0; dz <= 4; dz++) set(world, bx + 4, y, bz + dz, ModBlocks.WIRE, 0);
        set(world, bx, y, bz + 2, ModBlocks.WIRE, 0);
        for (int dx = 0; dx <= 4; dx++) set(world, bx + dx, y, bz + 3, ModBlocks.WIRE, 0);
    }

    private static void led(WorldServer world, int x, int y, int z, int metadata, int color) {
        set(world, x, y, z, ModBlocks.LED, metadata);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntityLed) ((TileEntityLed) tile).setColor(color);
    }

    private static void remotePort(WorldServer world, int x, int y, int z, TileEntityRoboBoard board,
                                   int side, TileEntityRoboPort.Role role) {
        set(world, x, y, z, ModBlocks.ROBO_PORT, 0);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityRoboPort) || board == null) return;
        TileEntityRoboPort port = (TileEntityRoboPort) tile;
        if (!port.bindToBoard(board, side)) return;
        for (int guard = TileEntityRoboPort.Role.values().length + 1;
             port.getRole() != role && guard > 0; guard--) port.cycleRole(port.getRevision());
    }

    private static void outputLed(WorldServer world, int bx, int y, int bz, int direction) {
        set(world, bx + 2 * direction, y, bz, ModBlocks.RESISTOR_220, 1);
        set(world, bx + 3 * direction, y, bz, ModBlocks.LED, direction > 0 ? 4 : 5);
        set(world, bx + 4 * direction, y, bz, ModBlocks.WIRE, 0);
        set(world, bx + 4 * direction, y, bz + 1, ModBlocks.WIRE, 0);
        int from = Math.min(0, 4 * direction), to = Math.max(0, 4 * direction);
        for (int dx = from; dx <= to; dx++) set(world, bx + dx, y, bz + 2, ModBlocks.WIRE, 0);
    }

    private static void sensorInput(WorldServer world, int bx, int y, int bz, Block sensor) {
        set(world, bx + 2, y, bz, sensor, 1);
        set(world, bx + 3, y, bz, ModBlocks.WIRE, 0);
        set(world, bx + 3, y, bz + 1, ModBlocks.WIRE, 0);
        for (int dx = 0; dx <= 3; dx++) set(world, bx + dx, y, bz + 2, ModBlocks.WIRE, 0);
    }

    private static void potentiometerInputAndLed(WorldServer world, int bx, int y, int bz) {
        port(world, bx, y, bz - 1, TileEntityRoboPort.Role.POWER_5V);
        port(world, bx - 1, y, bz, TileEntityRoboPort.Role.D9);
        set(world, bx + 2, y, bz + 4, ModBlocks.POTENTIOMETER, 0);

        set(world, bx + 2, y, bz, ModBlocks.WIRE, 0);
        set(world, bx + 2, y + 1, bz, ModBlocks.WIRE, 0);
        set(world, bx + 2, y + 2, bz, ModBlocks.WIRE, 0);
        for (int dz = 1; dz <= 4; dz++) set(world, bx + 2, y + 2, bz + dz, ModBlocks.WIRE, 0);
        set(world, bx + 2, y + 1, bz + 4, ModBlocks.WIRE, 0);

        set(world, bx, y, bz - 2, ModBlocks.WIRE, 0);
        for (int dx = 1; dx <= 4; dx++) set(world, bx + dx, y, bz - 2, ModBlocks.WIRE, 0);
        for (int dz = -1; dz <= 3; dz++) set(world, bx + 4, y, bz + dz, ModBlocks.WIRE, 0);
        set(world, bx + 3, y, bz + 3, ModBlocks.WIRE, 0);
        set(world, bx + 2, y, bz + 3, ModBlocks.WIRE, 0);

        set(world, bx, y, bz + 2, ModBlocks.WIRE, 0);
        set(world, bx - 1, y, bz + 2, ModBlocks.WIRE, 0);
        for (int dz = 3; dz <= 5; dz++) set(world, bx - 1, y, bz + dz, ModBlocks.WIRE, 0);
        for (int dx = 0; dx <= 2; dx++) set(world, bx + dx, y, bz + 5, ModBlocks.WIRE, 0);

        set(world, bx - 2, y, bz, ModBlocks.RESISTOR_220, 1);
        set(world, bx - 3, y, bz, ModBlocks.LED, 5);
        set(world, bx - 4, y, bz, ModBlocks.WIRE, 0);
        set(world, bx - 4, y, bz + 1, ModBlocks.WIRE, 0);
        for (int dx = -4; dx < 0; dx++) set(world, bx + dx, y, bz + 2, ModBlocks.WIRE, 0);
    }

    private static void port(WorldServer world, int x, int y, int z, TileEntityRoboPort.Role role) {
        set(world, x, y, z, ModBlocks.ROBO_PORT, 0);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityRoboPort)) return;
        TileEntityRoboPort port = (TileEntityRoboPort) tile;
        if (!port.bindToAdjacentBoard()) return;
        for (int guard = TileEntityRoboPort.Role.values().length + 1;
             port.getRole() != role && guard > 0; guard--) port.cycleRole(port.getRevision());
    }

    private static void chest(WorldServer world, int x, int y, int z, Project project) {
        set(world, x, y, z, Blocks.chest, 0);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof IInventory)) return;
        IInventory inventory = (IInventory) tile;
        inventory.setInventorySlotContents(0, book(project));
        inventory.setInventorySlotContents(1, new ItemStack(ModItems.MULTIMETER));
        inventory.setInventorySlotContents(2, new ItemStack(ModItems.WRENCH));
        inventory.setInventorySlotContents(3, new ItemStack(ModItems.MANUAL));
        inventory.setInventorySlotContents(4, new ItemStack(ModBlocks.WIRE, 32));
        inventory.setInventorySlotContents(5, new ItemStack(ModBlocks.RESISTOR_220, 8));
        inventory.setInventorySlotContents(6, new ItemStack(ModBlocks.LED, 4));
        inventory.setInventorySlotContents(7, new ItemStack(ModItems.ROBO_PORT_CONFIGURATOR));
        inventory.setInventorySlotContents(8, new ItemStack(ModItems.WIRE_ROUTER));
    }

    private static ItemStack book(Project project) {
        ItemStack book = new ItemStack(Items.written_book);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("title", project.title); tag.setString("author", "Craftonica Lab");
        NBTTagList pages = new NBTTagList();
        pages.appendTag(new NBTTagString(project.title + "\n\nPinos: " + project.pins
                + "\n\nO sketch ja esta na RoboBoard. Abra e compile com Ctrl+S."));
        pages.appendTag(new NBTTagString(project.setup));
        pages.appendTag(new NBTTagString(project.loop));
        pages.appendTag(new NBTTagString("F5 inicia/para. F6 abre o Serial. Respeite os limites DC da Craftonica 1.0."));
        tag.setTag("pages", pages); book.setTagCompound(tag);
        return book;
    }

    private static void sign(WorldServer world, int x, int y, int z, String a, String b, String c, String d) {
        set(world, x, y, z, Blocks.standing_sign, 8);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileEntitySign) {
            TileEntitySign sign = (TileEntitySign) tile;
            sign.signText[0] = fit(a); sign.signText[1] = fit(b); sign.signText[2] = fit(c); sign.signText[3] = fit(d);
            sign.markDirty();
        }
    }

    private static String fit(String value) { return value.length() <= 15 ? value : value.substring(0, 15); }
    private static void set(WorldServer world, int x, int y, int z, Block block, int metadata) {
        world.setBlock(x, y, z, block, metadata, 2);
    }

    private static final class Project {
        final String title, pins, setup, loop;
        final TileEntityRoboPort.Role primaryRole;
        Project(String title, String pins, TileEntityRoboPort.Role role, String setup, String loop) {
            this.title = title; this.pins = pins; this.primaryRole = role; this.setup = setup; this.loop = loop;
        }
    }
}
