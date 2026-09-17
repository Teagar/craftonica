package br.com.craftonica.lesson;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class TeacherActivityTest {
    @Test
    public void parsesAndRoundTripsServerActivity() {
        LessonDefinition activity = TeacherActivityParser.parse("lamp", "source:1:1,ground:1:1,resistor:1:3,led:1:2",
                "led", "current", "0.013", "0.001", "0.05", "all");
        assertEquals("teacher-lamp", activity.getId());
        assertEquals(4, activity.getComponentRules().size());
        assertEquals(LessonDefinition.Quantifier.ALL, activity.getGoals().get(0).getQuantifier());

        TeacherActivityData original = new TeacherActivityData();
        original.put(activity);
        original.assign(activity.getId());
        NBTTagCompound nbt = new NBTTagCompound();
        original.writeToNBT(nbt);
        TeacherActivityData restored = new TeacherActivityData();
        restored.readFromNBT(nbt);
        assertNotNull(restored.getActivity("teacher-lamp"));
        assertEquals("teacher-lamp", restored.getAssignedId());
    }

    @Test
    public void compactSyntaxFitsLegacyChatAndExpandsAliases() {
        String command = "/craftonica teacher create lamp s:1:1,g:1:1,r:1:3,l:1:2 l:i:.013:.001:.05:all";
        assertTrue(command.length() <= 100);
        LessonDefinition activity = TeacherActivityParser.parseCompact("lamp", "s:1:1,g:1:1,r:1:3,l:1:2",
                "l:i:.013:.001:.05:all");
        assertTrue(activity.getAllowedKinds().contains("source"));
        assertTrue(activity.getAllowedKinds().contains("led"));
        assertEquals(LessonDefinition.Quantity.CURRENT, activity.getGoals().get(0).getQuantity());
    }

    @Test
    public void rejectsUnknownKindsAndNonFiniteNumbers() {
        invalid("source:1:1,hacker:1:1", "source", "1");
        invalid("source:1:1", "source", "NaN");
        invalid("source:1:1", "source", "Infinity");
    }

    @Test
    public void aggregateExportContainsNoPlayerIdentity() {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        counts.put("ohm-led-220", 3);
        counts.put("teacher-lamp", 7);
        String csv = TeacherProgressExporter.renderCsv(counts);
        assertTrue(csv.contains("lesson_id,completions"));
        assertTrue(csv.contains("teacher-lamp,7"));
        assertFalse(csv.toLowerCase().contains("uuid"));
        assertFalse(csv.toLowerCase().contains("player"));
        assertFalse(csv.toLowerCase().contains("name"));
    }

    @Test
    public void futureSchemaCannotBeOverwritten() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("SchemaVersion", 2);
        TeacherActivityData data = new TeacherActivityData();
        data.readFromNBT(nbt);
        try {
            data.put(TeacherActivityParser.parse("lamp", "source:1:1", "source", "current",
                    "0.1", "0.01", "0", "any"));
            fail("Schema futuro nao deve ser alterado");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("schema"));
        }
    }

    @Test
    public void malformedActivityDoesNotRemoveValidNeighbors() {
        LessonDefinition valid = TeacherActivityParser.parse("valid", "source:1:1", "source", "current",
                "0.1", "0.01", "0", "any");
        TeacherActivityData original = new TeacherActivityData();
        original.put(valid);
        NBTTagCompound nbt = new NBTTagCompound();
        original.writeToNBT(nbt);
        NBTTagList activities = nbt.getTagList("Activities", 10);
        NBTTagCompound malformed = new NBTTagCompound();
        malformed.setString("Id", "teacher-broken");
        activities.appendTag(malformed);

        TeacherActivityData restored = new TeacherActivityData();
        restored.readFromNBT(nbt);
        assertNotNull(restored.getActivity("teacher-valid"));
        assertEquals(1, restored.getActivityIds().size());
    }

    private void invalid(String rules, String goalKind, String target) {
        try {
            TeacherActivityParser.parse("test", rules, goalKind, "current", target, "0.01", "0.05", "any");
            fail("Entrada invalida aceita");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
