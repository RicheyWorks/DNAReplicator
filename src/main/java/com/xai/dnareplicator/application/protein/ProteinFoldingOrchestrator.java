package com.xai.dnareplicator.application.protein;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xai.dnareplicator.algorithm.protein.AminoAcidSplayTree;
import com.xai.dnareplicator.algorithm.protein.FibonacciBondHeap;
import com.xai.dnareplicator.algorithm.protein.FoldingConstraintSolver;
import com.xai.dnareplicator.algorithm.protein.FoldingHmm;
import com.xai.dnareplicator.algorithm.protein.KolmogorovFoldingEstimate;
import com.xai.dnareplicator.algorithm.protein.MctsFoldingEvaluator;
import com.xai.dnareplicator.algorithm.protein.MutationPathwayTree;
import com.xai.dnareplicator.algorithm.protein.ProteinFoldingConstants;
import com.xai.dnareplicator.config.Config;
import com.xai.dnareplicator.controller.DNAProcessingException;
import com.xai.dnareplicator.domain.protein.AminoAcid;
import com.xai.dnareplicator.domain.protein.FoldingPathway;
import com.xai.dnareplicator.domain.protein.ProteinBond;
import com.xai.dnareplicator.model.Protein;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
public class ProteinFoldingOrchestrator {

    private final ObjectMapper objectMapper;
    private final AminoAcidSplayTree aminoAcidTree = new AminoAcidSplayTree();
    private final FoldingHmm foldingHMM = new FoldingHmm();
    private final MutationPathwayTree mutationTree = new MutationPathwayTree();
    private final List<FoldingPathway> successfulPathways = new ArrayList<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private double chaperoneLevel = 1.0;

    public ProteinFoldingOrchestrator(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper cannot be null");
        }
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public FoldingResult foldProtein(Protein protein) throws DNAProcessingException {
        return foldProteinInternal(protein);
    }

    private FoldingResult foldProteinInternal(Protein protein) throws DNAProcessingException {
        if (protein == null) {
            throw new DNAProcessingException("Invalid protein for folding");
        }
        String sequence = inferProteinSequence(protein);
        List<AminoAcid> aminoAcids = new ArrayList<>();
        for (int i = 0; i < Math.min(sequence.length(), Config.MAX_AMINO_ACIDS / 10); i++) {
            AminoAcid acid = new AminoAcid(UUID.randomUUID().toString(), String.valueOf(sequence.charAt(i)), Config.RAND.nextDouble() * 10);
            aminoAcids.add(acid);
            aminoAcidTree.insert(acid);
        }
        KolmogorovFoldingEstimate kc = new KolmogorovFoldingEstimate();
        boolean kcSuccess = kc.estimateFoldingDifficulty(sequence) <= 0.8;
        List<ProteinBond> approxBonds = approximateProteinFolding(aminoAcids);
        boolean approxSuccess = approxBonds.stream().mapToDouble(ProteinBond::getEnergy).sum() <= Config.BOND_ENERGY_THRESHOLD;
        boolean noetherSuccess = checkNoetherSymmetry(approxBonds);
        String foldingState = foldingHMM.predictFoldingState(sequence);
        boolean hmmSuccess = !"coil".equals(foldingState);
        List<FoldingPathway> gaPopulation = runParallelGeneticAlgorithm(aminoAcids);
        FoldingPathway gaPathway = gaPopulation.stream().max(Comparator.comparingDouble(FoldingPathway::getCompositeScore)).orElse(gaPopulation.get(0));
        boolean gaSuccess = gaPathway.getCompositeScore() >= 0.5;
        List<FoldingPathway> saPopulation = runParallelSimulatedAnnealing(aminoAcids);
        FoldingPathway saPathway = saPopulation.stream().max(Comparator.comparingDouble(FoldingPathway::getCompositeScore)).orElse(saPopulation.get(0));
        boolean saSuccess = saPathway.getCompositeScore() >= 0.5;
        MctsFoldingEvaluator mcts = new MctsFoldingEvaluator(successfulPathways);
        boolean mctsSuccess = mcts.evaluatePathway(gaPathway, computePossibleBonds(aminoAcids)) >= 0.5;
        boolean entropySuccess = calculateFoldingEntropy(Arrays.asList(gaPathway, saPathway)) >= 0.5;
        FoldingConstraintSolver csp = new FoldingConstraintSolver();
        List<ProteinBond> constrainedBonds = csp.propagateConstraints(approxBonds, 5);
        boolean cspSuccess = !constrainedBonds.isEmpty() && constrainedBonds.size() >= approxBonds.size() * 0.8;
        boolean randomSuccess = Config.RAND.nextDouble() < 0.7 * calculateDiffusionFactor() * chaperoneLevel;
        if (approxSuccess && noetherSuccess && hmmSuccess && gaSuccess && saSuccess && mctsSuccess && entropySuccess && cspSuccess && kcSuccess && randomSuccess) {
            protein.fold();
            successfulPathways.add(gaPathway);
            successfulPathways.add(saPathway);
            saveSuccessfulPathways();
            mutationTree.addPathway(gaPathway, null, "SuccessfulFold");
            mutationTree.addPathway(saPathway, gaPathway, "Annealing");
            mutationTree.seedNewPathway(gaPathway);
            chaperoneLevel = Math.max(0.5, chaperoneLevel * 0.95);
            return new FoldingResult(true, foldingState, "Protein " + protein.getEnzymeType() + " folded as " + foldingState);
        }
        protein.failFold();
        mutationTree.addPathway(gaPathway, null, "FailedFold");
        mutationTree.addPathway(saPathway, gaPathway, "Annealing");
        chaperoneLevel = Math.min(1.5, chaperoneLevel * 1.05);
        mutationTree.visualize();
        return new FoldingResult(false, foldingState, "Protein " + protein.getEnzymeType() + " failed to fold!");
    }

