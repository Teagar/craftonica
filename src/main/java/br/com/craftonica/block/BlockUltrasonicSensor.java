package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import br.com.craftonica.render.CraftonicaRenderIds;
import br.com.craftonica.tile.TileEntityUltrasonicSensor;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Legacy four-terminal HC-SR04: top VCC, bottom GND, left TRIG, right ECHO. */
public class BlockUltrasonicSensor extends BlockContainer
        implements IElectricalBlock, IRotatableElectricalBlock {
    public static final double SUPPLY_RESISTANCE_OHMS = 10000.0;
    public static final double SIGNAL_RESISTANCE_OHMS = 1000000.0;
    private IIcon body, front, vcc, ground, trigger, echo;

    public BlockUltrasonicSensor() {
        super(Material.iron);
        setBlockName("ultrasonicSensor"); setBlockTextureName("craftonica:ultrasonic_body");
        setCreativeTab(CraftonicaCreativeTab.INSTANCE); setHardness(1.2F); setResistance(4.0F);
    }

    @Override public void registerBlockIcons(IIconRegister register) {
        body = register.registerIcon("craftonica:ultrasonic_body");
        front = register.registerIcon("craftonica:ultrasonic_front");
        vcc = register.registerIcon("craftonica:terminal_positive");
        ground = register.registerIcon("craftonica:terminal_ground");
        trigger = register.registerIcon("craftonica:ultrasonic_trigger");
        echo = register.registerIcon("craftonica:ultrasonic_echo");
        blockIcon = front;
    }

    @Override public IIcon getIcon(int side, int metadata) {
        int facing = normalizeFront(metadata & 7);
        if (side == facing) return front;
        if (side == 1) return vcc;
        if (side == 0) return ground;
        if (side == leftOf(facing)) return trigger;
        if (side == rightOf(facing)) return echo;
        return body;
    }

    public IIcon getBodyIcon() { return body; }
    public IIcon getFrontIcon() { return front; }
    public IIcon getVccIcon() { return vcc; }
    public IIcon getGroundIcon() { return ground; }
    public IIcon getTriggerIcon() { return trigger; }
    public IIcon getEchoIcon() { return echo; }
    @Override public boolean isOpaqueCube() { return false; }
    @Override public boolean renderAsNormalBlock() { return false; }
    @Override public int getRenderType() { return CraftonicaRenderIds.ELECTRICAL_COMPONENT; }

    @Override public boolean canConnectOnSide(IBlockAccess world, int x, int y, int z, int side) {
        int facing = normalizeFront(world.getBlockMetadata(x, y, z) & 7);
        return side == 0 || side == 1 || side == leftOf(facing) || side == rightOf(facing);
    }

    @Override public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase placer, ItemStack stack) {
        world.setBlockMetadataWithNotify(x, y, z,
                HorizontalRotation.placementSideMetadata(placer.rotationYaw, world.getBlockMetadata(x, y, z)), 2);
        invalidate(world, x, y, z);
    }

    @Override public void onBlockAdded(World world, int x, int y, int z) {
        super.onBlockAdded(world, x, y, z); invalidate(world, x, y, z);
    }

    @Override public void onNeighborBlockChange(World world, int x, int y, int z, net.minecraft.block.Block neighbor) {
        invalidate(world, x, y, z);
    }

    @Override public void breakBlock(World world, int x, int y, int z, net.minecraft.block.Block block, int metadata) {
        invalidate(world, x, y, z); super.breakBlock(world, x, y, z, block, metadata);
    }

    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                               int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile instanceof TileEntityUltrasonicSensor) {
                TileEntityUltrasonicSensor sensor = (TileEntityUltrasonicSensor) tile;
                player.addChatMessage(new ChatComponentTranslation(sensor.hasVisualEcho()
                        ? "message.craftonica.ultrasonic.last" : "message.craftonica.ultrasonic.noecho",
                        sensor.getVisualDistanceCm()));
            }
        }
        return true;
    }

    @Override public int rotateMetadata(int metadata) { return HorizontalRotation.rotateSideMetadata(metadata); }
    @Override public int getPlacementMetadata(float rotationYaw, int metadata) {
        return HorizontalRotation.placementSideMetadata(rotationYaw, metadata);
    }
    @Override public TileEntity createNewTileEntity(World world, int metadata) { return new TileEntityUltrasonicSensor(); }

    public static int normalizeFront(int side) { return side >= 2 && side <= 5 ? side : 3; }
    public static int leftOf(int front) {
        return front == 2 ? 4 : front == 3 ? 5 : front == 4 ? 3 : 2;
    }
    public static int rightOf(int front) {
        return front == 2 ? 5 : front == 3 ? 4 : front == 4 ? 2 : 3;
    }
    public static int backOf(int front) { return front == 2 ? 3 : front == 3 ? 2 : front == 4 ? 5 : 4; }

    private static void invalidate(World world, int x, int y, int z) {
        if (!world.isRemote) ElectricalNetworkManager.forWorld(world).invalidateAround(new BlockPosition(x, y, z));
    }
}
