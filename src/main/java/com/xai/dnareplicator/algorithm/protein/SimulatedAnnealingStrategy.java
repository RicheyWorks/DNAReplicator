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
 * Parallel simulated annealing for protein folding pathways.
 */
public class SimulatedAnnealingStrategy {

    private final ExecutorService executor;

    public SimulatedAnnealingStrategy(ExecutorService executor) {
        this.executor = executor;
    }

    public List<FoldingPathway> anneal(
            List<AminoAcid> aminoAcids,
            GeneticFoldingStrategy.PathwayMutationListener mutationListener) throws DNAProcessingException {
        if (aminoAcids == null || aminoAcids.isEmpty()) {
            throw new DNAProcessingException("No amino acids for simulated annealing!");
        }

        List<ProteinBond> possibleBonds = PathwayBondFactory.computePossibleBonds(aminoAcids);
        List<Future<FoldingPathway>> futures = new ArrayList<>();
        int numRuns = 10;

        for (int i = 0; i < numRuns; i++) {
            futures.add(executor.submit(() -> runSingleAnnealing(possibleBonds, mutationListener)));
        }

        List<FoldingPathway> population = new ArrayList<>();
        for (Future<FoldingPathway> future : futures) {
            try {
                population.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                Thread.currentThread().interrupt();
                throw new DNAProcessingException("Parallel simulated annealing failed: " + e.getMessage());
            }
        }

        FoldingPathwayScorer.normalizeFitness(population);
        FoldingPathwayScorer.computeCompositeScore(population, FoldingPathwayScorer.calculateFoldingEntropy(population));
        return population;
    }

    private FoldingPathway runSingleAnnealing(
            List<ProteinBond> possibleBonds,
            GeneticFoldingStrategy.PathwayMutationListener mutationListener) {
        List<ProteinBond> currentBonds = new ArrayList<>();
        int initialBondCount = Math.max(1, Config.RAND.nextInt(Math.max(1, possibleBonds.size() / 2)));
        List<ProteinBond> shuffled = new ArrayList<>(possibleBonds);
        Collections.shuffle(shuffled, Config.RAND);
        for (int j = 0; j < initialBondCount && j < shuffled.size(); j++) {
            currentBonds.add(shuffled.get(j));
        }
        FoldingPathway currentPathway = new FoldingPathway(currentBonds);
        FoldingPathway bestPathway = currentPathway;

        double temperature = 1000.0;
        double coolingRate = 0.95;
        for (int k = 0; k < 100; k++) {
            List<ProteinBond> neighborBonds = new ArrayList<>(currentBonds);
            if (!neighborBonds.isEmpty() && Config.RAND.nextDouble() < 0.5) {
                int index = Config.RAND.nextInt(neighborBonds.size());
                neighborBonds.set(index, possibleBonds.get(Config.RAND.nextInt(possibleBonds.size())));
            } else if (neighborBonds.size() < possibleBonds.size()) {
                neighborBonds.add(possibleBonds.get(Config.RAND.nextInt(possibleBonds.size())));
            }
            FoldingPathway neighborPathway = new FoldingPathway(neighborBonds);

            double deltaFitness = neighborPathway.getFitness() - currentPathway.getFitness();
            if (deltaFitness > 0 || Config.RAND.nextDouble() < Math.exp(deltaFitness / temperature)) {
                if (mutationListener != null) {
                    mutationListener.onPathway(neighborPathway, currentPathway, "Annealing");
                }
                currentPathway = neighborPathway;
                currentBonds = new ArrayList<>(neighborPathway.getBonds());
            }
            if (currentPathway.getFitness() > bestPathway.getFitness()) {
                bestPathway = currentPathway;
            }
            temperature *= coolingRate;
        }
        return bestPathway;
    }
}
