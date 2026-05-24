package com.xai.dnareplicator.algorithm.graph;

import com.xai.dnareplicator.model.Cell;
import com.xai.dnareplicator.model.Virus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * BFS infection spread over a cell adjacency graph (CLRS Chapter 22).
 */
public final class BreadthFirstInfection {

    private BreadthFirstInfection() {
    }

    public static List<Cell> spread(
            Cell startCell,
            Virus virus,
            BiPredicate<Cell, Virus> canInfect,
            Consumer<Cell> onCellInfected) {
        if (startCell == null || virus == null) {
            return List.of();
        }

        List<Cell> infected = new ArrayList<>();
        Queue<Cell> queue = new LinkedList<>();
        Set<Cell> visited = new HashSet<>();
        queue.add(startCell);
        visited.add(startCell);
        infected.add(startCell);
        if (onCellInfected != null) {
            onCellInfected.accept(startCell);
        }

        while (!queue.isEmpty()) {
            Cell current = queue.poll();
            for (Cell neighbor : current.getNeighbors()) {
                if (!visited.contains(neighbor) && canInfect.test(neighbor, virus)) {
                    queue.add(neighbor);
                    visited.add(neighbor);
                    infected.add(neighbor);
                    if (onCellInfected != null) {
                        onCellInfected.accept(neighbor);
                    }
                }
            }
        }
        return infected;
    }
}
