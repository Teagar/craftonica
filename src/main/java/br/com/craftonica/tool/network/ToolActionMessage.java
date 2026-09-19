package br.com.craftonica.tool.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public final class ToolActionMessage implements IMessage {
    private ToolAction action;
    private int value;
    private int x;
    private int y;
    private int z;
    private long revision;
    private boolean valid;

    public ToolActionMessage() {
    }

    public ToolActionMessage(ToolAction action, int value, int x, int y, int z, long revision) {
        if (action == null || revision < 0) throw new IllegalArgumentException("Invalid tool action");
        this.action = action;
        this.value = value;
        this.x = x;
        this.y = y;
        this.z = z;
        this.revision = revision;
        valid = true;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        if (buffer.readableBytes() != 25) return;
        int ordinal = buffer.readUnsignedByte();
        value = buffer.readInt();
        x = buffer.readInt();
        y = buffer.readInt();
        z = buffer.readInt();
        revision = buffer.readLong();
        if (ordinal < ToolAction.values().length && revision >= 0) {
            action = ToolAction.values()[ordinal];
            valid = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) throw new IllegalStateException("Cannot encode invalid tool action");
        buffer.writeByte(action.ordinal()).writeInt(value).writeInt(x).writeInt(y).writeInt(z).writeLong(revision);
    }

    public boolean isValid() { return valid; }
    public ToolAction getAction() { return action; }
    public int getValue() { return value; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getZ() { return z; }
    public long getRevision() { return revision; }
}
