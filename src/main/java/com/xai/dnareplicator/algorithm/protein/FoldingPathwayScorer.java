package com.xai.dnareplicator.algorithm.protein;

import com.xai.dnareplicator.config.Config;
import com.xai.dnareplicator.domain.protein.FoldingPathway;
import com.xai.dnareplicator.domain.protein.ProteinBond;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Scoring helpers for folding pathway populations. */
public final class FoldingPathwayScorer {

    private FoldingPathwayScorer() {
    }

    public static boolean checkNoetherSymmetry(List<ProteinBond> bonds) {
        if (bonds == null || bonds.isEmpty()) {
            return false;
        }
        int leftBias = (int) bonds.stream().filter(b -> "L".equals(b.getDirection())).count();
        int rightBias = (int) bonds.stream().filter(b -> "R".equals(b.getDirection())).count();
        int totalBonds = bonds.size();
        double symmetryScore = 1.0 - Math.abs(leftBias - rightBias) / (double) totalBonds;
        return symmetryScore >= 0.8;
    }

    public static double calculateFoldingEntropy(List<FoldingPathway> pathways) {
        if (pathways == null || pathways.isEmpty()) {
            return 0.0;
        }
        Map<String, Integer> configCounts = new HashMap<>();
        for (FoldingPathway pathway : pathways) {
            String config = pathway.getBonds().stream()
                    .map(b -> b.getAminoAcid1().getId() + "-" + b.getAminoAcid2().getId())
                    .sorted()
                    .collect(Collectors.joining(","));
            configCounts.put(config, configCounts.getOrDefault(config, 0) + 1);
        }
        double entropy = 0.0;
        int total = pathways.size();
        for (int count : configCounts.values()) {
            double p = count / (double) total;
            entropy -= p * (Math.log(p) / Math.log(2));
        }
        double maxEntropy = Math.log(pathways.size()) / Math.log(2);
        return maxEntropy == 0 ? 0 : entropy / maxEntropy;
    }

    public static void normalizeFitness(List<FoldingPathway> pathways) {
        if (pathways == null || pathways.isEmpty()) {
            return;
        }
        double mean = pathways.stream().mapToDouble(FoldingPathway::getFitness).average().orElse(0.0);
        double variance = pathways.stream()
                .mapToDouble(p -> Math.pow(p.getFitness() - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        for (FoldingPathway pathway : pathways) {
            double fitness = pathway.getFitness();
            double normalized = stdDev == 0 ? 0.5 : (fitness - mean) / (2 * stdDev) + 0.5;
            pathway.setNormalizedFitness(Math.max(0, Math.min(1, normalized)));
        }
    }

    public static void computeCompositeScore(List<FoldingPathway> pathways, double entropy) {
        if (pathways == null || pathways.isEmpty()) {
            return;
        }
        double energyWeight = 0.4;
        double symmetryWeight = 0.3;
        double entropyWeight = 0.2;
        double plausibilityWeight = 0.1;

        for (FoldingPathway pathway : pathways) {
            double energyScore = pathway.getNormalizedFitness();
            double symmetryScore = checkNoetherSymmetry(pathway.getBonds()) ? 1.0 : 0.5;
            double plausibilityScore = estimateBiologicalPlausibility(pathway);
            double compositeScore = energyWeight * energyScore
                    + symmetryWeight * symmetryScore
                    + entropyWeight * entropy
                    + plausibilityWeight * plausibilityScore;
            pathway.setCompositeScore(Math.max(0, Math.min(1, compositeScore)));
        }
    }

    private static double estimateBiologicalPlausibility(FoldingPathway pathway) {
        double meanEnergy = pathway.getBonds().stream().mapToDouble(ProteinBond::getEnergy).average().orElse(0.0);
        double variance = pathway.getBonds().stream()
                .mapToDouble(b -> Math.pow(b.getEnergy() - meanEnergy, 2))
                .average()
                .orElse(0.0);
        return variance == 0 ? 0.5 : Math.exp(-variance / Config.BOND_ENERGY_THRESHOLD);
    }
}
