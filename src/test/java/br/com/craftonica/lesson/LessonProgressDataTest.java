package br.com.craftonica.lesson;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class LessonProgressDataTest {
    @Test
    public void roundTripKeepsPlayersIsolatedAndCompletionIdempotent() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        LessonProgressData original = new LessonProgressData();
        assertTrue(original.start(first, "ohm-led-220"));
        assertTrue(original.start(second, "ohm-led-220"));
        assertTrue(original.complete(first, "ohm-led-220"));
        assertFalse(original.complete(first, "ohm-led-220"));

        NBTTagCompound nbt = new NBTTagCompound();
        original.writeToNBT(nbt);
        LessonProgressData restored = new LessonProgressData();
        restored.readFromNBT(nbt);

        assertNull(restored.getActive(first));
        assertEquals("ohm-led-220", restored.getActive(second));
        assertTrue(restored.getCompleted(first).contains("ohm-led-220"));
        assertFalse(restored.getCompleted(second).contains("ohm-led-220"));
    }

    @Test
    public void malformedAndUnknownEntriesDoNotGrantProgress() {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("SchemaVersion", 1);
        NBTTagList players = new NBTTagList();
        NBTTagCompound malformed = new NBTTagCompound();
        malformed.setString("UUID", "not-a-uuid");
        malformed.setString("ActiveLesson", "ohm-led-220");
        players.appendTag(malformed);
        UUID validId = UUID.randomUUID();
        NBTTagCompound unknown = new NBTTagCompound();
        unknown.setString("UUID", validId.toString());
        unknown.setString("ActiveLesson", "unknown");
        players.appendTag(unknown);
        root.setTag("Players", players);

        LessonProgressData restored = new LessonProgressData();
        restored.readFromNBT(root);
        assertNull(restored.getActive(validId));
        assertTrue(restored.getCompleted(validId).isEmpty());
    }

    @Test
    public void futureSchemaCannotBeMutatedByAnOlderVersion() {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("SchemaVersion", 2);
        LessonProgressData restored = new LessonProgressData();
        restored.readFromNBT(root);
        try {
            restored.start(UUID.randomUUID(), "ohm-led-220");
            fail("Schema futuro nao deve ser sobrescrito");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("schema"));
        }
    }
}
