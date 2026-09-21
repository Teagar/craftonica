package br.com.craftonica.robot.modular.transaction.forge;

import br.com.craftonica.robot.modular.ComponentCatalog;
import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.EntityModularRobot;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.ModularRobotState;
import br.com.craftonica.robot.modular.StandardComponentCatalog;
import br.com.craftonica.robot.modular.assembly.AssemblyDiscovery;
import br.com.craftonica.robot.modular.assembly.AssemblyDiscoveryResult;
import br.com.craftonica.robot.modular.assembly.AssemblyGraph;
import br.com.craftonica.robot.modular.assembly.AssemblyLimits;
import br.com.craftonica.robot.modular.assembly.DiscoveredComponent;
import br.com.craftonica.robot.modular.assembly.forge.ForgeAssemblyWorldView;
import br.com.craftonica.robot.modular.manifest.ModularBlockSnapshot;
import br.com.craftonica.robot.modular.manifest.ModularRobotManifest;
import br.com.craftonica.robot.modular.electrical.MobileNetlistExtractor;
import br.com.craftonica.robot.modular.transaction.AssemblyTransaction;
import br.com.craftonica.robot.modular.transaction.AssemblyTransactionResult;
import br.com.craftonica.tile.TileEntityRoboBoard;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Server-only bridge from arbitrary discovered blocks to the transactional core. */
public final class ForgeModularAssemblyService {
    private static final ComponentCatalog CATALOG = StandardComponentCatalog.create();
    private ForgeModularAssemblyService() { }

    public static boolean assemble(World world, int x, int y, int z, EntityPlayer player) {
        if (world == null || world.isRemote || player == null) return false;
        recoverLoaded(world);
        GridVector anchor = new GridVector(x, y, z);
        AssemblyDiscoveryResult discovered = new AssemblyDiscovery(CATALOG, AssemblyLimits.PROFILE_1)
                .discover(new ForgeAssemblyWorldView(world), anchor);
        if (!discovered.isValid()) {
            message(player, "Montagem modular inválida: " + discovered.status.name()); return false;
        }
        if (!authorized(world, discovered.graph, player)) {
            message(player, "A RoboBoard da montagem não pertence a você."); return false;
        }
        ModularRobotManifest manifest;
        try { manifest = capture(world, discovered.graph); }
        catch (RuntimeException invalid) { message(player, "Falha ao copiar os componentes: " + invalid.getMessage()); return false; }
        UUID robotId = UUID.randomUUID();
        ModularAssemblyJournalData journal = ModularAssemblyJournalData.get(world);
        UUID transactionId = UUID.randomUUID();
        journal.put(new ModularAssemblyJournalData.Record(transactionId, robotId, player.getUniqueID(),
                ModularAssemblyJournalData.Kind.ASSEMBLE, anchor, discovered.graph.anchorOrientation, manifest));
        ForgeAssemblyTransactionWorld adapter = new ForgeAssemblyTransactionWorld(world,
                discovered.graph.anchorOrientation);
        AssemblyTransactionResult result = AssemblyTransaction.assemble(adapter, robotId,
                player.getUniqueID(), anchor, discovered.graph.anchorOrientation, manifest);
        if (result.status != AssemblyTransactionResult.Status.RECOVERY_REQUIRED) journal.remove(transactionId);
        if (!result.committed()) {
            message(player, "Conversão cancelada com rollback: " + result.status.name()); return false;
        }
        message(player, "Montagem convertida em robô modular terrestre (" + manifest.getModules().size() + " blocos).");
        return true;
    }

