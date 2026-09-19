package br.com.craftonica.block;

public interface IRotatableElectricalBlock {
    int rotateMetadata(int metadata);

    int getPlacementMetadata(float rotationYaw, int metadata);
}
