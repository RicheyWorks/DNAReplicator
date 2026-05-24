package com.xai.dnareplicator.algorithm.graph;

import com.xai.dnareplicator.model.Cell;
import com.xai.dnareplicator.model.Virus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BreadthFirstInfectionTest {

    @Test
    void spread_visitsReachableCells() {
        Cell start = new Cell(0, 0, 0.0);
        Cell neighbor = new Cell(10, 0, 0.0);
        start.addNeighbor(neighbor);
        neighbor.addNeighbor(start);

        Virus virus = new Virus(0, 0, 0.5, "test", 0.9);
        AtomicInteger visits = new AtomicInteger();

        List<Cell> infected = BreadthFirstInfection.spread(
                start,
                virus,
                (cell, v) -> true,
                cell -> visits.incrementAndGet());

        assertEquals(2, infected.size());
        assertEquals(2, visits.get());
    }
}
