package com.xai.dnareplicator.algorithm.protein;

import com.xai.dnareplicator.config.Config;
import com.xai.dnareplicator.domain.protein.AminoAcid;
import com.xai.dnareplicator.domain.protein.FoldingPathway;
import com.xai.dnareplicator.domain.protein.ProteinBond;

import java.util.ArrayList;
import java.util.List;

/** Builds possible bonds and genetic operators for folding pathways. */
public final class PathwayBondFactory {

    private PathwayBondFactory() {
    }

    public static List<ProteinBond> computePossibleBonds(List<AminoAcid> aminoAcids) {
        List<ProteinBond> bonds = new ArrayList<>();
        for (int i = 0; i < aminoAcids.size(); i++) {
            for (int j = i + 1; j < aminoAcids.size(); j++) {
                double energy = Config.RAND.nextDouble() * Config.BOND_ENERGY_THRESHOLD;
                String direction = Config.RAND.nextDouble() < 0.5 ? "L" : "R";
                bonds.add(new ProteinBond(aminoAcids.get(i), aminoAcids.get(j), energy, direction));
            }
        }
        return bonds;
    }

    public static FoldingPathway crossover(FoldingPathway parent1, FoldingPathway parent2) {
        List<ProteinBond> childBonds = new ArrayList<>();
        int split = Config.RAND.nextInt(Math.max(1, Math.min(parent1.getBonds().size(), parent2.getBonds().size())));
        if (!parent1.getBonds().isEmpty()) {
            childBonds.addAll(parent1.getBonds().subList(0, Math.min(split, parent1.getBonds().size())));
        }
        if (split < parent2.getBonds().size()) {
            childBonds.addAll(parent2.getBonds().subList(split, parent2.getBonds().size()));
        }
        return new FoldingPathway(childBonds);
    }

    public static FoldingPathway mutate(FoldingPathway pathway, List<ProteinBond> possibleBonds) {
        List<ProteinBond> bonds = new ArrayList<>(pathway.getBonds());
        if (!bonds.isEmpty() && !possibleBonds.isEmpty()) {
            int index = Config.RAND.nextInt(bonds.size());
            bonds.set(index, possibleBonds.get(Config.RAND.nextInt(possibleBonds.size())));
        }
        return new FoldingPathway(bonds);
    }
}
