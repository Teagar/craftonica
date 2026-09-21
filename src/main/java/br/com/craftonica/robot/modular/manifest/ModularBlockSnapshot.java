package br.com.craftonica.robot.modular.manifest;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.GridVector;
import net.minecraft.nbt.NBTTagCompound;

/** Immutable copy of one captured world block, including its TileEntity payload. */
public final class ModularBlockSnapshot {
    public final String componentTypeId;
    public final int componentSchema;
    public final GridVector localPosition;
    public final ComponentOrientation localOrientation;
    public final String blockRegistryName;
    public final int metadata;
    private final NBTTagCompound tileData;

    public ModularBlockSnapshot(String componentTypeId, int componentSchema, GridVector localPosition,
            ComponentOrientation localOrientation, String blockRegistryName, int metadata,
            NBTTagCompound tileData) {
        if (componentTypeId == null || componentTypeId.length() == 0 || componentSchema <= 0
                || localPosition == null || localOrientation == null || blockRegistryName == null
                || blockRegistryName.length() == 0 || metadata < 0 || metadata > 15)
            throw new IllegalArgumentException("block snapshot");
        this.componentTypeId = componentTypeId;
        this.componentSchema = componentSchema;
        this.localPosition = localPosition;
        this.localOrientation = localOrientation;
        this.blockRegistryName = blockRegistryName;
        this.metadata = metadata;
        this.tileData = tileData == null ? null : (NBTTagCompound) tileData.copy();
    }

    public NBTTagCompound getTileData() {
        return tileData == null ? null : (NBTTagCompound) tileData.copy();
    }
}
