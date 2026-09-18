package br.com.craftonica.tile;

import br.com.craftonica.block.BlockRoboBoard;
import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import java.util.UUID;

/** Persisted ownership and electrical role for one RoboBoard face. */
public final class TileEntityRoboPort extends TileEntity {
    public static final int SCHEMA_VERSION = 1;
    public static final double DRIVE_RESISTANCE_OHMS = 25.0;
    public static final double INPUT_PULLUP_RESISTANCE_OHMS = 20000.0;
    public static final double LOGIC_VOLTS = 5.0;

    public enum Role {
        D0, D1, D2, D3, D4, D5, D6, D7, D8, D9,
        D10, D11, D12, D13, D14, D15, D16, D17, D18, D19,
        POWER_5V, GROUND;

        public boolean isDigital() { return ordinal() < RoboBoardState.OUTPUT_PIN_COUNT; }
        public int getPin() { return isDigital() ? ordinal() : -1; }
    }

    private Role role = Role.D0;
    private long revision;
    private int boardX;
    private int boardY;
    private int boardZ;
    private UUID boardId;
    private int outwardSide = -1;

    public boolean bindToAdjacentBoard() {
        if (worldObj == null || worldObj.isRemote) return false;
        AdjacentBoard found = findSingleAdjacentBoard();
        if (found == null) {
            clearBinding();
            return false;
        }
        if (boardId != null && (!boardId.equals(found.tile.getBoardId())
                || boardX != found.x || boardY != found.y || boardZ != found.z)) {
            clearBinding();
            return false;
        }
        boardX = found.x;
        boardY = found.y;
        boardZ = found.z;
        boardId = found.tile.getBoardId();
        outwardSide = opposite(found.side);
        if (role.isDigital() && !isDigitalRoleAvailable(role)) role = firstAvailableDigitalRole();
        markDirty();
        return true;
    }

    public boolean validateBinding() {
        if (worldObj == null || worldObj.isRemote || boardId == null) return false;
        if (!worldObj.getChunkProvider().chunkExists(boardX >> 4, boardZ >> 4)) return false;
        AdjacentBoard found = findSingleAdjacentBoard();
        boolean valid = found != null && boardX == found.x && boardY == found.y && boardZ == found.z
                && boardId.equals(found.tile.getBoardId()) && outwardSide == opposite(found.side);
        if (!valid) clearBinding();
        return valid;
    }

    public long cycleRole(long expectedRevision) {
        requireServer();
        if (!validateBinding()) throw new IllegalStateException("RoboPort is not bound to exactly one loaded board");
        if (revision != expectedRevision) throw new IllegalStateException("Stale RoboPort revision");
        if (revision == Long.MAX_VALUE) throw new IllegalStateException("RoboPort revision exhausted");
        Role[] values = Role.values();
        do {
            role = values[(role.ordinal() + 1) % values.length];
        } while (role.isDigital() && !isDigitalRoleAvailable(role));
        revision++;
        markDirty();
        return revision;
    }

    /** Canonical Thevenin voltage exposed to the nodal extractor. */
    public Double getDriveVoltage() {
        TileEntityRoboBoard board = getBoundBoard();
        if (board == null || hasDuplicateDigitalRole()) return null;
        int pin = role.getPin();
        if (pin >= 0 && board.isPinPwm(pin) && !board.isPinPwmRecognized(pin)) return null;
        return calculateDriveVoltage(role, pin >= 0 && board.isPinOutput(pin),
                pin >= 0 && board.isPinHigh(pin), pin >= 0 && board.isPinPwmRecognized(pin),
                pin >= 0 ? board.getPinPwmCompare(pin) : 0);
    }

