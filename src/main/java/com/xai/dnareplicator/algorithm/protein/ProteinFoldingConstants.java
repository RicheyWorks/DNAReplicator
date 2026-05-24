package com.xai.dnareplicator.algorithm.protein;

import java.util.Set;

/**
 * Shared constants for educational protein-folding algorithms.
 */
public final class ProteinFoldingConstants {

    public static final int LARGE_PRIME = 10007;
    public static final Set<Integer> KNOWN_HASHES = Set.of(3, 17, 29);
    public static final double BOLTZMANN_CONSTANT = 1.380649e-23;
    public static final double TEMPERATURE = 310.0;
    public static final double KT = BOLTZMANN_CONSTANT * TEMPERATURE;

    private ProteinFoldingConstants() {
    }
}
