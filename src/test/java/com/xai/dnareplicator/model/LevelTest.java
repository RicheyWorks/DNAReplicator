package com.xai.dnareplicator.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LevelTest {

    @Test
    void advanceLevel_increasesRequirementsAndResistance() {
        Level level = new Level();
        assertEquals(1, level.getLevel());
        assertEquals(2, level.getFragmentsRequired());

        level.advanceLevel();
        assertEquals(2, level.getLevel());
        assertEquals(4, level.getFragmentsRequired());
        assertEquals(0.2, level.getCellResistance(), 0.001);
    }
}
