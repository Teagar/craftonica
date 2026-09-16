package br.com.craftonica.network;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BoundedNetworkSearchTest {
    @Test
    public void discoversOnlyConnectedNetwork() {
        final Map<Integer, List<Integer>> graph = new HashMap<Integer, List<Integer>>();
        connect(graph, 1, 2);
        connect(graph, 2, 3);
        connect(graph, 10, 11);

        BoundedNetworkSearch.Result<Integer> result = new BoundedNetworkSearch<Integer>(1024)
                .discover(1, provider(graph));

        assertEquals(3, result.getNodes().size());
        assertFalse(result.getNodes().contains(10));
        assertFalse(result.isLimitExceeded());
    }

    @Test
    public void stopsBeforeAddingNodeBeyondLimit() {
        final Map<Integer, List<Integer>> graph = new HashMap<Integer, List<Integer>>();
        for (int index = 0; index < 1024; index++) {
            connect(graph, index, index + 1);
        }

        BoundedNetworkSearch.Result<Integer> result = new BoundedNetworkSearch<Integer>(1024)
                .discover(0, provider(graph));

        assertEquals(1024, result.getNodes().size());
        assertTrue(result.isLimitExceeded());
    }

    @Test
    public void acceptsNetworkAtExactLimit() {
        final Map<Integer, List<Integer>> graph = new HashMap<Integer, List<Integer>>();
        for (int index = 0; index < 1023; index++) {
            connect(graph, index, index + 1);
        }

        BoundedNetworkSearch.Result<Integer> result = new BoundedNetworkSearch<Integer>(1024)
                .discover(0, provider(graph));

        assertEquals(1024, result.getNodes().size());
        assertFalse(result.isLimitExceeded());
    }

    private BoundedNetworkSearch.NeighborProvider<Integer> provider(final Map<Integer, List<Integer>> graph) {
        return new BoundedNetworkSearch.NeighborProvider<Integer>() {
            @Override
            public Iterable<Integer> neighbors(Integer node) {
                List<Integer> neighbors = graph.get(node);
                return neighbors == null ? Collections.<Integer>emptyList() : neighbors;
            }
        };
    }

    private void connect(Map<Integer, List<Integer>> graph, int first, int second) {
        add(graph, first, second);
        add(graph, second, first);
    }

    private void add(Map<Integer, List<Integer>> graph, int node, int neighbor) {
        List<Integer> neighbors = graph.get(node);
        if (neighbors == null) {
            neighbors = new ArrayList<Integer>();
            graph.put(node, neighbors);
        }
        neighbors.add(neighbor);
    }
}
