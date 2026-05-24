package com.xai.dnareplicator.application.protein;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xai.dnareplicator.algorithm.protein.AminoAcidSplayTree;
import com.xai.dnareplicator.algorithm.protein.FibonacciBondHeap;
import com.xai.dnareplicator.algorithm.protein.FoldingConstraintSolver;
import com.xai.dnareplicator.algorithm.protein.FoldingHmm;
import com.xai.dnareplicator.algorithm.protein.FoldingPathwayScorer;
import com.xai.dnareplicator.algorithm.protein.GeneticFoldingStrategy;
import com.xai.dnareplicator.algorithm.protein.KolmogorovFoldingEstimate;
import com.xai.dnareplicator.algorithm.protein.MctsFoldingEvaluator;
import com.xai.dnareplicator.algorithm.protein.MutationPathwayTree;
import com.xai.dnareplicator.algorithm.protein.PathwayBondFactory;
import com.xai.dnareplicator.algorithm.protein.ProteinFoldingConstants;
import com.xai.dnareplicator.algorithm.protein.SimulatedAnnealingStrategy;
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
    private final GeneticFoldingStrategy geneticStrategy;
    private final SimulatedAnnealingStrategy annealingStrategy;
    private double chaperoneLevel = 1.0;

    public ProteinFoldingOrchestrator(ObjectMapper objectMapper) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper cannot be null");
        }
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.geneticStrategy = new GeneticFoldingStrategy(executor);
        this.annealingStrategy = new SimulatedAnnealingStrategy(executor);
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
        boolean noetherSuccess = FoldingPathwayScorer.checkNoetherSymmetry(approxBonds);
        String foldingState = foldingHMM.predictFoldingState(sequence);
        boolean hmmSuccess = !"coil".equals(foldingState);
        GeneticFoldingStrategy.PathwayMutationListener listener = mutationTree::addPathway;
        List<FoldingPathway> gaPopulation = geneticStrategy.evolve(aminoAcids, listener);
        FoldingPathway gaPathway = gaPopulation.stream().max(Comparator.comparingDouble(FoldingPathway::getCompositeScore)).orElse(gaPopulation.get(0));
        boolean gaSuccess = gaPathway.getCompositeScore() >= 0.5;
        List<FoldingPathway> saPopulation = annealingStrategy.anneal(aminoAcids, listener);
        FoldingPathway saPathway = saPopulation.stream().max(Comparator.comparingDouble(FoldingPathway::getCompositeScore)).orElse(saPopulation.get(0));
        boolean saSuccess = saPathway.getCompositeScore() >= 0.5;
        MctsFoldingEvaluator mcts = new MctsFoldingEvaluator(successfulPathways);
        boolean mctsSuccess = mcts.evaluatePathway(gaPathway, PathwayBondFactory.computePossibleBonds(aminoAcids)) >= 0.5;
        boolean entropySuccess = FoldingPathwayScorer.calculateFoldingEntropy(Arrays.asList(gaPathway, saPathway)) >= 0.5;
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
            String insight = buildInsight(foldingState, gaPathway, saPathway, mctsSuccess, entropySuccess);
            return new FoldingResult(true, foldingState,
                    "Protein " + protein.getEnzymeType() + " folded as " + foldingState, insight);
        }
        protein.failFold();
        mutationTree.addPathway(gaPathway, null, "FailedFold");
        mutationTree.addPathway(saPathway, gaPathway, "Annealing");
        chaperoneLevel = Math.min(1.5, chaperoneLevel * 1.05);
        mutationTree.visualize();
        String insight = buildInsight(foldingState, gaPathway, saPathway, mctsSuccess, entropySuccess);
        return new FoldingResult(false, foldingState,
                "Protein " + protein.getEnzymeType() + " failed to fold!", insight);
    }

    private String buildInsight(String foldingState, FoldingPathway gaPathway, FoldingPathway saPathway,
                                boolean mctsSuccess, boolean entropySuccess) {
        return String.format(
                "HMM=%s | GA score=%.2f | SA score=%.2f | MCTS=%s | entropy=%s",
                foldingState,
                gaPathway.getCompositeScore(),
                saPathway.getCompositeScore(),
                mctsSuccess ? "ok" : "low",
                entropySuccess ? "ok" : "low");
    }

    // CLRS Chapter 35: Greedy approximation with Fibonacci Heap and CSP
    private List<ProteinBond> approximateProteinFolding(List<AminoAcid> aminoAcids) throws DNAProcessingException {
        if (aminoAcids == null || aminoAcids.isEmpty()) {
            throw new DNAProcessingException("No amino acids provided for folding!");
        }
        if (aminoAcids.size() > Config.MAX_AMINO_ACIDS) {
            throw new DNAProcessingException("Too many amino acids: " + aminoAcids.size());
        }

        List<ProteinBond> possibleBonds = PathwayBondFactory.computePossibleBonds(aminoAcids);
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
