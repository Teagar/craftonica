package br.com.craftonica.lesson;

import br.com.craftonica.network.BlockPosition;
import br.com.craftonica.network.ElectricalNetworkManager;
import br.com.craftonica.network.ElectricalNetworkSnapshot;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

import java.util.Locale;
import java.util.Set;

public final class LessonEngine {
    private final LessonEvaluator evaluator = new LessonEvaluator();

    public void start(EntityPlayerMP player, String lessonId) {
        TeacherActivityData teacher = TeacherActivityData.get(player.worldObj);
        if ("assigned".equals(lessonId)) lessonId = teacher.getAssignedId();
        if (lessonId != null && lessonId.startsWith("teacher-") && !lessonId.equals(teacher.getAssignedId())) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.teacher.not_assigned"));
            return;
        }
        LessonDefinition lesson = resolve(lessonId, teacher);
        if (lesson == null) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.unknown", lessonId));
            return;
        }
        try {
            LessonProgressData.get(player.worldObj).start(player.getUniqueID(), lessonId);
        } catch (IllegalStateException unsupportedData) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.data_readonly"));
            return;
        }
        player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.started", lessonId));
    }

    public void status(EntityPlayerMP player) {
        LessonProgressData data = LessonProgressData.get(player.worldObj);
        String active = data.getActive(player.getUniqueID());
        Set<String> completed = data.getCompleted(player.getUniqueID());
        player.addChatMessage(new ChatComponentTranslation(active == null
                ? "message.craftonica.lesson.no_active" : "message.craftonica.lesson.status", active));
        player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.completed_count", completed.size()));
    }

    public void check(EntityPlayerMP player, BlockPosition anchor) {
        LessonProgressData data = LessonProgressData.get(player.worldObj);
        String active = data.getActive(player.getUniqueID());
        if (active == null) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.no_active"));
            return;
        }
        ElectricalNetworkSnapshot snapshot = ElectricalNetworkManager.forWorld(player.worldObj).getSnapshot(anchor);
        LessonDefinition lesson = resolve(active, TeacherActivityData.get(player.worldObj));
        if (lesson == null) {
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.unknown", active));
            return;
        }
        LessonEvaluation result = evaluator.evaluate(lesson, snapshot);
        if (result.isSuccess()) {
            try {
                data.complete(player.getUniqueID(), active);
            } catch (IllegalStateException unsupportedData) {
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.data_readonly"));
                return;
            }
            player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.success", active));
            return;
        }
        showFailure(player, result.getFailures().get(0));
    }

    private void showFailure(EntityPlayerMP player, LessonEvaluation.Failure failure) {
        switch (failure.getCode()) {
            case PENDING:
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.pending"));
                break;
            case UNSOLVED:
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.unsolved", failure.getSubject()));
                break;
            case FORBIDDEN_COMPONENT:
                BlockPosition p = failure.getPosition();
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.forbidden",
                        failure.getSubject(), p.x, p.y, p.z));
                break;
            case COMPONENT_COUNT:
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.count",
                        failure.getSubject(), (int) failure.getExpected(), (int) failure.getTolerance(),
                        (int) failure.getActual()));
                break;
            case REQUIRED_DIAGNOSTIC:
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.required_diagnostic",
                        failure.getSubject()));
                break;
            case GOAL_UNAVAILABLE:
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.goal_unavailable",
                        failure.getSubject()));
                break;
            default:
                player.addChatMessage(new ChatComponentTranslation("message.craftonica.lesson.goal",
                        failure.getSubject(), number(failure.getExpected()), number(failure.getTolerance()),
                        number(failure.getActual())));
        }
    }

    private String number(double value) { return String.format(Locale.ROOT, "%.6f", value); }

    private LessonDefinition resolve(String id, TeacherActivityData teacher) {
        LessonDefinition builtIn = LessonCatalog.get(id);
        return builtIn == null ? teacher.getActivity(id) : builtIn;
    }
}
