import re
from pathlib import Path

path = Path(__file__).resolve().parents[1] / "src/main/java/com/xai/dnareplicator/application/protein/ProteinFoldingOrchestrator.java"
lines = path.read_text(encoding="utf-8").splitlines(keepends=True)
start = next(i for i, l in enumerate(lines) if "private static class AminoAcid" in l)
end = next(i for i, l in enumerate(lines) if l.strip().startswith("public ProteinService"))
body = lines[end:]

header = """package com.xai.dnareplicator.application.protein;

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
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
    }

"""

text = header + "".join(body)
text = text.replace("public class ProteinService", "public class ProteinFoldingOrchestrator")
text = text.replace("public ProteinService(", "public ProteinFoldingOrchestrator(")

for old, new in [
    ("SimulationSession session, SimulationViewPort viewPort, JavaFxExecutor javaFxExecutor, ObjectMapper objectMapper", "ObjectMapper objectMapper"),
    ("private final List<Protein> proteins;\n    private final SimulationViewPort viewPort;\n    private final JavaFxExecutor javaFxExecutor;\n    private final ObjectMapper objectMapper;\n    private SplayTree aminoAcidTree",
     "private final ObjectMapper objectMapper;\n    private final AminoAcidSplayTree aminoAcidTree"),
    ("new SplayTree()", ""),
    ("private HMM foldingHMM", ""),
    ("new HMM()", ""),
    ("private MutationTree mutationTree", ""),
    ("new MutationTree()", ""),
    ("FibonacciHeap", "FibonacciBondHeap"),
    ("ConstraintPropagator", "FoldingConstraintSolver"),
    ("KolmogorovComplexity", "KolmogorovFoldingEstimate"),
    ("MCTS", "MctsFoldingEvaluator"),
    ("new MctsFoldingEvaluator(", "new MctsFoldingEvaluator("),
    ("HMM.OBSERVATIONS", "FoldingHmm.OBSERVATIONS"),
    (" / KT", " / ProteinFoldingConstants.KT"),
    ("viewPort.updateStatus(\"Failed to read FASTA file", "// FASTA read failed"),
]:
    text = text.replace(old, new)

