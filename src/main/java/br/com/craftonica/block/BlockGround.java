package br.com.craftonica.block;

public final class BlockGround extends BlockSingleTerminal {
    public BlockGround() {
        super("ground", "craftonica:ground");
    }

    @Override
    protected String getTerminalTexture() {
        return "craftonica:terminal_ground";
    }
}
