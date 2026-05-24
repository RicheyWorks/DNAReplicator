package com.xai.dnareplicator.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationSessionTest {

    @Test
    void clearDnaAndProteins_emptiesBothLists() {
        SimulationSession session = new SimulationSession();
        session.getDnaFragments().add(new DNAFragment(0, 0, "A"));
        session.getProteins().add(new Protein(0, 0, "enzyme"));

        session.clearDnaAndProteins();

        assertTrue(session.getDnaFragments().isEmpty());
        assertTrue(session.getProteins().isEmpty());
    }

    @Test
    void sharedLists_sameInstanceReturned() {
        SimulationSession session = new SimulationSession();
        assertEquals(session.getDnaFragments(), session.getDnaFragments());
        assertEquals(session.getProteins(), session.getProteins());
    }
}
