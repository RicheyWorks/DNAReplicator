package com.xai.dnareplicator.domain.protein;

/**
 * Amino acid node used during toy protein folding (Gusfield-style compressed sequence).
 */
public class AminoAcid {

    private final String id;
    private final String compressedSequence;
    private final double energy;

    public AminoAcid(String id, String sequence, double energy) {
        this.id = id;
        this.compressedSequence = compressSequence(sequence);
        this.energy = energy;
    }

    public String getId() {
        return id;
    }

    public String getCompressedSequence() {
        return compressedSequence;
    }

    public double getEnergy() {
        return energy;
    }

    private static String compressSequence(String sequence) {
        if (sequence == null || sequence.isEmpty()) {
            return "";
        }
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
