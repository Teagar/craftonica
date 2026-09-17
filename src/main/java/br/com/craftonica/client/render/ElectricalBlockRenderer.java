package br.com.craftonica.client.render;

import br.com.craftonica.block.BlockElectricalButton;
import br.com.craftonica.block.BlockElectricalWire;
import br.com.craftonica.block.BlockGround;
import br.com.craftonica.block.BlockLed;
import br.com.craftonica.block.BlockPowerSource;
import br.com.craftonica.block.BlockResistor;
import br.com.craftonica.block.BlockSingleTerminal;
import br.com.craftonica.block.BlockTwoTerminal;
import br.com.craftonica.block.WireColor;
import br.com.craftonica.render.CraftonicaRenderIds;
import cpw.mods.fml.client.registry.ISimpleBlockRenderingHandler;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import org.lwjgl.opengl.GL11;

public final class ElectricalBlockRenderer implements ISimpleBlockRenderingHandler {
    private static final double P = 1.0D / 16.0D;

    @Override
    public void renderInventoryBlock(final Block block, int metadata, int modelId, final RenderBlocks renderer) {
        final int color = block instanceof BlockElectricalWire ? WireColor.rgb(metadata) : 0xFFFFFF;
        GL11.glTranslatef(-0.5F, -0.5F, -0.5F);
        renderModel(block, metadata, null, 0, 0, 0, new PartRenderer() {
            @Override
            public void render(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                               IIcon icon) {
                renderPart(block, renderer, minX, minY, minZ, maxX, maxY, maxZ, icon, color, 255);
            }
        });
        GL11.glTranslatef(0.5F, 0.5F, 0.5F);
    }

    public void renderPlacementPreview(final Block block, int metadata) {
        final RenderBlocks renderer = new RenderBlocks();
        renderModel(block, metadata, null, 0, 0, 0, new PartRenderer() {
            @Override
            public void render(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                               IIcon icon) {
                renderPart(block, renderer, minX, minY, minZ, maxX, maxY, maxZ, icon, 0xFFFFFF, 150);
            }
        });
    }

    @Override
    public boolean renderWorldBlock(final IBlockAccess world, final int x, final int y, final int z,
                                    final Block block, int modelId, final RenderBlocks renderer) {
        int metadata = world.getBlockMetadata(x, y, z);
        final int color = block instanceof BlockElectricalWire ? WireColor.rgb(metadata) : 0xFFFFFF;
        renderModel(block, metadata, world, x, y, z, new PartRenderer() {
            @Override
            public void render(double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
                               IIcon icon) {
                renderer.setRenderBounds(minX, minY, minZ, maxX, maxY, maxZ);
                renderer.setOverrideBlockTexture(icon);
                renderer.renderStandardBlockWithColorMultiplier(
                        block,
                        x,
                        y,
                        z,
                        ((color >> 16) & 255) / 255.0F,
                        ((color >> 8) & 255) / 255.0F,
                        (color & 255) / 255.0F);
                renderer.clearOverrideBlockTexture();
            }
        });
        return true;
    }

    private void renderModel(Block block, int metadata, IBlockAccess world, int x, int y, int z,
                             PartRenderer parts) {
        if (block instanceof BlockElectricalWire) {
            renderWire((BlockElectricalWire) block, metadata, world, x, y, z, parts);
        } else if (block instanceof BlockLed) {
            renderLed((BlockLed) block, metadata, world, x, y, z, parts);
        } else if (block instanceof BlockElectricalButton) {
            renderButton((BlockElectricalButton) block, metadata, parts);
        } else if (block instanceof BlockResistor) {
            renderResistor((BlockResistor) block, metadata, parts);
        } else if (block instanceof BlockSingleTerminal) {
            renderSingleTerminal((BlockSingleTerminal) block, metadata, parts);
        }
    }

