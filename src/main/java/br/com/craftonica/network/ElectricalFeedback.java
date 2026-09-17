package br.com.craftonica.network;

import br.com.craftonica.electrical.CircuitResult;
import br.com.craftonica.electrical.CircuitStatus;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/** Server-owned audiovisual feedback; clients only receive the normal sound/particle packets. */
public final class ElectricalFeedback {
    private static final int EVENT_COOLDOWN_TICKS = 8;

    private ElectricalFeedback() {
    }

    public static void placed(World world, BlockPosition position) {
        if (!world.isRemote) {
            world.playSoundEffect(position.x + 0.5D, position.y + 0.5D, position.z + 0.5D,
                    "random.click", 0.45F, 1.25F);
        }
    }

    public static void switched(World world, BlockPosition position, boolean closed) {
        if (!world.isRemote) {
            world.playSoundEffect(position.x + 0.5D, position.y + 0.5D, position.z + 0.5D,
                    closed ? "random.click" : "random.door_close", 0.65F, closed ? 1.0F : 1.35F);
        }
    }

    public static void networkTransition(World world, BlockPosition position,
                                         CircuitResult previous, CircuitResult current) {
        if (world.isRemote || !FeedbackThrottle.allow(key(world, position), world.getTotalWorldTime(),
                EVENT_COOLDOWN_TICKS) || previous != null && previous.getStatus() == current.getStatus()) {
            return;
        }
        if (current.getStatus() == CircuitStatus.CLOSED) {
            world.playSoundEffect(position.x + 0.5D, position.y + 0.5D, position.z + 0.5D,
                    "random.orb", 0.35F, 1.8F);
            particles(world, "reddust", position, 3);
        } else if (current.getStatus() == CircuitStatus.OVERCURRENT) {
            world.playSoundEffect(position.x + 0.5D, position.y + 0.5D, position.z + 0.5D,
                    "random.fizz", 0.7F, 0.8F);
            particles(world, "smoke", position, 4);
        } else if (isFailure(current.getStatus())) {
            world.playSoundEffect(position.x + 0.5D, position.y + 0.5D, position.z + 0.5D,
                    "random.fizz", 0.45F, 1.2F);
            particles(world, "smoke", position, 2);
        }
    }

    private static boolean isFailure(CircuitStatus status) {
        return status == CircuitStatus.OPEN_CIRCUIT
                || status == CircuitStatus.REVERSED_POLARITY
                || status == CircuitStatus.UNSUPPORTED_TOPOLOGY
                || status == CircuitStatus.NETWORK_TOO_LARGE;
    }

    private static void particles(World world, String particle, BlockPosition position, int count) {
        if (world instanceof WorldServer) {
            ((WorldServer) world).func_147487_a(particle, position.x + 0.5D, position.y + 0.65D,
                    position.z + 0.5D, count, 0.18D, 0.18D, 0.18D, 0.01D);
        }
    }

    private static String key(World world, BlockPosition position) {
        return world.provider.dimensionId + ":" + position.toString();
    }
}