    /** Canonical finite source resistance exposed to the nodal extractor. */
    public Double getDriveResistanceOhms() {
        TileEntityRoboBoard board = getBoundBoard();
        if (board == null || hasDuplicateDigitalRole()) return null;
        int pin = role.getPin();
        boolean output = pin >= 0 && board.isPinOutput(pin);
        boolean pwm = pin >= 0 && board.isPinPwmRecognized(pin);
        int compare = pin >= 0 ? board.getPinPwmCompare(pin) : 0;
        if (pin >= 0 && board.isPinPwm(pin) && !pwm) return null;
        if (pwm && (compare < 0 || compare > 255)) return null;
        if (role == Role.GROUND) return 0.0;
        if (role == Role.POWER_5V || output) return DRIVE_RESISTANCE_OHMS;
        return pin >= 0 && board.isPinHigh(pin) ? INPUT_PULLUP_RESISTANCE_OHMS : null;
    }

    public static Double calculateDriveVoltage(Role role, boolean output, boolean high,
                                               boolean pwm, int compare) {
        if (role == null) throw new IllegalArgumentException("RoboPort role is required");
        if (role == Role.POWER_5V) return LOGIC_VOLTS;
        if (role == Role.GROUND) return 0.0;
        if (!output) return high ? LOGIC_VOLTS : null;
        if (!pwm) return high ? LOGIC_VOLTS : 0.0;
        if (compare < 0 || compare > 255) return null;
        return LOGIC_VOLTS * compare / 255.0;
    }

    public boolean isReferenceGround() { return role == Role.GROUND && getBoundBoard() != null; }
    public boolean isHighImpedance() {
        TileEntityRoboBoard board = getBoundBoard();
        return board != null && role.isDigital() && !board.isPinOutput(role.getPin())
                && !board.isPinHigh(role.getPin());
    }
    public boolean isInputPin() {
        TileEntityRoboBoard board = getBoundBoard();
        return board != null && role.isDigital() && !board.isPinOutput(role.getPin());
    }

    public boolean hasDuplicateDigitalRole() {
        return role.isDigital() && !isDigitalRoleAvailable(role);
    }

    public TileEntityRoboBoard getBoundBoard() {
        if (!validateBinding()) return null;
        TileEntity tile = worldObj.getTileEntity(boardX, boardY, boardZ);
        return tile instanceof TileEntityRoboBoard ? (TileEntityRoboBoard) tile : null;
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setInteger("PortSchema", SCHEMA_VERSION);
        tag.setByte("Role", (byte) role.ordinal());
        tag.setLong("Revision", revision);
        if (boardId != null) {
            tag.setInteger("BoardX", boardX);
            tag.setInteger("BoardY", boardY);
            tag.setInteger("BoardZ", boardZ);
            tag.setLong("BoardMost", boardId.getMostSignificantBits());
            tag.setLong("BoardLeast", boardId.getLeastSignificantBits());
            tag.setByte("Outward", (byte) outwardSide);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        int roleOrdinal = tag.getByte("Role");
        if (tag.getInteger("PortSchema") != SCHEMA_VERSION || roleOrdinal < 0 || roleOrdinal >= Role.values().length
                || tag.getLong("Revision") < 0) {
            role = Role.D0;
            revision = 0;
            clearBinding();
            return;
        }
        role = Role.values()[roleOrdinal];
        revision = tag.getLong("Revision");
        if (tag.hasKey("BoardMost") && tag.hasKey("BoardLeast")) {
            boardX = tag.getInteger("BoardX");
            boardY = tag.getInteger("BoardY");
            boardZ = tag.getInteger("BoardZ");
            boardId = new UUID(tag.getLong("BoardMost"), tag.getLong("BoardLeast"));
            outwardSide = tag.getByte("Outward");
            if (outwardSide < 0 || outwardSide >= 6) clearBinding();
        } else clearBinding();
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound visual = new NBTTagCompound();
        visual.setByte("Role", (byte) role.ordinal());
        visual.setByte("Outward", (byte) outwardSide);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, visual);
    }

