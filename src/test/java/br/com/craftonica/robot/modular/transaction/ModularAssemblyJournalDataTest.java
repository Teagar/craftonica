package br.com.craftonica.robot.modular.transaction;

import br.com.craftonica.robot.modular.ComponentOrientation;
import br.com.craftonica.robot.modular.GridVector;
import br.com.craftonica.robot.modular.manifest.ModularManifestTest;
import br.com.craftonica.robot.modular.transaction.forge.ModularAssemblyJournalData;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public final class ModularAssemblyJournalDataTest {
    @Test public void pendingIntentRoundTripsUntilExplicitlyRemoved() {
        UUID transaction = new UUID(1, 8), robot = new UUID(2, 9), owner = new UUID(3, 10);
        ModularAssemblyJournalData original = new ModularAssemblyJournalData();
        original.put(new ModularAssemblyJournalData.Record(transaction, robot, owner,
                ModularAssemblyJournalData.Kind.ASSEMBLE, new GridVector(4, 5, 6),
                ComponentOrientation.NORTH_UP, ModularManifestTest.manifest()));
        NBTTagCompound encoded = new NBTTagCompound(); original.writeToNBT(encoded);
        ModularAssemblyJournalData restored = new ModularAssemblyJournalData(); restored.readFromNBT(encoded);
        assertEquals(1, restored.all().size());
        ModularAssemblyJournalData.Record record = restored.all().get(0);
        assertEquals(transaction, record.transactionId); assertEquals(robot, record.robotId);
        assertArrayEquals(ModularManifestTest.manifest().getFingerprint(), record.manifest.getFingerprint());
        restored.remove(transaction); assertTrue(restored.all().isEmpty());
    }
}
