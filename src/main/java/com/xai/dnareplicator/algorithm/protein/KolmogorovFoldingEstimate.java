package com.xai.dnareplicator.algorithm.protein;

/** Kolmogorov-complexity style folding difficulty estimate (educational). */
public class KolmogorovFoldingEstimate {

    public double estimateFoldingDifficulty(String sequence) {
        if (sequence == null || sequence.isEmpty()) {
            return 1.0;
        }
        String compressed = compressSequence(sequence);
        double compressionRatio = (double) compressed.length() / sequence.length();
        return Math.max(0.0, Math.min(1.0, compressionRatio));
    }

    private String compressSequence(String sequence) {
        StringBuilder compressed = new StringBuilder();
        char current = sequence.charAt(0);
        int count = 1;
        for (int i = 1; i < sequence.length(); i++) {
            if (sequence.charAt(i) == current) {
                count++;
            } else {
                compressed.append(current).append(count);
                current = sequence.charAt(i);
                count = 1;
            }
        }
        compressed.append(current).append(count);
        return compressed.toString();
    }
}