    // CLRS Chapter 35: Greedy approximation with Fibonacci Heap and CSP
    private List<ProteinBond> approximateProteinFolding(List<AminoAcid> aminoAcids) throws DNAProcessingException {
        if (aminoAcids == null || aminoAcids.isEmpty()) {
            throw new DNAProcessingException("No amino acids provided for folding!");
        }
        if (aminoAcids.size() > Config.MAX_AMINO_ACIDS) {
            throw new DNAProcessingException("Too many amino acids: " + aminoAcids.size());
        }

        List<ProteinBond> possibleBonds = computePossibleBonds(aminoAcids);
        // Apply constraint propagation (Knuth/CSP)
        FoldingConstraintSolver csp = new FoldingConstraintSolver();
        possibleBonds = csp.propagateConstraints(possibleBonds, 5); // Max direction imbalance = 5
        if (possibleBonds.isEmpty()) {
            throw new DNAProcessingException("No bonds satisfy CSP constraints!");
        }

        // Use Fibonacci Heap for fast bond retrieval (CLRS Chapter 19)
        FibonacciBondHeap heap = new FibonacciBondHeap();
        Set<ProteinBond> selectedBondsSet = new HashSet<>();
        List<ProteinBond> selectedBonds = new ArrayList<>();
        Set<AminoAcid> covered = new HashSet<>();

        // Insert bonds into Fibonacci Heap
        for (ProteinBond bond : possibleBonds) {
            if (!selectedBondsSet.contains(bond) &&
                (!covered.contains(bond.getAminoAcid1()) || !covered.contains(bond.getAminoAcid2()))) {
                heap.insert(bond, bond.getEnergy());
            }
        }

        while (!heap.isEmpty() && covered.size() < aminoAcids.size()) {
            ProteinBond bestBond = heap.extractMin();
            if (bestBond == null) break;

            if (!selectedBondsSet.contains(bestBond) &&
                (!covered.contains(bestBond.getAminoAcid1()) || !covered.contains(bestBond.getAminoAcid2()))) {
                selectedBonds.add(bestBond);
                selectedBondsSet.add(bestBond);
                covered.add(bestBond.getAminoAcid1());
                covered.add(bestBond.getAminoAcid2());
            }
        }

        double boltzmannProbability = calculateBoltzmannProbability(selectedBonds, possibleBonds, aminoAcids);
        if (boltzmannProbability < 0.1) { // Require minimum probability
            throw new DNAProcessingException("Selected bonds have insufficient Boltzmann probability: " + String.format("%.4f", boltzmannProbability));
        }
        return selectedBonds;
    }

