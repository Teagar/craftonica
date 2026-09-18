package br.com.craftonica.block;

import br.com.craftonica.tile.TileEntityAnalogSensor;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** A deterministic 0-5 V sensor represented as a two-terminal Thevenin source. */
public final class BlockAnalogSensor extends BlockTwoTerminal {
    public enum Type { LIGHT, TEMPERATURE }

    private final Type type;
    private final String registryName;
    private IIcon positiveIcon;
    private IIcon negativeIcon;

    public BlockAnalogSensor(Type type, String registryName, String unlocalizedName, String textureName) {
        super(requireText(unlocalizedName, "unlocalizedName"), requireText(textureName, "textureName"));
        if (type == null) throw new IllegalArgumentException("type");
        this.type = type;
        this.registryName = requireText(registryName, "registryName");
    }

    public Type getType() { return type; }
    public String getRegistryName() { return registryName; }
    public int getOutputResistanceOhms() { return TileEntityAnalogSensor.OUTPUT_RESISTANCE_OHMS; }

    public int getVisualLevel(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileEntityAnalogSensor ? ((TileEntityAnalogSensor) tile).getVisualLevel() : 0;
    }

    public int getVisualColor(IBlockAccess world, int x, int y, int z) {
        int level = getVisualLevel(world, x, y, z);
        if (type == Type.LIGHT) return rgb(96 + level * 10, 104 + level * 9, 88 + level * 6);
        return rgb(64 + level * 12, 144 - level * 5, 224 - level * 9);
    }

    @Override public void registerBlockIcons(IIconRegister register) {
        super.registerBlockIcons(register);
        positiveIcon = register.registerIcon("craftonica:terminal_positive");
        negativeIcon = register.registerIcon("craftonica:terminal_ground");
    }

    public IIcon getPositiveIcon() { return positiveIcon; }
    public IIcon getNegativeIcon() { return negativeIcon; }

    private static int rgb(int red, int green, int blue) {
        return Math.min(255, red) << 16 | Math.max(0, Math.min(255, green)) << 8
                | Math.max(0, Math.min(255, blue));
    }

    @Override public boolean hasTileEntity(int metadata) { return true; }
    @Override public TileEntity createTileEntity(World world, int metadata) { return new TileEntityAnalogSensor(); }

    private static String requireText(String value, String name) {
        if (value == null || value.length() == 0) throw new IllegalArgumentException(name);
        return value;
    }
}
