package com.xai.dnareplicator.algorithm.protein;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KolmogorovFoldingEstimateTest {

    @Test
    void estimateFoldingDifficulty_returnsBoundedValue() {
        KolmogorovFoldingEstimate estimate = new KolmogorovFoldingEstimate();
        double difficulty = estimate.estimateFoldingDifficulty("AAAAGGGG");
        assertTrue(difficulty >= 0.0 && difficulty <= 1.0);
    }
}
