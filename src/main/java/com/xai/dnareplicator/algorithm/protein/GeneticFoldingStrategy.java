package com.xai.dnareplicator.algorithm.protein;

import com.xai.dnareplicator.config.Config;
import com.xai.dnareplicator.controller.DNAProcessingException;
import com.xai.dnareplicator.domain.protein.AminoAcid;
import com.xai.dnareplicator.domain.protein.FoldingPathway;
import com.xai.dnareplicator.domain.protein.ProteinBond;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Parallel genetic algorithm for protein folding pathways.
 */
public class GeneticFoldingStrategy {

    private final ExecutorService executor;

    public GeneticFoldingStrategy(ExecutorService executor) {
        this.executor = executor;
    }

    public List<FoldingPathway> evolve(
            List<AminoAcid> aminoAcids,
            PathwayMutationListener mutationListener) throws DNAProcessingException {
        if (aminoAcids == null || aminoAcids.isEmpty()) {
            throw new DNAProcessingException("No amino acids for genetic algorithm!");
        }

        int populationSize = 50;
        List<ProteinBond> possibleBonds = PathwayBondFactory.computePossibleBonds(aminoAcids);
        List<FoldingPathway> population = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            List<ProteinBond> randomBonds = new ArrayList<>();
            int bondCount = Math.max(1, Config.RAND.nextInt(Math.max(1, possibleBonds.size() / 2)));
            List<ProteinBond> shuffled = new ArrayList<>(possibleBonds);
            Collections.shuffle(shuffled, Config.RAND);
            for (int j = 0; j < bondCount && j < shuffled.size(); j++) {
                randomBonds.add(shuffled.get(j));
            }
            population.add(new FoldingPathway(randomBonds));
        }

        int generations = 20;
        for (int gen = 0; gen < generations; gen++) {
            FoldingPathwayScorer.normalizeFitness(population);
            double entropy = FoldingPathwayScorer.calculateFoldingEntropy(population);
            FoldingPathwayScorer.computeCompositeScore(population, entropy);
            population.sort((p1, p2) -> Double.compare(p2.getCompositeScore(), p1.getCompositeScore()));
            List<FoldingPathway> newPopulation = new ArrayList<>(population.subList(0, populationSize / 2));

            List<FoldingPathway> currentPopulation = population;
            List<Future<FoldingPathway>> futures = new ArrayList<>();
            for (int i = newPopulation.size(); i < populationSize; i++) {
                futures.add(executor.submit(() -> {
                    FoldingPathway parent1 = currentPopulation.get(Config.RAND.nextInt(populationSize / 2));
                    FoldingPathway parent2 = currentPopulation.get(Config.RAND.nextInt(populationSize / 2));
                    FoldingPathway child = PathwayBondFactory.crossover(parent1, parent2);
                    if (Config.RAND.nextDouble() < 0.1) {
                        child = PathwayBondFactory.mutate(child, possibleBonds);
                    }
                    if (mutationListener != null) {
                        String event = Config.RAND.nextDouble() < 0.5 ? "Crossover" : "Mutation";
                        mutationListener.onPathway(child, parent1, event);
                    }
                    return child;
                }));
            }

            for (Future<FoldingPathway> future : futures) {
                try {
                    newPopulation.add(future.get());
                } catch (InterruptedException | ExecutionException e) {
                    Thread.currentThread().interrupt();
                    throw new DNAProcessingException("Parallel genetic algorithm failed: " + e.getMessage());
                }
            }
            population = newPopulation;
        }

        FoldingPathwayScorer.normalizeFitness(population);
        FoldingPathwayScorer.computeCompositeScore(population, FoldingPathwayScorer.calculateFoldingEntropy(population));
        return population;
    }

    @FunctionalInterface
    public interface PathwayMutationListener {
        void onPathway(FoldingPathway pathway, FoldingPathway parent, String eventType);
    }
}
