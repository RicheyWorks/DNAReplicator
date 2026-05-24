package com.xai.dnareplicator.algorithm.graph;

import com.xai.dnareplicator.model.Cell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DijkstraPathfinderTest {

    private Cell a;
    private Cell b;
    private Cell c;

    @BeforeEach
    void setUp() {
        a = new Cell(0, 0, 0.1);
        b = new Cell(50, 0, 0.1);
        c = new Cell(100, 0, 0.1);
        a.addNeighbor(b);
        b.addNeighbor(c);
        b.addNeighbor(a);
        c.addNeighbor(b);
    }

    @Test
    void findPath_returnsShortestRoute() {
        List<Cell> path = DijkstraPathfinder.findPath(a, c, List.of(a, b, c), (from, to) -> 1.0);
        assertFalse(path.isEmpty());
        assertEquals(a, path.get(0));
        assertEquals(c, path.get(path.size() - 1));
    }
}