    private void renderWire(BlockElectricalWire block, int metadata, IBlockAccess world, int x, int y, int z,
                            PartRenderer parts) {
        IIcon icon = block.getIcon(0, metadata);
        parts.render(6 * P, 6 * P, 6 * P, 10 * P, 10 * P, 10 * P, icon);
        int mask = world == null ? (1 << 4) | (1 << 5) : block.getConnectionMask(world, x, y, z);
        if ((mask & 1) != 0) {
            parts.render(6 * P, 0, 6 * P, 10 * P, 6 * P, 10 * P, icon);
        }
        if ((mask & 1 << 1) != 0) {
            parts.render(6 * P, 10 * P, 6 * P, 10 * P, 1, 10 * P, icon);
        }
        if ((mask & 1 << 2) != 0) {
            parts.render(6 * P, 6 * P, 0, 10 * P, 10 * P, 6 * P, icon);
        }
        if ((mask & 1 << 3) != 0) {
            parts.render(6 * P, 6 * P, 10 * P, 10 * P, 10 * P, 1, icon);
        }
        if ((mask & 1 << 4) != 0) {
            parts.render(0, 6 * P, 6 * P, 6 * P, 10 * P, 10 * P, icon);
        }
        if ((mask & 1 << 5) != 0) {
            parts.render(10 * P, 6 * P, 6 * P, 1, 10 * P, 10 * P, icon);
        }
    }

    private void renderSingleTerminal(BlockSingleTerminal block, int metadata, PartRenderer parts) {
        int terminal = normalizeHorizontal(metadata & 7);
        if (block instanceof BlockPowerSource) {
            parts.render(3 * P, P, 2 * P, 13 * P, 15 * P, 14 * P, block.getBodyIcon());
            parts.render(5 * P, 13 * P, 5 * P, 11 * P, 1, 11 * P, block.getTerminalIcon());
            renderLead(parts, terminal, block.getTerminalIcon(), 5 * P, 11 * P);
        } else if (block instanceof BlockGround) {
            parts.render(2 * P, 2 * P, 2 * P, 14 * P, 10 * P, 14 * P, block.getBodyIcon());
            parts.render(4 * P, 10 * P, 4 * P, 12 * P, 12 * P, 12 * P, block.getTerminalIcon());
            renderLead(parts, terminal, block.getTerminalIcon(), 7 * P, 9 * P);
        }
    }

    private void renderResistor(BlockResistor block, int metadata, PartRenderer parts) {
        int axis = metadata & 1;
        IIcon body = block.getBodyIcon(metadata);
        IIcon lead = block.getTerminalIcon();
        if (axis == 0) {
            parts.render(5 * P, 5 * P, 4 * P, 11 * P, 11 * P, 12 * P, body);
            parts.render(7 * P, 7 * P, 0, 9 * P, 9 * P, 4 * P, lead);
            parts.render(7 * P, 7 * P, 12 * P, 9 * P, 9 * P, 1, lead);
        } else {
            parts.render(4 * P, 5 * P, 5 * P, 12 * P, 11 * P, 11 * P, body);
            parts.render(0, 7 * P, 7 * P, 4 * P, 9 * P, 9 * P, lead);
            parts.render(12 * P, 7 * P, 7 * P, 1, 9 * P, 9 * P, lead);
        }
    }

    private void renderButton(BlockElectricalButton block, int metadata, PartRenderer parts) {
        IIcon base = block.getTerminalIcon();
        IIcon cap = block.getBodyIcon(metadata);
        parts.render(3 * P, 3 * P, 3 * P, 13 * P, 7 * P, 13 * P, base);
        double capTop = (metadata & 2) != 0 ? 11 * P : 14 * P;
        parts.render(5 * P, 7 * P, 5 * P, 11 * P, capTop, 11 * P, cap);
        if ((metadata & 1) == 0) {
            parts.render(7 * P, 6 * P, 0, 9 * P, 9 * P, 3 * P, base);
            parts.render(7 * P, 6 * P, 13 * P, 9 * P, 9 * P, 1, base);
        } else {
            parts.render(0, 6 * P, 7 * P, 3 * P, 9 * P, 9 * P, base);
            parts.render(13 * P, 6 * P, 7 * P, 1, 9 * P, 9 * P, base);
        }
    }

