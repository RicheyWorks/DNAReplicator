package com.xai.dnareplicator.algorithm.graph;

import com.xai.dnareplicator.model.Cell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.ToDoubleBiFunction;

/**
 * Dijkstra shortest path on a cell graph (CLRS Chapter 24).
 */
public final class DijkstraPathfinder {

    private DijkstraPathfinder() {
    }

    public static List<Cell> findPath(
            Cell start,
            Cell target,
            Iterable<Cell> graph,
            ToDoubleBiFunction<Cell, Cell> edgeWeight) {
        if (start == null || target == null) {
            return List.of();
        }

        Map<Cell, Double> distances = new HashMap<>();
        Map<Cell, Cell> predecessors = new HashMap<>();
        for (Cell cell : graph) {
            distances.put(cell, Double.MAX_VALUE);
            cell.setDistance(Double.MAX_VALUE);
        }
        distances.put(start, 0.0);
        start.setDistance(0.0);

        PriorityQueue<Cell> pq = new PriorityQueue<>(Comparator.comparingDouble(Cell::getDistance));
        pq.add(start);

        while (!pq.isEmpty()) {
            Cell current = pq.poll();
            if (current == target) {
                break;
            }
            for (Cell neighbor : current.getNeighbors()) {
                double weight = edgeWeight.applyAsDouble(current, neighbor);
                double candidate = distances.get(current) + weight;
                if (candidate < distances.get(neighbor)) {
                    distances.put(neighbor, candidate);
                    predecessors.put(neighbor, current);
                    neighbor.setDistance(candidate);
                    pq.add(neighbor);
                }
            }
        }

        List<Cell> path = new ArrayList<>();
        Cell current = target;
        while (current != null) {
            path.add(current);
            current = predecessors.get(current);
        }
        java.util.Collections.reverse(path);
        if (path.isEmpty() || path.get(0) != start) {
            return List.of();
        }
        return path;
    }
}