    // Thermodynamic integration: Calculate Boltzmann probability using partition function
    private double calculateBoltzmannProbability(List<ProteinBond> selectedBonds, List<ProteinBond> possibleBonds, List<AminoAcid> aminoAcids) {
        double selectedEnergy = selectedBonds.stream().mapToDouble(ProteinBond::getEnergy).sum();
        // Approximate partition function Z by sampling bond configurations
        double Z = 0.0;
        int sampleSize = Math.min(100, possibleBonds.size()); // Limit for performance
        Set<AminoAcid> covered = new HashSet<>();
        List<ProteinBond> sampleConfig = new ArrayList<>();

        // Include selected bonds as one configuration
        Z += Math.exp(-selectedEnergy / ProteinFoldingConstants.KT);

        // Sample other configurations
        for (int i = 0; i < sampleSize; i++) {
            sampleConfig.clear();
            covered.clear();
            Collections.shuffle(possibleBonds, Config.RAND);
            for (ProteinBond bond : possibleBonds) {
                if (!covered.contains(bond.getAminoAcid1()) || !covered.contains(bond.getAminoAcid2())) {
                    sampleConfig.add(bond);
                    covered.add(bond.getAminoAcid1());
                    covered.add(bond.getAminoAcid2());
                    if (covered.size() >= aminoAcids.size()) break;
                }
            }
            double configEnergy = sampleConfig.stream().mapToDouble(ProteinBond::getEnergy).sum();
            Z += Math.exp(-configEnergy / ProteinFoldingConstants.KT);
        }

        // Calculate Boltzmann probability: P = e^(-E/kT) / Z
        double probability = Math.exp(-selectedEnergy / ProteinFoldingConstants.KT) / Z;
        return Math.max(0.0, Math.min(1.0, probability));
    }

    // Noether-inspired symmetry check
    private boolean checkNoetherSymmetry(List<ProteinBond> bonds) {
        if (bonds == null || bonds.isEmpty()) {
            return false;
        }
        int leftBias = (int) bonds.stream().filter(b -> "L".equals(b.getDirection())).count();
        int rightBias = (int) bonds.stream().filter(b -> "R".equals(b.getDirection())).count();
        int totalBonds = bonds.size();
        double symmetryScore = totalBonds == 0 ? 0 : 1.0 - Math.abs(leftBias - rightBias) / (double) totalBonds;
        return symmetryScore >= 0.8;
    }

