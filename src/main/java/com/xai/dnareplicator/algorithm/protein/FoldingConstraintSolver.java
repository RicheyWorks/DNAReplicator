package com.xai.dnareplicator.algorithm.protein;

import com.xai.dnareplicator.config.Config;
import com.xai.dnareplicator.domain.protein.ProteinBond;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/** Constraint propagation for bond direction and energy (CSP). */
public class FoldingConstraintSolver {

    private static class Constraint {
        ProteinBond bond;
        Set<String> directionDomain;
        double minEnergy;
        double maxEnergy;

        Constraint(ProteinBond bond) {
            this.bond = bond;
            this.directionDomain = new HashSet<>(Arrays.asList("L", "R"));
            this.minEnergy = 0.0;
            this.maxEnergy = Config.BOND_ENERGY_THRESHOLD;
        }
    }

    public List<ProteinBond> propagateConstraints(List<ProteinBond> bonds, int maxDirectionImbalance) {
        if (bonds == null || bonds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Constraint> constraints = bonds.stream()
                .map(Constraint::new)
                .collect(Collectors.toList());

        for (Constraint c : constraints) {
            if (!c.bond.passesModularFilter()) {
                c.directionDomain.clear();
            }
        }

        double meanEnergy = bonds.stream().mapToDouble(ProteinBond::getEnergy).average().orElse(0.0);
        double variance = bonds.stream()
                .mapToDouble(b -> Math.pow(b.getEnergy() - meanEnergy, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);
        double energyLowerBound = Math.max(0.0, meanEnergy - stdDev);
        double energyUpperBound = Math.min(Config.BOND_ENERGY_THRESHOLD, meanEnergy + stdDev);

        for (Constraint c : constraints) {
            if (c.bond.getEnergy() < energyLowerBound || c.bond.getEnergy() > energyUpperBound) {
                c.directionDomain.clear();
            } else {
                c.minEnergy = Math.max(c.minEnergy, energyLowerBound);
                c.maxEnergy = Math.min(c.maxEnergy, energyUpperBound);
            }
        }

        Queue<Constraint> queue = new LinkedList<>(constraints);
        while (!queue.isEmpty()) {
            Constraint c = queue.poll();
            if (c.directionDomain.isEmpty()) {
                continue;
            }
            int leftCount = (int) constraints.stream()
                    .filter(con -> con.directionDomain.contains("L") && con.directionDomain.size() == 1)
                    .count();
            int rightCount = (int) constraints.stream()
                    .filter(con -> con.directionDomain.contains("R") && con.directionDomain.size() == 1)
                    .count();
            int potentialRight = constraints.stream()
                    .filter(con -> con.directionDomain.contains("R"))
                    .mapToInt(con -> con.directionDomain.size())
                    .sum();
            int potentialLeft = constraints.stream()
                    .filter(con -> con.directionDomain.contains("L"))
                    .mapToInt(con -> con.directionDomain.size())
                    .sum();

            if (c.directionDomain.contains("L")
                    && (leftCount - rightCount > maxDirectionImbalance || potentialRight - leftCount < -maxDirectionImbalance)) {
                c.directionDomain.remove("L");
                queue.addAll(constraints);
            }
            if (c.directionDomain.contains("R")
                    && (rightCount - leftCount > maxDirectionImbalance || potentialLeft - rightCount < -maxDirectionImbalance)) {
                c.directionDomain.remove("R");
                queue.addAll(constraints);
            }
        }

        return constraints.stream()
                .filter(c -> !c.directionDomain.isEmpty())
                .map(c -> c.bond)
                .collect(Collectors.toList());
    }
}
