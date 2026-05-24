package com.xai.dnareplicator.algorithm.dna;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LongestCommonSubsequenceTest {

    @Test
    void align_returnsCommonSubsequence() {
        String result = LongestCommonSubsequence.align("AGTC", "GTCA");
        assertEquals("GTC", result);
    }

    @Test
    void alignmentScore_fullMatch() {
        double score = LongestCommonSubsequence.alignmentScore("ACGT", "ACGT");
        assertEquals(1.0, score, 0.001);
    }

    @Test
    void alignmentScore_partialMatch() {
        double score = LongestCommonSubsequence.alignmentScore("AGTC", "GTCA");
        assertTrue(score > 0.5 && score < 1.0);
    }
}