    public static boolean disassemble(EntityModularRobot robot, EntityPlayer player) {
        if (robot == null || robot.worldObj == null || robot.worldObj.isRemote || player == null
                || robot.getRobotState() == null) return false;
        recoverLoaded(robot.worldObj);
        ModularRobotState state = robot.getRobotState();
        if (!state.getOwnerId().equals(player.getUniqueID())) { message(player, "Somente o proprietário pode desmontar."); return false; }
        GridVector anchor = state.getAnchor();
        if (StrictMath.abs(robot.posX - (anchor.x + 0.5)) > 0.01 || StrictMath.abs(robot.posY - anchor.y) > 0.01
                || StrictMath.abs(robot.posZ - (anchor.z + 0.5)) > 0.01) {
            message(player, "O robô deve estar alinhado à grade para desmontar."); return false;
        }
        ModularAssemblyJournalData journal = ModularAssemblyJournalData.get(robot.worldObj);
        UUID transactionId = UUID.randomUUID();
        journal.put(new ModularAssemblyJournalData.Record(transactionId, state.getRobotId(), state.getOwnerId(),
                ModularAssemblyJournalData.Kind.DISASSEMBLE, state.getAnchor(), state.getAnchorOrientation(), state.getManifest()));
        AssemblyTransactionResult result = AssemblyTransaction.disassemble(
                new ForgeAssemblyTransactionWorld(robot.worldObj, state.getAnchorOrientation()),
                state.getRobotId(), anchor, state.getAnchorOrientation(), state.getManifest());
        if (result.status != AssemblyTransactionResult.Status.RECOVERY_REQUIRED) journal.remove(transactionId);
        if (!result.committed()) {
            message(player, result.detail != null && result.detail.equals("TARGET_BLOCKED")
                    ? "Desmontagem bloqueada; a entidade permaneceu intacta."
                    : "Desmontagem cancelada com rollback: " + result.status.name());
            return false;
        }
        if (player.getCurrentEquippedItem() != null) player.getCurrentEquippedItem().damageItem(1, player);
        message(player, "Robô modular desmontado sem criar drops."); return true;
    }

    private static ModularRobotManifest capture(World world, AssemblyGraph graph) {
        List<ModularBlockSnapshot> modules = new ArrayList<ModularBlockSnapshot>();
        for (DiscoveredComponent component : graph.getComponents()) {
            GridVector p = component.worldPosition;
            if (!world.blockExists(p.x, p.y, p.z)) throw new IllegalArgumentException("chunk descarregado");
            Block block = world.getBlock(p.x, p.y, p.z);
            Object key = Block.blockRegistry.getNameForObject(block);
            if (key == null) throw new IllegalArgumentException("bloco sem registro");
            TileEntity tile = world.getTileEntity(p.x, p.y, p.z); NBTTagCompound tileData = null;
            if (tile != null) { tileData = new NBTTagCompound(); tile.writeToNBT(tileData); }
            modules.add(new ModularBlockSnapshot(component.type.getId(), component.type.getSchemaVersion(),
                    component.localPosition, component.localOrientation, key.toString(),
                    world.getBlockMetadata(p.x, p.y, p.z) & 15, tileData));
        }
        return new ModularRobotManifest(UUID.randomUUID(), modules, graph.getEdges(),
                MobileNetlistExtractor.extract(modules, CATALOG));
    }

    /** Reconciles journals only when every target chunk is already loaded. Conflicts stay recorded. */
    public static void recoverLoaded(World world) {
        if (world == null || world.isRemote) return;
        ModularAssemblyJournalData data = ModularAssemblyJournalData.get(world);
        for (ModularAssemblyJournalData.Record record : data.all()) {
            ForgeAssemblyTransactionWorld adapter = new ForgeAssemblyTransactionWorld(world, record.orientation);
            boolean ready = true;
            for (ModularBlockSnapshot snapshot : record.manifest.getModules()) {
                GridVector target = record.anchor.add(record.orientation.toWorld(snapshot.localPosition));
                if (!adapter.isLoaded(target)) { ready = false; break; }
            }
            if (!ready) continue;
            boolean mobile = adapter.robotExists(record.robotId), complete = true;
            for (ModularBlockSnapshot snapshot : record.manifest.getModules()) {
                GridVector target = record.anchor.add(record.orientation.toWorld(snapshot.localPosition));
                if (mobile) {
                    if (adapter.matches(target, snapshot) && !adapter.remove(target, snapshot)) complete = false;
                    else if (!adapter.matches(target, snapshot) && !adapter.isLoadedAndEmpty(target)) complete = false;
                } else {
                    if (!adapter.matches(target, snapshot)) {
                        if (!adapter.isLoadedAndEmpty(target) || !adapter.restore(target, snapshot)) complete = false;
                    }
                }
            }
            if (complete) data.remove(record.transactionId);
        }
    }

    private static boolean authorized(World world, AssemblyGraph graph, EntityPlayer player) {
        int boards = 0;
        for (DiscoveredComponent component : graph.getComponents()) {
            if (!StandardComponentCatalog.ROBO_BOARD.equals(component.type.getId())) continue;
            boards++; GridVector p = component.worldPosition; TileEntity tile = world.getTileEntity(p.x, p.y, p.z);
            if (!(tile instanceof TileEntityRoboBoard)
                    || !player.getUniqueID().equals(((TileEntityRoboBoard) tile).getOwnerId())) return false;
        }
        return boards <= 1;
    }

    private static void message(EntityPlayer player, String text) { player.addChatMessage(new ChatComponentText(text)); }
}