    @Override
    public void onDataPacket(NetworkManager manager, S35PacketUpdateTileEntity packet) {
        NBTTagCompound visual = packet.func_148857_g();
        int value = visual.getByte("Role");
        if (value >= 0 && value < Role.values().length) role = Role.values()[value];
        int side = visual.getByte("Outward");
        outwardSide = side >= 0 && side < 6 ? side : -1;
        if (worldObj != null) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void invalidate() {
        invalidateNetwork();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        invalidateNetwork();
        super.onChunkUnload();
    }

    private AdjacentBoard findSingleAdjacentBoard() {
        AdjacentBoard found = null;
        for (int side = 0; side < 6; side++) {
            int nx = xCoord + dx(side), ny = yCoord + dy(side), nz = zCoord + dz(side);
            if (!worldObj.getChunkProvider().chunkExists(nx >> 4, nz >> 4)) continue;
            if (!(worldObj.getBlock(nx, ny, nz) instanceof BlockRoboBoard)) continue;
            TileEntity tile = worldObj.getTileEntity(nx, ny, nz);
            if (!(tile instanceof TileEntityRoboBoard) || found != null) return null;
            found = new AdjacentBoard(nx, ny, nz, side, (TileEntityRoboBoard) tile);
        }
        return found;
    }

    private void clearBinding() {
        boardId = null;
        outwardSide = -1;
        if (worldObj != null) markDirty();
    }

    private void invalidateNetwork() {
        if (worldObj != null && !worldObj.isRemote)
            ElectricalNetworkManager.forWorld(worldObj).invalidateAround(new BlockPosition(xCoord, yCoord, zCoord));
    }

    private Role firstAvailableDigitalRole() {
        for (int pin = 0; pin < RoboBoardState.OUTPUT_PIN_COUNT; pin++) {
            Role candidate = Role.values()[pin];
            if (isDigitalRoleAvailable(candidate)) return candidate;
        }
        return Role.D0;
    }

    private boolean isDigitalRoleAvailable(Role candidate) {
        if (!candidate.isDigital() || worldObj == null || boardId == null) return true;
        for (int side = 0; side < 6; side++) {
            int x = boardX + dx(side), y = boardY + dy(side), z = boardZ + dz(side);
            if (x == xCoord && y == yCoord && z == zCoord) continue;
            if (!worldObj.getChunkProvider().chunkExists(x >> 4, z >> 4)) continue;
            TileEntity other = worldObj.getTileEntity(x, y, z);
            if (other instanceof TileEntityRoboPort && ((TileEntityRoboPort) other).role == candidate) return false;
        }
        return true;
    }

    private void requireServer() {
        if (worldObj == null || worldObj.isRemote) throw new IllegalStateException("RoboPort mutations require the server world");
    }

    private static int opposite(int side) { return side == 0 ? 1 : side == 1 ? 0 : side == 2 ? 3 : side == 3 ? 2 : side == 4 ? 5 : 4; }
    private static int dx(int side) { return side == 4 ? -1 : side == 5 ? 1 : 0; }
    private static int dy(int side) { return side == 0 ? -1 : side == 1 ? 1 : 0; }
    private static int dz(int side) { return side == 2 ? -1 : side == 3 ? 1 : 0; }

    public Role getRole() { return role; }
    public int getLogicalPin() { return role.getPin(); }
    public long getRevision() { return revision; }
    public int getOutwardSide() {
        return worldObj != null && worldObj.isRemote ? outwardSide : validateBinding() ? outwardSide : -1;
    }
    public UUID getOwnerBoardId() { return boardId; }
    public int getOwnerBoardX() { return boardX; }
    public int getOwnerBoardY() { return boardY; }
    public int getOwnerBoardZ() { return boardZ; }

    private static final class AdjacentBoard {
        final int x, y, z, side;
        final TileEntityRoboBoard tile;
        AdjacentBoard(int x, int y, int z, int side, TileEntityRoboBoard tile) {
            this.x = x; this.y = y; this.z = z; this.side = side; this.tile = tile;
        }
    }
}
