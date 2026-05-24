package com.xai.dnareplicator.domain.protein;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Candidate folding pathway for genetic / annealing search.
 */
public class FoldingPathway {

    private final List<ProteinBond> bonds;
    private final double fitness;
    private double normalizedFitness;
    private double compositeScore;

    public FoldingPathway(List<ProteinBond> bonds) {
        this.bonds = new ArrayList<>(bonds);
        this.fitness = calculateFitness();
        this.normalizedFitness = fitness;
        this.compositeScore = fitness;
    }

    private double calculateFitness() {
        double totalEnergy = bonds.stream().mapToDouble(ProteinBond::getEnergy).sum();
        return 1.0 / (1.0 + totalEnergy);
    }

    public List<ProteinBond> getBonds() {
        return bonds;
    }

    public double getFitness() {
        return fitness;
    }

    public double getNormalizedFitness() {
        return normalizedFitness;
    }

    public void setNormalizedFitness(double normalizedFitness) {
        this.normalizedFitness = normalizedFitness;
    }

    public double getCompositeScore() {
        return compositeScore;
    }

    public void setCompositeScore(double compositeScore) {
        this.compositeScore = compositeScore;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> json = new LinkedHashMap<>();
        List<Map<String, Object>> bondsArray = new ArrayList<>();
        for (ProteinBond bond : bonds) {
            bondsArray.add(bond.toMap());
        }
        json.put("bonds", bondsArray);
        json.put("fitness", fitness);
        json.put("normalizedFitness", normalizedFitness);
        json.put("compositeScore", compositeScore);
        return json;
    }
}