    // Shannon-inspired entropy calculation (MacKay)
    private double calculateFoldingEntropy(List<FoldingPathway> pathways) {
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
            entropy -= p * Math.log(p) / Math.log(2); // Shannon entropy in bits
        }
        double maxEntropy = Math.log(pathways.size()) / Math.log(2);
        return maxEntropy == 0 ? 0 : entropy / maxEntropy;
    }

    // Gauss-inspired fitness normalization
    private void normalizeFitness(List<FoldingPathway> pathways) {
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

    // Advanced composite scoring (Alberts, Dayan & Abbott)
    private void computeCompositeScore(List<FoldingPathway> pathways, double entropy) {
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
            double entropyScore = entropy;
            double plausibilityScore = estimateBiologicalPlausibility(pathway);
            double compositeScore = energyWeight * energyScore +
                                   symmetryWeight * symmetryScore +
                                   entropyWeight * entropyScore +
                                   plausibilityWeight * plausibilityScore;
            pathway.setCompositeScore(Math.max(0, Math.min(1, compositeScore)));
        }
    }

    // Simplified biological plausibility (Alberts-inspired)
    private double estimateBiologicalPlausibility(FoldingPathway pathway) {
        double meanEnergy = pathway.getBonds().stream().mapToDouble(ProteinBond::getEnergy).average().orElse(0.0);
        double variance = pathway.getBonds().stream()
            .mapToDouble(b -> Math.pow(b.getEnergy() - meanEnergy, 2))
            .average()
            .orElse(0.0);
        return variance == 0 ? 0.5 : Math.exp(-variance / Config.BOND_ENERGY_THRESHOLD);
    }

    // Parallel genetic algorithm
    private List<FoldingPathway> runParallelGeneticAlgorithm(List<AminoAcid> aminoAcids) throws DNAProcessingException {
        if (aminoAcids == null || aminoAcids.isEmpty()) {
            throw new DNAProcessingException("No amino acids for genetic algorithm!");
        }

        int populationSize = 50;
        List<FoldingPathway> population = new ArrayList<>();
        List<ProteinBond> possibleBonds = computePossibleBonds(aminoAcids);
        for (int i = 0; i < populationSize; i++) {
            List<ProteinBond> randomBonds = new ArrayList<>();
            int bondCount = Config.RAND.nextInt(possibleBonds.size() / 2) + 1;
            Collections.shuffle(possibleBonds, Config.RAND);
            for (int j = 0; j < bondCount && j < possibleBonds.size(); j++) {
                randomBonds.add(possibleBonds.get(j));
            }
            population.add(new FoldingPathway(randomBonds));
        }

        int generations = 20;
        for (int gen = 0; gen < generations; gen++) {
            normalizeFitness(population); // Gauss: Normalize fitness
            double entropy = calculateFoldingEntropy(population); // Shannon/MacKay
            computeCompositeScore(population, entropy); // Advanced scoring
            population.sort((p1, p2) -> Double.compare(p2.getCompositeScore(), p1.getCompositeScore()));
            List<FoldingPathway> newPopulation = new ArrayList<>(population.subList(0, populationSize / 2));

            List<Future<FoldingPathway>> futures = new ArrayList<>();

// ✅ FIX: create effectively final copy
List<FoldingPathway> currentPopulation = population;

for (int i = newPopulation.size(); i < populationSize; i++) {
    futures.add(executor.submit(() -> {
        FoldingPathway parent1 = currentPopulation.get(Config.RAND.nextInt(populationSize / 2));
        FoldingPathway parent2 = currentPopulation.get(Config.RAND.nextInt(populationSize / 2));
                    FoldingPathway child = crossover(parent1, parent2);
                    if (Config.RAND.nextDouble() < 0.1) {
                        child = mutate(child, possibleBonds);
                    }
                    mutationTree.addPathway(child, parent1, Config.RAND.nextDouble() < 0.5 ? "Crossover" : "Mutation");
                    return child;
                }));
            }

            for (Future<FoldingPathway> future : futures) {
                try {
                    newPopulation.add(future.get());
                } catch (InterruptedException | ExecutionException e) {
                    throw new DNAProcessingException("Parallel genetic algorithm failed: " + e.getMessage());
                }
            }
            population = newPopulation;
        }
        normalizeFitness(population); // Gauss: Final normalization
        computeCompositeScore(population, calculateFoldingEntropy(population)); // Final scoring
        return population;
    }

    // Parallel simulated annealing
    private List<FoldingPathway> runParallelSimulatedAnnealing(List<AminoAcid> aminoAcids) throws DNAProcessingException {
        if (aminoAcids == null || aminoAcids.isEmpty()) {
            throw new DNAProcessingException("No amino acids for simulated annealing!");
        }

        List<ProteinBond> possibleBonds = computePossibleBonds(aminoAcids);
        List<Future<FoldingPathway>> futures = new ArrayList<>();
        int numRuns = 10; // Number of parallel annealing runs

        for (int i = 0; i < numRuns; i++) {
            futures.add(executor.submit(() -> {
                List<ProteinBond> currentBonds = new ArrayList<>();
                int initialBondCount = Config.RAND.nextInt(possibleBonds.size() / 2) + 1;
                Collections.shuffle(possibleBonds, Config.RAND);
                for (int j = 0; j < initialBondCount && j < possibleBonds.size(); j++) {
                    currentBonds.add(possibleBonds.get(j));
                }
                FoldingPathway currentPathway = new FoldingPathway(currentBonds);
                FoldingPathway bestPathway = currentPathway;

                double temperature = 1000.0;
                double coolingRate = 0.95;
                int iterations = 100;

                for (int k = 0; k < iterations; k++) {
                    List<ProteinBond> neighborBonds = new ArrayList<>(currentBonds);
                    if (!neighborBonds.isEmpty() && Config.RAND.nextDouble() < 0.5) {
                        int index = Config.RAND.nextInt(neighborBonds.size());
                        neighborBonds.set(index, possibleBonds.get(Config.RAND.nextInt(possibleBonds.size())));
                    } else {
                        if (neighborBonds.size() < possibleBonds.size()) {
                            neighborBonds.add(possibleBonds.get(Config.RAND.nextInt(possibleBonds.size())));
                        }
                    }
                    FoldingPathway neighborPathway = new FoldingPathway(neighborBonds);

                    double deltaFitness = neighborPathway.getFitness() - currentPathway.getFitness();
                    if (deltaFitness > 0 || Config.RAND.nextDouble() < Math.exp(deltaFitness / temperature)) {
                        currentPathway = neighborPathway;
                        mutationTree.addPathway(neighborPathway, currentPathway, "Annealing");
                    }
                    if (currentPathway.getFitness() > bestPathway.getFitness()) {
                        bestPathway = currentPathway;
                    }
                    temperature *= coolingRate;
                }
                return bestPathway;
            }));
        }

        List<FoldingPathway> population = new ArrayList<>();
        for (Future<FoldingPathway> future : futures) {
            try {
                population.add(future.get());
            } catch (InterruptedException | ExecutionException e) {
                throw new DNAProcessingException("Parallel simulated annealing failed: " + e.getMessage());
            }
        }
        normalizeFitness(population); // Gauss: Normalize fitness
        computeCompositeScore(population, calculateFoldingEntropy(population)); // Advanced scoring
        // Note: Could be extended with GPU (e.g., CUDA/OpenCL) for thousands of pathways
        return population;
    }

    private FoldingPathway crossover(FoldingPathway parent1, FoldingPathway parent2) {
        List<ProteinBond> childBonds = new ArrayList<>();
        int split = Config.RAND.nextInt(Math.min(parent1.getBonds().size(), parent2.getBonds().size()));
        childBonds.addAll(parent1.getBonds().subList(0, split));
        childBonds.addAll(parent2.getBonds().subList(split, parent2.getBonds().size()));
        return new FoldingPathway(childBonds);
    }

    private FoldingPathway mutate(FoldingPathway pathway, List<ProteinBond> possibleBonds) {
        List<ProteinBond> bonds = new ArrayList<>(pathway.getBonds());
        if (!bonds.isEmpty()) {
            int index = Config.RAND.nextInt(bonds.size());
            bonds.set(index, possibleBonds.get(Config.RAND.nextInt(possibleBonds.size())));
        }
        return new FoldingPathway(bonds);
    }

    private List<ProteinBond> computePossibleBonds(List<AminoAcid> aminoAcids) {
        List<ProteinBond> bonds = new ArrayList<>();
        for (int i = 0; i < aminoAcids.size(); i++) {
            for (int j = i + 1; j < aminoAcids.size(); j++) {
                double energy = Config.RAND.nextDouble() * Config.BOND_ENERGY_THRESHOLD;
                String direction = Config.RAND.nextDouble() < 0.5 ? "L" : "R"; // Noether: Assign L/R
                bonds.add(new ProteinBond(aminoAcids.get(i), aminoAcids.get(j), energy, direction));
            }
        }
        return bonds;
    }

    // Pevzner: Dynamic programming for mass spectrometry or FASTA sequence inference
    private String inferProteinSequence(Protein protein) throws DNAProcessingException {
        if (protein == null || protein.getEnzymeType() == null) {
            throw new DNAProcessingException("Invalid protein for sequence inference!");
        }
        // Try FASTA input first (Jones & Pevzner-inspired)
        String sequence = readFastaSequence();
        if (sequence != null && !sequence.isEmpty()) {
            // Simplified translation: Map DNA to amino acids (dummy mapping)
            StringBuilder aminoSeq = new StringBuilder();
            for (int i = 0; i < sequence.length() && aminoSeq.length() < 20; i += 3) {
                String codon = i + 3 <= sequence.length() ? sequence.substring(i, i + 3) : sequence.substring(i);
                aminoSeq.append(translateCodon(codon));
            }
            return aminoSeq.toString();
        }
        // Fallback to random sequence (Pevzner’s mass spectrometry dummy)
        StringBuilder sequenceBuilder = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sequenceBuilder.append(FoldingHmm.OBSERVATIONS[Config.RAND.nextInt(FoldingHmm.OBSERVATIONS.length)]);
        }
        return sequenceBuilder.toString();
    }

    // FASTA sequence reader (Jones & Pevzner-inspired)
    private String readFastaSequence() {
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(Config.FASTA_EXPORT_PATH))) {
            StringBuilder sequence = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith(">")) {
                    sequence.append(line.trim());
                }
            }
            return sequence.toString();
        } catch (java.io.IOException e) {
            // FASTA read failed: " + e.getMessage());
            return null;
        }
    }

    // Simplified codon to amino acid translation
    private String translateCodon(String codon) {
        if (codon.length() < 3) return "A";
        return String.valueOf(codon.charAt(0)); // Simplified for demo
    }

    // Fall: Simplified reaction-diffusion factor
    private double calculateDiffusionFactor() {
        return 0.8 + Config.RAND.nextDouble() * 0.4;
    }

    // Save successful pathways to JSON
    private void saveSuccessfulPathways() {
        try {
            List<Map<String, Object>> pathwaysArray = new ArrayList<>();
            for (FoldingPathway pathway : successfulPathways) {
                pathwaysArray.add(pathway.toMap());
            }
            objectMapper.writeValue(
                    new File(Config.getStatsFilePath() + "_pathways.json"),
                    pathwaysArray);
        } catch (IOException e) {
            // Pathway persistence is best-effort during folding
        }
    }
}
