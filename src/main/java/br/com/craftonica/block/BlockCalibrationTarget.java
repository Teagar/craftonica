package br.com.craftonica.block;

import br.com.craftonica.CraftonicaCreativeTab;
import br.com.craftonica.sensor.AcousticMaterialProfile;
import br.com.craftonica.tile.TileEntityCalibrationTarget;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/** A compact sub-block calibration rail with one replaceable acoustic sample. */
public final class BlockCalibrationTarget extends BlockContainer {
    private final AcousticMaterialProfile profile;

    public BlockCalibrationTarget(String name, String texture, AcousticMaterialProfile profile) {
        super(Material.wood);
        if (profile == null) throw new IllegalArgumentException("profile");
        this.profile = profile;
        setBlockName(name); setBlockTextureName(texture); setCreativeTab(CraftonicaCreativeTab.INSTANCE);
        setHardness(0.8F);
    }

    public AcousticMaterialProfile getProfile() { return profile; }

    @Override public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player,
                                               int side, float hitX, float hitY, float hitZ) {
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityCalibrationTarget)) return false;
        TileEntityCalibrationTarget target = (TileEntityCalibrationTarget) tile;
        if (!world.isRemote) {
            if (player.isSneaking()) target.cycleAngle(); else target.cycleDistance();
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.calibration.state",
                    target.getDistanceCentimeters(), target.getAngleDegrees(), profile.getId()));
        }
        return true;
    }

    @Override public TileEntity createNewTileEntity(World world, int metadata) {
        return new TileEntityCalibrationTarget();
    }
}
