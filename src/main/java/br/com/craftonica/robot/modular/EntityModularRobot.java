package br.com.craftonica.robot.modular;

import br.com.craftonica.registry.ModItems;
import br.com.craftonica.robot.modular.transaction.forge.ForgeModularAssemblyService;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluation;
import br.com.craftonica.robot.modular.electrical.MobileElectricalEvaluator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.UUID;

/** Server-authoritative persistent entity for an arbitrary captured rigid assembly. */
public final class EntityModularRobot extends Entity {
    private ModularRobotState state;

    public EntityModularRobot(World world) {
        super(world); setSize(1.0F, 1.0F); preventEntitySpawning = true;
    }

    public static EntityModularRobot create(World world, UUID robotId, UUID ownerId, GridVector anchor,
            ComponentOrientation orientation, br.com.craftonica.robot.modular.manifest.ModularRobotManifest manifest) {
        if (world == null || world.isRemote) throw new IllegalArgumentException("world");
        EntityModularRobot value = new EntityModularRobot(world);
        value.state = new ModularRobotState(robotId, ownerId, anchor, orientation, manifest);
        value.setPosition(anchor.x + 0.5, anchor.y, anchor.z + 0.5);
        return value;
    }

    @Override protected void entityInit() { }
    @Override public void onUpdate() { super.onUpdate(); motionX = motionY = motionZ = 0.0; }

    @Override protected void writeEntityToNBT(NBTTagCompound tag) {
        if (state != null) tag.setTag("CraftonicaModularRobot", state.write());
    }

    @Override protected void readEntityFromNBT(NBTTagCompound tag) {
        try {
            state = ModularRobotState.read(tag.getCompoundTag("CraftonicaModularRobot"));
            GridVector anchor = state.getAnchor(); setPosition(anchor.x + 0.5, anchor.y, anchor.z + 0.5);
        } catch (RuntimeException invalid) {
            setDead();
        }
    }

    @Override public boolean canBeCollidedWith() { return !isDead; }
    @Override public boolean canBePushed() { return false; }
    @Override public boolean interactFirst(EntityPlayer player) {
        if (!worldObj.isRemote && state != null) {
            if (player.getCurrentEquippedItem() != null && player.getCurrentEquippedItem().getItem() == ModItems.WRENCH)
                ForgeModularAssemblyService.disassemble(this, player);
            else {
                MobileElectricalEvaluation electrical = getElectricalEvaluation();
                player.addChatMessage(new ChatComponentText("Robô modular " + state.getRobotId()
                        + " — " + state.getStatus().name() + ", redes="
                        + state.getManifest().getElectricalNetlist().getNetworkCount()
                        + ", diagnósticos=" + electrical.getDiagnostics().size()));
            }
        }
        return true;
    }

    public ModularRobotState getRobotState() { return state; }
    public MobileElectricalEvaluation getElectricalEvaluation() {
        return MobileElectricalEvaluator.evaluate(state == null
                ? br.com.craftonica.robot.modular.electrical.MobileElectricalNetlist.EMPTY
                : state.getManifest().getElectricalNetlist());
    }
}
