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

/** Persisted ownership, terminal direction and electrical role for one RoboBoard port. */
public final class TileEntityRoboPort extends TileEntity {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_LINK_DISTANCE = 64;
    public static final double DRIVE_RESISTANCE_OHMS = 25.0;
    public static final double INPUT_PULLUP_RESISTANCE_OHMS = 20000.0;
    public static final double LOGIC_VOLTS = 5.0;

    public enum Role {
        D0, D1, D2, D3, D4, D5, D6, D7, D8, D9,
        D10, D11, D12, D13, A0, A1, A2, A3, A4, A5,
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
    private boolean registered;

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
        return bindToBoard(found.tile, opposite(found.side));
    }

    public boolean bindToBoard(TileEntityRoboBoard board, int terminalSide) {
        requireServer();
        if (board == null || board.getWorldObj() != worldObj || terminalSide < 0 || terminalSide >= 6)
            return false;
        if (revision == Long.MAX_VALUE) return false;
        double dx = xCoord - board.xCoord, dy = yCoord - board.yCoord, dz = zCoord - board.zCoord;
        if (dx * dx + dy * dy + dz * dz > MAX_LINK_DISTANCE * MAX_LINK_DISTANCE) return false;
        RoboPortRegistry.unregister(this);
        boardX = board.xCoord;
        boardY = board.yCoord;
        boardZ = board.zCoord;
        boardId = board.getBoardId();
        outwardSide = terminalSide;
        registered = true;
        RoboPortRegistry.register(this);
        if (role.isDigital() && !isDigitalRoleAvailable(role)) role = firstAvailableDigitalRole();
        revision++;
        markDirty();
        return true;
    }

    public boolean validateBinding() {
        if (worldObj == null || worldObj.isRemote || boardId == null) return false;
        if (!worldObj.getChunkProvider().chunkExists(boardX >> 4, boardZ >> 4)) return false;
        double dx = xCoord - boardX, dy = yCoord - boardY, dz = zCoord - boardZ;
        TileEntity tile = worldObj.getTileEntity(boardX, boardY, boardZ);
        boolean valid = tile instanceof TileEntityRoboBoard
                && boardId.equals(((TileEntityRoboBoard) tile).getBoardId())
                && outwardSide >= 0 && outwardSide < 6
                && dx * dx + dy * dy + dz * dz <= MAX_LINK_DISTANCE * MAX_LINK_DISTANCE;
        if (!valid) clearBinding();
        else if (!registered) {
            registered = true;
            RoboPortRegistry.register(this);
        }
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

    public boolean isBoundTo(TileEntityRoboBoard board) {
        return board != null && validateBinding() && board.getWorldObj() == worldObj
                && boardId.equals(board.getBoardId()) && boardX == board.xCoord
                && boardY == board.yCoord && boardZ == board.zCoord;
    }

    @Override
    public void updateEntity() {
        if (worldObj != null && !worldObj.isRemote && boardId != null && !registered) validateBinding();
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
        RoboPortRegistry.unregister(this);
        registered = false;
        invalidateNetwork();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        RoboPortRegistry.unregister(this);
        registered = false;
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
        RoboPortRegistry.unregister(this);
        registered = false;
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
        TileEntity board = worldObj.getTileEntity(boardX, boardY, boardZ);
        if (!(board instanceof TileEntityRoboBoard)) return false;
        for (TileEntityRoboPort other : RoboPortRegistry.loadedPorts((TileEntityRoboBoard) board))
            if (other != this && other.role == candidate) return false;
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
