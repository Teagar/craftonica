package br.com.craftonica.tool.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;

public final class ToolStateMessage implements IMessage {
    public static final int MULTIMETER = 0;
    public static final int ROBOPORT = 1;
    private static final int MAX_TEXT = 160;

    private int type;
    private int mode;
    private int dimension;
    private boolean firstSet;
    private int firstX, firstY, firstZ, firstSide;
    private boolean secondSet;
    private int secondX, secondY, secondZ, secondSide;
    private boolean boardSet;
    private int boardX, boardY, boardZ;
    private boolean portSet;
    private int portX, portY, portZ, role;
    private long revision;
    private String headline = "", detail = "";
    private boolean valid;

    public ToolStateMessage() {
    }

    public static ToolStateMessage multimeter(int mode, int dimension,
                                               boolean firstSet, int firstX, int firstY, int firstZ, int firstSide,
                                               boolean secondSet, int secondX, int secondY, int secondZ, int secondSide,
                                               String headline, String detail) {
        ToolStateMessage message = new ToolStateMessage();
        message.type = MULTIMETER; message.mode = mode; message.dimension = dimension;
        message.firstSet = firstSet; message.firstX = firstX; message.firstY = firstY;
        message.firstZ = firstZ; message.firstSide = firstSide;
        message.secondSet = secondSet; message.secondX = secondX; message.secondY = secondY;
        message.secondZ = secondZ; message.secondSide = secondSide;
        message.headline = safe(headline); message.detail = safe(detail); message.valid = true;
        return message;
    }

    public static ToolStateMessage roboPort(int dimension,
                                             boolean boardSet, int boardX, int boardY, int boardZ,
                                             boolean portSet, int portX, int portY, int portZ,
                                             int role, long revision, String headline, String detail) {
        ToolStateMessage message = new ToolStateMessage();
        message.type = ROBOPORT; message.dimension = dimension;
        message.boardSet = boardSet; message.boardX = boardX; message.boardY = boardY; message.boardZ = boardZ;
        message.portSet = portSet; message.portX = portX; message.portY = portY; message.portZ = portZ;
        message.role = role; message.revision = revision;
        message.headline = safe(headline); message.detail = safe(detail); message.valid = true;
        return message;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        valid = false;
        try {
            if (buffer.readableBytes() > 512) return;
            type = buffer.readUnsignedByte(); mode = buffer.readInt(); dimension = buffer.readInt();
            firstSet = buffer.readBoolean(); firstX = buffer.readInt(); firstY = buffer.readInt();
            firstZ = buffer.readInt(); firstSide = buffer.readInt();
            secondSet = buffer.readBoolean(); secondX = buffer.readInt(); secondY = buffer.readInt();
            secondZ = buffer.readInt(); secondSide = buffer.readInt();
            boardSet = buffer.readBoolean(); boardX = buffer.readInt(); boardY = buffer.readInt(); boardZ = buffer.readInt();
            portSet = buffer.readBoolean(); portX = buffer.readInt(); portY = buffer.readInt(); portZ = buffer.readInt();
            role = buffer.readInt(); revision = buffer.readLong();
            headline = ByteBufUtils.readUTF8String(buffer); detail = ByteBufUtils.readUTF8String(buffer);
            valid = (type == MULTIMETER || type == ROBOPORT) && headline.length() <= MAX_TEXT
                    && detail.length() <= MAX_TEXT && !buffer.isReadable() && revision >= 0
                    && (!firstSet || validPoint(firstX, firstY, firstZ, firstSide))
                    && (!secondSet || validPoint(secondX, secondY, secondZ, secondSide))
                    && (!boardSet || validBlock(boardX, boardY, boardZ))
                    && (!portSet || validBlock(portX, portY, portZ) && role >= 0 && role < 22);
        } catch (RuntimeException rejected) {
            valid = false;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!valid) throw new IllegalStateException("Cannot encode invalid tool state");
        buffer.writeByte(type).writeInt(mode).writeInt(dimension);
        buffer.writeBoolean(firstSet).writeInt(firstX).writeInt(firstY).writeInt(firstZ).writeInt(firstSide);
        buffer.writeBoolean(secondSet).writeInt(secondX).writeInt(secondY).writeInt(secondZ).writeInt(secondSide);
        buffer.writeBoolean(boardSet).writeInt(boardX).writeInt(boardY).writeInt(boardZ);
        buffer.writeBoolean(portSet).writeInt(portX).writeInt(portY).writeInt(portZ);
        buffer.writeInt(role).writeLong(revision);
        ByteBufUtils.writeUTF8String(buffer, headline); ByteBufUtils.writeUTF8String(buffer, detail);
    }

    private static String safe(String value) {
        String safe = value == null ? "" : value;
        return safe.length() <= MAX_TEXT ? safe : safe.substring(0, MAX_TEXT);
    }

    private static boolean validPoint(int x, int y, int z, int side) {
        return validBlock(x, y, z) && side >= 0 && side < 6;
    }

    private static boolean validBlock(int x, int y, int z) {
        return x >= -30000000 && x <= 30000000 && z >= -30000000 && z <= 30000000
                && y >= 0 && y < 256;
    }

    public boolean isValid() { return valid; }
    public int getType() { return type; }
    public int getMode() { return mode; }
    public int getDimension() { return dimension; }
    public boolean isFirstSet() { return firstSet; }
    public int getFirstX() { return firstX; } public int getFirstY() { return firstY; }
    public int getFirstZ() { return firstZ; } public int getFirstSide() { return firstSide; }
    public boolean isSecondSet() { return secondSet; }
    public int getSecondX() { return secondX; } public int getSecondY() { return secondY; }
    public int getSecondZ() { return secondZ; } public int getSecondSide() { return secondSide; }
    public boolean isBoardSet() { return boardSet; }
    public int getBoardX() { return boardX; } public int getBoardY() { return boardY; } public int getBoardZ() { return boardZ; }
    public boolean isPortSet() { return portSet; }
    public int getPortX() { return portX; } public int getPortY() { return portY; } public int getPortZ() { return portZ; }
    public int getRole() { return role; } public long getRevision() { return revision; }
    public String getHeadline() { return headline; } public String getDetail() { return detail; }
}