    private void renderLed(BlockLed block, int metadata, IBlockAccess world, int x, int y, int z,
                           PartRenderer parts) {
        IIcon body = world == null ? block.getBodyIcon() : block.getBodyIcon(world, x, y, z);
        parts.render(5 * P, 5 * P, 5 * P, 11 * P, 8 * P, 11 * P, body);
        parts.render(6 * P, 8 * P, 6 * P, 10 * P, 13 * P, 10 * P, body);
        parts.render(7 * P, 13 * P, 7 * P, 9 * P, 15 * P, 9 * P, body);
        int anode = normalizeHorizontal(metadata & 7);
        renderLead(parts, anode, block.getAnodeIcon(), 6 * P, 10 * P);
        renderLead(parts, opposite(anode), block.getCathodeIcon(), 7 * P, 9 * P);
    }

    private void renderLead(PartRenderer parts, int side, IIcon icon, double minCross, double maxCross) {
        if (side == 2) {
            parts.render(minCross, 6 * P, 0, maxCross, 10 * P, 5 * P, icon);
        } else if (side == 3) {
            parts.render(minCross, 6 * P, 11 * P, maxCross, 10 * P, 1, icon);
        } else if (side == 4) {
            parts.render(0, 6 * P, minCross, 5 * P, 10 * P, maxCross, icon);
        } else {
            parts.render(11 * P, 6 * P, minCross, 1, 10 * P, maxCross, icon);
        }
    }

    private int normalizeHorizontal(int side) {
        return side >= 2 && side <= 5 ? side : 3;
    }

    private int opposite(int side) {
        return side == 2 ? 3 : side == 3 ? 2 : side == 4 ? 5 : 4;
    }

    private void renderPart(Block block, RenderBlocks renderer,
                            double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ,
                            IIcon icon, int color, int alpha) {
        Tessellator tessellator = Tessellator.instance;
        renderer.setRenderBounds(minX, minY, minZ, maxX, maxY, maxZ);
        tessellator.startDrawingQuads();
        tessellator.setNormal(0, -1, 0);
        tessellator.setColorRGBA_I(color, alpha);
        renderer.renderFaceYNeg(block, 0, 0, 0, icon);
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.setNormal(0, 1, 0);
        tessellator.setColorRGBA_I(color, alpha);
        renderer.renderFaceYPos(block, 0, 0, 0, icon);
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.setNormal(0, 0, -1);
        tessellator.setColorRGBA_I(color, alpha);
        renderer.renderFaceZNeg(block, 0, 0, 0, icon);
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.setNormal(0, 0, 1);
        tessellator.setColorRGBA_I(color, alpha);
        renderer.renderFaceZPos(block, 0, 0, 0, icon);
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.setNormal(-1, 0, 0);
        tessellator.setColorRGBA_I(color, alpha);
        renderer.renderFaceXNeg(block, 0, 0, 0, icon);
        tessellator.draw();
        tessellator.startDrawingQuads();
        tessellator.setNormal(1, 0, 0);
        tessellator.setColorRGBA_I(color, alpha);
        renderer.renderFaceXPos(block, 0, 0, 0, icon);
        tessellator.draw();
    }

    @Override
    public boolean shouldRender3DInInventory(int modelId) {
        return true;
    }

    @Override
    public int getRenderId() {
        return CraftonicaRenderIds.ELECTRICAL_COMPONENT;
    }

    private interface PartRenderer {
        void render(double minX, double minY, double minZ,
                    double maxX, double maxY, double maxZ, IIcon icon);
    }
}
