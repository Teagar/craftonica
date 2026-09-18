package br.com.craftonica.block;

import br.com.craftonica.tile.TileEntityEducationalActuator;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** A two-terminal educational load; mechanical and acoustic physics are intentionally omitted. */
public final class BlockEducationalActuator extends BlockTwoTerminal {
    public enum Type {
        BUZZER(220),
        DC_MOTOR(100);

        private final int resistanceOhms;

        Type(int resistanceOhms) { this.resistanceOhms = resistanceOhms; }
        public int getResistanceOhms() { return resistanceOhms; }
    }

    private final Type type;
    private final String registryName;
    private IIcon activeIcon;

    public BlockEducationalActuator(Type type, String registryName, String unlocalizedName, String textureName) {
        super(requireText(unlocalizedName, "unlocalizedName"), requireText(textureName, "textureName"));
        if (type == null) throw new IllegalArgumentException("type");
        this.type = type;
        this.registryName = requireText(registryName, "registryName");
    }

    public Type getType() { return type; }
    public String getRegistryName() { return registryName; }
    public int getResistanceOhms() { return type.getResistanceOhms(); }

    public int getActivityLevel(IBlockAccess world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileEntityEducationalActuator
                ? ((TileEntityEducationalActuator) tile).getActivityLevel() : 0;
    }

    @Override public void registerBlockIcons(IIconRegister register) {
        super.registerBlockIcons(register);
        activeIcon = register.registerIcon(getTextureName() + "_active");
    }

    public IIcon getBodyIcon(IBlockAccess world, int x, int y, int z) {
        return getActivityLevel(world, x, y, z) > 0 ? activeIcon : bodyIcon;
    }

    @Override public boolean hasTileEntity(int metadata) { return true; }
    @Override public TileEntity createTileEntity(World world, int metadata) { return new TileEntityEducationalActuator(); }

    private static String requireText(String value, String name) {
        if (value == null || value.length() == 0) throw new IllegalArgumentException(name);
        return value;
    }
}
