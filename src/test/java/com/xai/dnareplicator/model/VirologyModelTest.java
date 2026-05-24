package com.xai.dnareplicator.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VirologyModelTest {

    private VirologyModel model;

    @BeforeEach
    void setUp() {
        model = new VirologyModel();
    }

    @Test
    void recordInfection_successIncrementsInfected() {
        model.recordInfection(true);
        model.recordInfection(true);
        assertEquals(2, model.getInfectedCells());
        assertEquals(0, model.getResistantCells());
        assertEquals(2, model.getInfectionHistory().size());
    }

    @Test
    void recordInfection_failureIncrementsResistant() {
        model.recordInfection(false);
        assertEquals(0, model.getInfectedCells());
        assertEquals(1, model.getResistantCells());
    }

    @Test
    void reset_clearsCounts() {
        model.recordInfection(true);
        model.recordInfection(false);
        model.reset();
        assertEquals(0, model.getInfectedCells());
        assertEquals(0, model.getResistantCells());
        assertEquals(0, model.getInfectionHistory().size());
    }
}
