package br.com.craftonica.network;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

public final class BoundedNetworkSearch<T> {
    public interface NeighborProvider<T> {
        Iterable<T> neighbors(T node);
    }

    public static final class Result<T> {
        private final Set<T> nodes;
        private final boolean limitExceeded;

        private Result(Set<T> nodes, boolean limitExceeded) {
            this.nodes = Collections.unmodifiableSet(nodes);
            this.limitExceeded = limitExceeded;
        }

        public Set<T> getNodes() {
            return nodes;
        }

        public boolean isLimitExceeded() {
            return limitExceeded;
        }
    }

    private final int limit;

    public BoundedNetworkSearch(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Network limit must be positive");
        }
        this.limit = limit;
    }

    public Result<T> discover(T start, NeighborProvider<T> provider) {
        Set<T> visited = new LinkedHashSet<T>();
        Deque<T> pending = new ArrayDeque<T>();
        pending.add(start);
        while (!pending.isEmpty()) {
            T current = pending.removeFirst();
            if (visited.contains(current)) {
                continue;
            }
            if (visited.size() == limit) {
                return new Result<T>(visited, true);
            }
            visited.add(current);
            for (T neighbor : provider.neighbors(current)) {
                if (!visited.contains(neighbor)) {
                    pending.addLast(neighbor);
                }
            }
        }
        return new Result<T>(visited, false);
    }
}
