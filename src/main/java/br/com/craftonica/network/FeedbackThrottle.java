package br.com.craftonica.network;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Small deterministic gate used to prevent repeated feedback during rapid invalidations. */
public final class FeedbackThrottle {
    private static final Map<String, Long> LAST_EVENTS = new HashMap<String, Long>();

    private FeedbackThrottle() {
    }

    public static boolean allow(String key, long tick, int cooldownTicks) {
        Long last = LAST_EVENTS.get(key);
        if (last != null && tick - last < cooldownTicks) {
            return false;
        }
        LAST_EVENTS.put(key, tick);
        if (LAST_EVENTS.size() > 256) {
            Iterator<Map.Entry<String, Long>> iterator = LAST_EVENTS.entrySet().iterator();
            while (iterator.hasNext()) {
                if (tick - iterator.next().getValue() >= cooldownTicks) {
                    iterator.remove();
                }
            }
        }
        return true;
    }

    static void clearForTests() {
        LAST_EVENTS.clear();
    }
}