text = re.sub(r"\bBond\b", "ProteinBond", text)
text = re.sub(r"    public void foldProteins\(\) throws DNAProcessingException \{.*?\n    \}\n\n    // CLRS Chapter 35", 
              "    public FoldingResult foldProtein(Protein protein) throws DNAProcessingException {\n        return foldProteinInternal(protein);\n    }\n\n    private FoldingResult foldProteinInternal(Protein protein) throws DNAProcessingException {\n        if (protein == null) {\n            throw new DNAProcessingException(\"Invalid protein for folding\");\n        }\n        String sequence = inferProteinSequence(protein);\n        List<AminoAcid> aminoAcids = new ArrayList<>();\n        for (int i = 0; i < Math.min(sequence.length(), Config.MAX_AMINO_ACIDS / 10); i++) {\n            AminoAcid acid = new AminoAcid(UUID.randomUUID().toString(), String.valueOf(sequence.charAt(i)), Config.RAND.nextDouble() * 10);\n            aminoAcids.add(acid);\n            aminoAcidTree.insert(acid);\n        }\n        KolmogorovFoldingEstimate kc = new KolmogorovFoldingEstimate();\n        boolean kcSuccess = kc.estimateFoldingDifficulty(sequence) <= 0.8;\n        List<ProteinBond> approxBonds = approximateProteinFolding(aminoAcids);\n        boolean approxSuccess = approxBonds.stream().mapToDouble(ProteinBond::getEnergy).sum() <= Config.BOND_ENERGY_THRESHOLD;\n        boolean noetherSuccess = checkNoetherSymmetry(approxBonds);\n        String foldingState = foldingHMM.predictFoldingState(sequence);\n        boolean hmmSuccess = !\"coil\".equals(foldingState);\n        List<FoldingPathway> gaPopulation = runParallelGeneticAlgorithm(aminoAcids);\n        FoldingPathway gaPathway = gaPopulation.stream().max(Comparator.comparingDouble(FoldingPathway::getCompositeScore)).orElse(gaPopulation.get(0));\n        boolean gaSuccess = gaPathway.getCompositeScore() >= 0.5;\n        List<FoldingPathway> saPopulation = runParallelSimulatedAnnealing(aminoAcids);\n        FoldingPathway saPathway = saPopulation.stream().max(Comparator.comparingDouble(FoldingPathway::getCompositeScore)).orElse(saPopulation.get(0));\n        boolean saSuccess = saPathway.getCompositeScore() >= 0.5;\n        MctsFoldingEvaluator mcts = new MctsFoldingEvaluator(successfulPathways);\n        boolean mctsSuccess = mcts.evaluatePathway(gaPathway, computePossibleBonds(aminoAcids)) >= 0.5;\n        boolean entropySuccess = calculateFoldingEntropy(Arrays.asList(gaPathway, saPathway)) >= 0.5;\n        FoldingConstraintSolver csp = new FoldingConstraintSolver();\n        List<ProteinBond> constrainedBonds = csp.propagateConstraints(approxBonds, 5);\n        boolean cspSuccess = !constrainedBonds.isEmpty() && constrainedBonds.size() >= approxBonds.size() * 0.8;\n        boolean randomSuccess = Config.RAND.nextDouble() < 0.7 * calculateDiffusionFactor() * chaperoneLevel;\n        if (approxSuccess && noetherSuccess && hmmSuccess && gaSuccess && saSuccess && mctsSuccess && entropySuccess && cspSuccess && kcSuccess && randomSuccess) {\n            protein.fold();\n            successfulPathways.add(gaPathway);\n            successfulPathways.add(saPathway);\n            saveSuccessfulPathways();\n            mutationTree.addPathway(gaPathway, null, \"SuccessfulFold\");\n            mutationTree.addPathway(saPathway, gaPathway, \"Annealing\");\n            mutationTree.seedNewPathway(gaPathway);\n            chaperoneLevel = Math.max(0.5, chaperoneLevel * 0.95);\n            return new FoldingResult(true, foldingState, \"Protein \" + protein.getEnzymeType() + \" folded as \" + foldingState);\n        }\n        protein.failFold();\n        mutationTree.addPathway(gaPathway, null, \"FailedFold\");\n        mutationTree.addPathway(saPathway, gaPathway, \"Annealing\");\n        chaperoneLevel = Math.min(1.5, chaperoneLevel * 1.05);\n        mutationTree.visualize();\n        return new FoldingResult(false, foldingState, \"Protein \" + protein.getEnzymeType() + \" failed to fold!\");\n    }\n\n    // CLRS Chapter 35",
              text, count=1, flags=re.DOTALL)

text = re.sub(r"    public double calculateResistanceFactor\(\) \{.*?\n    \}\n\n    // Save successful",
              "    // Save successful", text, count=1, flags=re.DOTALL)

# strip broken constructor validation lines
text = re.sub(r"        if \(viewPort == null\) \{[^}]+\}\n", "", text)
text = re.sub(r"        if \(javaFxExecutor == null\) \{[^}]+\}\n", "", text)
text = re.sub(r"        if \(session == null\) \{[^}]+\}\n", "", text)
text = re.sub(r"        this\.proteins = session\.getProteins\(\);\n        this\.viewPort = viewPort;\n        this\.javaFxExecutor = javaFxExecutor;\n", "", text)
text = re.sub(r"        this\.objectMapper = objectMapper\.copy\(\)\.enable\(SerializationFeature\.INDENT_OUTPUT\);\n        this\.aminoAcidTree = new AminoAcidSplayTree\(\);\n        this\.foldingHMM = new FoldingHmm\(\);\n        this\.chaperoneLevel = 1\.0;.*?\n        this\.mutationTree = new MutationPathwayTree\(\);\n    \}\n",
              "", text, count=1, flags=re.DOTALL)

path.write_text(text, encoding="utf-8")
print("Wrote", path, "lines", len(text.splitlines()))
