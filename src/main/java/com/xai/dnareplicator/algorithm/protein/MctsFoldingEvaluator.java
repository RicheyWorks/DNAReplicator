package com.xai.dnareplicator.algorithm.protein;

import com.xai.dnareplicator.config.Config;
import com.xai.dnareplicator.domain.protein.FoldingPathway;
import com.xai.dnareplicator.domain.protein.ProteinBond;

import java.util.ArrayList;
import java.util.List;

/** Monte Carlo tree search style pathway evaluator (educational). */
public class MctsFoldingEvaluator {

    private static class Node {
        FoldingPathway pathway;
        int visits;
        double totalScore;

        Node(FoldingPathway pathway) {
            this.pathway = pathway;
            this.visits = 0;
            this.totalScore = 0.0;
        }
    }

    private final Node root = new Node(null);
    private final List<FoldingPathway> successfulPathways;

    public MctsFoldingEvaluator(List<FoldingPathway> successfulPathways) {
        this.successfulPathways = successfulPathways != null ? successfulPathways : new ArrayList<>();
    }

    public double evaluatePathway(FoldingPathway pathway, List<ProteinBond> possibleBonds) {
        Node node = new Node(pathway);
        root.visits = 0;
        root.totalScore = 0;
        int simulations = 10;
        double totalReward = 0.0;
        for (int i = 0; i < simulations; i++) {
            double reward = simulate(node, possibleBonds);
            node.visits++;
            node.totalScore += reward;
            totalReward += reward;
        }
        return totalReward / simulations;
    }

    private double simulate(Node node, List<ProteinBond> possibleBonds) {
        List<ProteinBond> simBonds = new ArrayList<>(node.pathway.getBonds());
        if (!simBonds.isEmpty() && Config.RAND.nextDouble() < 0.2) {
            int index = Config.RAND.nextInt(simBonds.size());
            simBonds.set(index, possibleBonds.get(Config.RAND.nextInt(possibleBonds.size())));
        }
        FoldingPathway simPathway = new FoldingPathway(simBonds);
        return successfulPathways.contains(simPathway)
                ? simPathway.getCompositeScore()
                : simPathway.getFitness();
    }
}
