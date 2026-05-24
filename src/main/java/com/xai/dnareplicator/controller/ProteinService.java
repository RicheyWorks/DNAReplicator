package com.xai.dnareplicator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.xai.dnareplicator.model.Protein;
import com.xai.dnareplicator.model.SimulationSession;
import com.xai.dnareplicator.config.Config;
import com.xai.dnareplicator.controller.DNAProcessingException;
import com.xai.dnareplicator.presentation.contract.SimulationViewPort;
import com.xai.dnareplicator.presentation.javafx.JavaFxExecutor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import javafx.concurrent.Task;

import org.springframework.stereotype.Component;

@Component
public class ProteinService {
    private final List<Protein> proteins;
    private final SimulationViewPort viewPort;
    private final JavaFxExecutor javaFxExecutor;
    private final ObjectMapper objectMapper;
    private SplayTree aminoAcidTree; // CLRS Chapter 17: Splay tree for amino acids
    private HMM foldingHMM; // Durbin: HMM for folding states
    private double chaperoneLevel; // Alon: Feedback loop for protein expression
    private MutationTree mutationTree; // Mutation tree with recursive subgraph
    private static final int LARGE_PRIME = 10007; // For Ramanujan hash
    private static final Set<Integer> KNOWN_HASHES = new HashSet<>(Arrays.asList(3, 17, 29)); // Example rare hash residues
    private static final ExecutorService executor = Executors.newFixedThreadPool(4); // Parallelization pool
    private final List<FoldingPathway> successfulPathways = new ArrayList<>(); // ML: Store successful pathways
    private static final double BOLTZMANN_CONSTANT = 1.380649e-23; // J/K
    private static final double TEMPERATURE = 310.0; // K (human body temperature)
    private static final double KT = BOLTZMANN_CONSTANT * TEMPERATURE;

    // Inner class for amino acids (with compressed sequence)
    private static class AminoAcid {
        private String id;
        private String compressedSequence; // Gusfield: Compressed sequence
        private double energy;

        AminoAcid(String id, String sequence, double energy) {
            this.id = id;
            this.compressedSequence = compressSequence(sequence); // Gusfield
            this.energy = energy;
        }

        public String getId() { return id; }
        public String getCompressedSequence() { return compressedSequence; }
        public double getEnergy() { return energy; }

        private String compressSequence(String sequence) {
            if (sequence == null || sequence.isEmpty()) return "";
            StringBuilder compressed = new StringBuilder();
            char current = sequence.charAt(0);
            int count = 1;
            for (int i = 1; i < sequence.length(); i++) {
                if (sequence.charAt(i) == current) {
                    count++;
                } else {
                    compressed.append(current).append(count);
                    current = sequence.charAt(i);
                    count = 1;
                }
            }
            compressed.append(current).append(count);
            return compressed.toString();
        }
    }

    // Inner class for bonds (for CLRS Chapter 35 approximation)
    private static class Bond {
        private AminoAcid aminoAcid1, aminoAcid2;
        private double energy;
        private String direction; // Noether: L/R for symmetry analysis

        Bond(AminoAcid a1, AminoAcid a2, double energy, String direction) {
            this.aminoAcid1 = a1;
            this.aminoAcid2 = a2;
            this.energy = energy;
            this.direction = direction;
        }

        public AminoAcid getAminoAcid1() { return aminoAcid1; }
        public AminoAcid getAminoAcid2() { return aminoAcid2; }
        public double getEnergy() { return energy; }
        public String getDirection() { return direction; }

        // Ramanujan-inspired modular filter
        public boolean passesModularFilter() {
            int energyInt = (int) Math.round(energy * 100); // Convert to integer scale
            int hash = energyInt % LARGE_PRIME;
            return KNOWN_HASHES.contains(hash);
        }

        Map<String, Object> toMap() {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("aminoAcid1", aminoAcid1.getId());
            json.put("aminoAcid2", aminoAcid2.getId());
            json.put("energy", energy);
            json.put("direction", direction);
            return json;
        }
    }

    // Fibonacci Heap for fast bond retrieval (CLRS Chapter 19)
    private static class FibonacciHeap {
        private static class Node {
            Bond bond;
            double key; // Energy as key
            Node parent, child, left, right;
            int degree;
            boolean mark;

            Node(Bond bond, double key) {
                this.bond = bond;
                this.key = key;
                this.left = this;
                this.right = this;
            }
        }

        private Node min;
        private int size;

        public void insert(Bond bond, double key) {
            Node node = new Node(bond, key);
            if (min == null) {
                min = node;
            } else {
                mergeLists(min, node);
                if (node.key < min.key) {
                    min = node;
                }
            }
            size++;
        }

        public Bond extractMin() {
            if (min == null) return null;

            Node z = min;
            Bond result = z.bond;

            // Add children to root list
            if (z.child != null) {
                Node x = z.child;
                do {
                    Node next = x.right;
                    mergeLists(min, x);
                    x.parent = null;
                    x = next;
                } while (x != z.child);
            }

            // Remove z from root list
            if (z == z.right) {
                min = null;
            } else {
                min = z.right;
                z.left.right = z.right;
                z.right.left = z.left;
                consolidate();
            }

            size--;
            return result;
        }

        private void mergeLists(Node a, Node b) {
            Node aRight = a.right;
            Node bLeft = b.left;
            a.right = b;
            b.left = a;
            bLeft.right = aRight;
            aRight.left = bLeft;
        }

        private void consolidate() {
            int maxDegree = (int) Math.floor(Math.log(size) / Math.log(2)) + 1;
            Node[] A = new Node[maxDegree + 1];
            List<Node> roots = new ArrayList<>();
            Node x = min;
            if (x != null) {
                do {
                    roots.add(x);
                    x = x.right;
                } while (x != min);
            }

            for (Node w : roots) {
                x = w;
                int d = x.degree;
                while (A[d] != null) {
                    Node y = A[d];
                    if (x.key > y.key) {
                        Node temp = x;
                        x = y;
                        y = temp;
                    }
                    link(y, x);
                    A[d] = null;
                    d++;
                }
                A[d] = x;
            }

            min = null;
            for (Node node : A) {
                if (node != null) {
                    if (min == null) {
                        min = node;
                        node.left = node;
                        node.right = node;
                    } else {
                        mergeLists(min, node);
                        if (node.key < min.key) {
                            min = node;
                        }
                    }
                }
            }
        }

        private void link(Node y, Node x) {
            // Remove y from root list
            y.left.right = y.right;
            y.right.left = y.left;

            // Make y a child of x
            y.parent = x;
            if (x.child == null) {
                x.child = y;
                y.right = y;
                y.left = y;
            } else {
                mergeLists(x.child, y);
            }

            x.degree++;
            y.mark = false;
        }

        public boolean isEmpty() {
            return min == null;
        }
    }

    // Constraint Propagator for bond direction and energy (Knuth/CSP)
    private static class ConstraintPropagator {
        private static class Constraint {
            Bond bond;
            Set<String> directionDomain; // L, R
            double minEnergy, maxEnergy;

            Constraint(Bond bond) {
                this.bond = bond;
                this.directionDomain = new HashSet<>(Arrays.asList("L", "R"));
                this.minEnergy = 0.0;
                this.maxEnergy = Config.BOND_ENERGY_THRESHOLD;
            }
        }

        public List<Bond> propagateConstraints(List<Bond> bonds, int maxDirectionImbalance) {
            if (bonds == null || bonds.isEmpty()) {
                return new ArrayList<>();
            }

            // Initialize constraints
            List<Constraint> constraints = bonds.stream()
                .map(Constraint::new)
                .collect(Collectors.toList());

            // Apply Ramanujan filter as initial constraint
            for (Constraint c : constraints) {
                if (!c.bond.passesModularFilter()) {
                    c.directionDomain.clear();
                }
            }

            // Apply energy threshold constraint (mean ± std dev)
            double meanEnergy = bonds.stream().mapToDouble(Bond::getEnergy).average().orElse(0.0);
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

            // Arc consistency (AC-3) for direction balance
            Queue<Constraint> queue = new LinkedList<>(constraints);
            while (!queue.isEmpty()) {
                Constraint c = queue.poll();
                if (c.directionDomain.isEmpty()) continue;

                // Check direction balance constraint
                int leftCount = constraints.stream()
                    .filter(con -> con.directionDomain.contains("L"))
                    .mapToInt(con -> con.directionDomain.size() == 1 && con.directionDomain.contains("L") ? 1 : 0)
                    .sum();
                int rightCount = constraints.stream()
                    .filter(con -> con.directionDomain.contains("R"))
                    .mapToInt(con -> con.directionDomain.size() == 1 && con.directionDomain.contains("R") ? 1 : 0)
                    .sum();
                int potentialLeft = constraints.stream()
                    .filter(con -> con.directionDomain.contains("L"))
                    .mapToInt(con -> con.directionDomain.size())
                    .sum();
                int potentialRight = constraints.stream()
                    .filter(con -> con.directionDomain.contains("R"))
                    .mapToInt(con -> con.directionDomain.size())
                    .sum();

                // Prune directions if imbalance exceeds threshold
                if (c.directionDomain.contains("L") && (leftCount - rightCount > maxDirectionImbalance || potentialRight - leftCount < -maxDirectionImbalance)) {
                    c.directionDomain.remove("L");
                    queue.addAll(constraints);
                }
                if (c.directionDomain.contains("R") && (rightCount - leftCount > maxDirectionImbalance || potentialLeft - rightCount < -maxDirectionImbalance)) {
                    c.directionDomain.remove("R");
                    queue.addAll(constraints);
                }
            }

            // Return bonds with non-empty direction domains
            return constraints.stream()
                .filter(c -> !c.directionDomain.isEmpty())
                .map(c -> c.bond)
                .collect(Collectors.toList());
        }
    }

    // Kolmogorov Complexity for sequence compressibility (MacKay)
    private static class KolmogorovComplexity {
        public double estimateFoldingDifficulty(String sequence) {
            if (sequence == null || sequence.isEmpty()) {
                return 1.0; // Maximum difficulty for invalid sequences
            }

            // Approximate Kolmogorov Complexity using RLE compression
            String compressed = compressSequence(sequence);
            double compressionRatio = (double) compressed.length() / sequence.length();

            // Map compression ratio to folding difficulty (0 to 1)
            // Lower compression ratio (more compressible) -> lower difficulty
            // Higher compression ratio (less compressible) -> higher difficulty
            double difficulty = compressionRatio; // Linear mapping for simplicity
            return Math.max(0.0, Math.min(1.0, difficulty));
        }

        private String compressSequence(String sequence) {
            // RLE compression (Gusfield-inspired)
            StringBuilder compressed = new StringBuilder();
            char current = sequence.charAt(0);
            int count = 1;
            for (int i = 1; i < sequence.length(); i++) {
                if (sequence.charAt(i) == current) {
                    count++;
                } else {
                    compressed.append(current).append(count);
                    current = sequence.charAt(i);
                    count = 1;
                }
            }
            compressed.append(current).append(count);
            return compressed.toString();
        }
    }

    // Folding pathway for genetic algorithm and simulated annealing
    private static class FoldingPathway {
        private List<Bond> bonds;
        private double fitness; // Lower energy = higher fitness
        private double normalizedFitness; // Gauss: Normalized fitness
        private double compositeScore; // Advanced scoring

        FoldingPathway(List<Bond> bonds) {
            this.bonds = new ArrayList<>(bonds);
            this.fitness = calculateFitness();
            this.normalizedFitness = fitness;
            this.compositeScore = fitness; // Updated externally
        }

        private double calculateFitness() {
            double totalEnergy = bonds.stream().mapToDouble(Bond::getEnergy).sum();
            return 1.0 / (1.0 + totalEnergy); // Higher fitness for lower energy
        }

        public List<Bond> getBonds() { return bonds; }
        public double getFitness() { return fitness; }
        public double getNormalizedFitness() { return normalizedFitness; }
        public void setNormalizedFitness(double normalizedFitness) { this.normalizedFitness = normalizedFitness; }
        public double getCompositeScore() { return compositeScore; }
        public void setCompositeScore(double compositeScore) { this.compositeScore = compositeScore; }

        Map<String, Object> toMap() {
            Map<String, Object> json = new LinkedHashMap<>();
            List<Map<String, Object>> bondsArray = new ArrayList<>();
            for (Bond bond : bonds) {
                bondsArray.add(bond.toMap());
            }
            json.put("bonds", bondsArray);
            json.put("fitness", fitness);
            json.put("normalizedFitness", normalizedFitness);
            json.put("compositeScore", compositeScore);
            return json;
        }
    }

    // Mutation tree with recursive subgraph (Von Neumann-inspired)
    private static class MutationTree {
        private class Node {
            FoldingPathway pathway;
            List<Node> children;
            String eventType; // Mutation, Crossover, Annealing, Seed, SuccessfulFold, FailedFold

            Node(FoldingPathway pathway, String eventType) {
                this.pathway = pathway;
                this.children = new ArrayList<>();
                this.eventType = eventType;
            }
        }
        private Node root;

        public void addPathway(FoldingPathway pathway, FoldingPathway parent, String eventType) {
            Node newNode = new Node(pathway, eventType);
            if (root == null && parent == null) {
                root = newNode;
            } else if (parent != null) {
                Node parentNode = findNode(root, parent);
                if (parentNode != null) {
                    parentNode.children.add(newNode);
                }
            }
        }

        // Von Neumann: Recursive subgraph for seeding new pathways
        public void seedNewPathway(FoldingPathway successfulPathway) {
            if (successfulPathway.getCompositeScore() >= 0.5) {
                List<Bond> seededBonds = new ArrayList<>(successfulPathway.getBonds());
                if (!seededBonds.isEmpty() && Config.RAND.nextDouble() < 0.2) {
                    int index = Config.RAND.nextInt(seededBonds.size());
                    seededBonds.set(index, new Bond(
                        seededBonds.get(index).getAminoAcid1(),
                        seededBonds.get(index).getAminoAcid2(),
                        Config.RAND.nextDouble() * Config.BOND_ENERGY_THRESHOLD,
                        Config.RAND.nextDouble() < 0.5 ? "L" : "R"
                    ));
                }
                FoldingPathway seededPathway = new FoldingPathway(seededBonds);
                addPathway(seededPathway, successfulPathway, "Seed");
            }
        }

        private Node findNode(Node node, FoldingPathway pathway) {
            if (node == null) return null;
            if (node.pathway == pathway) return node;
            for (Node child : node.children) {
                Node found = findNode(child, pathway);
                if (found != null) return found;
            }
            return null;
        }

        public void visualize() {
            // Placeholder: Visualize tree in SimulationView
            // viewPort.visualizeMutationTree(root);
        }
    }

    // Simplified HMM for folding states (Durbin)
    private static class HMM {
        private static final String[] STATES = {"helix", "sheet", "coil"};
        private static final String[] OBSERVATIONS = {"A", "C", "G", "T"};
        private double[][] transition;
        private double[][] emission;
        private double[] initial;

        HMM() {
            int n = STATES.length, m = OBSERVATIONS.length;
            transition = new double[n][n];
            emission = new double[n][m];
            initial = new double[n];
            for (int i = 0; i < n; i++) {
                initial[i] = 1.0 / n;
                for (int j = 0; j < n; j++) {
                    transition[i][j] = 1.0 / n;
                }
                for (int j = 0; j < m; j++) {
                    emission[i][j] = 1.0 / m;
                }
            }
        }

        public String predictFoldingState(String sequence) throws DNAProcessingException {
            if (sequence == null || sequence.isEmpty()) {
                throw new DNAProcessingException("Invalid sequence for HMM prediction!");
            }
            int n = STATES.length, m = sequence.length();
            double[][] viterbi = new double[n][m];
            int[][] backtrack = new int[n][m];

            for (int i = 0; i < n; i++) {
                int obsIndex = Arrays.asList(OBSERVATIONS).indexOf(String.valueOf(sequence.charAt(0)));
                if (obsIndex < 0) {
                    throw new DNAProcessingException("Invalid observation in sequence: " + sequence.charAt(0));
                }
                viterbi[i][0] = initial[i] * emission[i][obsIndex];
            }

            for (int t = 1; t < m; t++) {
                int obsIndex = Arrays.asList(OBSERVATIONS).indexOf(String.valueOf(sequence.charAt(t)));
                if (obsIndex < 0) {
                    throw new DNAProcessingException("Invalid observation in sequence: " + sequence.charAt(t));
                }
                for (int i = 0; i < n; i++) {
                    double maxProb = 0;
                    int maxState = 0;
                    for (int j = 0; j < n; j++) {
                        double prob = viterbi[j][t - 1] * transition[j][i] * emission[i][obsIndex];
                        if (prob > maxProb) {
                            maxProb = prob;
                            maxState = j;
                        }
                    }
                    viterbi[i][t] = maxProb;
                    backtrack[i][t] = maxState;
                }
            }

            int[] path = new int[m];
            double maxProb = 0;
            for (int i = 0; i < n; i++) {
                if (viterbi[i][m - 1] > maxProb) {
                    maxProb = viterbi[i][m - 1];
                    path[m - 1] = i;
                }
            }
            for (int t = m - 2; t >= 0; t--) {
                path[t] = backtrack[path[t + 1]][t + 1];
            }
            return STATES[path[m - 1]];
        }
    }

    // Monte Carlo Tree Search for local policy (Turing/Church-inspired)
    private static class MCTS {
        private class Node {
            FoldingPathway pathway;
            List<Node> children;
            int visits;
            double totalScore;

            Node(FoldingPathway pathway) {
                this.pathway = pathway;
                this.children = new ArrayList<>();
                this.visits = 0;
                this.totalScore = 0.0;
            }
        }
        private Node root;
        private List<FoldingPathway> successfulPathways;

        MCTS(List<FoldingPathway> successfulPathways) {
            this.successfulPathways = successfulPathways != null ? successfulPathways : new ArrayList<>();
            this.root = new Node(null); // Root is a dummy node
        }

        public double evaluatePathway(FoldingPathway pathway, List<Bond> possibleBonds) {
            // Simplified MCTS: Simulate and evaluate
            Node node = new Node(pathway);
            root.children.add(node);
            int simulations = 10; // Limited for performance
            double totalReward = 0.0;

            for (int i = 0; i < simulations; i++) {
                double reward = simulate(node, possibleBonds);
                node.visits++;
                node.totalScore += reward;
                totalReward += reward;
            }

            return totalReward / simulations;
        }

        private double simulate(Node node, List<Bond> possibleBonds) {
            // Simulate by perturbing pathway and evaluating
            List<Bond> simBonds = new ArrayList<>(node.pathway.getBonds());
            if (!simBonds.isEmpty() && Config.RAND.nextDouble() < 0.2) {
                int index = Config.RAND.nextInt(simBonds.size());
                simBonds.set(index, possibleBonds.get(Config.RAND.nextInt(possibleBonds.size())));
            }
            FoldingPathway simPathway = new FoldingPathway(simBonds);
            // Use composite score as reward, or fallback to fitness if new
            double reward = successfulPathways.contains(simPathway) ? simPathway.getCompositeScore() : simPathway.getFitness();
            return reward;
        }
    }

    // Splay tree for amino acids (CLRS Chapter 17)
    private static class SplayTree {
        private class Node {
            AminoAcid acid;
            Node left, right;
            Node(AminoAcid acid) { this.acid = acid; }
        }
        private Node root;

        public void insert(AminoAcid acid) {
            root = splayInsert(root, acid);
        }

        private Node splayInsert(Node node, AminoAcid acid) {
            if (node == null) return new Node(acid);
            if (acid.getId().compareTo(node.acid.getId()) < 0) {
                node.left = splayInsert(node.left, acid);
            } else {
                node.right = splayInsert(node.right, acid);
            }
            return node;
        }

        public List<AminoAcid> getAminoAcids() {
            List<AminoAcid> acids = new ArrayList<>();
            inOrderTraversal(root, acids);
            return acids;
        }

        private void inOrderTraversal(Node node, List<AminoAcid> acids) {
            if (node != null) {
                inOrderTraversal(node.left, acids);
                acids.add(node.acid);
                inOrderTraversal(node.right, acids);
            }
        }
    }

    public ProteinService(
            SimulationSession session,
            SimulationViewPort viewPort,
            JavaFxExecutor javaFxExecutor,
            ObjectMapper objectMapper) {
        if (viewPort == null) {
            throw new IllegalArgumentException("SimulationViewPort cannot be null");
        }
        if (javaFxExecutor == null) {
            throw new IllegalArgumentException("JavaFxExecutor cannot be null");
        }
        if (session == null) {
            throw new IllegalArgumentException("SimulationSession cannot be null");
        }
        if (objectMapper == null) {
            throw new IllegalArgumentException("ObjectMapper cannot be null");
        }
        this.proteins = session.getProteins();
        this.viewPort = viewPort;
        this.javaFxExecutor = javaFxExecutor;
        this.objectMapper = objectMapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
        this.aminoAcidTree = new SplayTree();
        this.foldingHMM = new HMM();
        this.chaperoneLevel = 1.0; // Alon: Feedback loop for protein expression
        this.mutationTree = new MutationTree();
    }

    // Protein folding with CLRS approximation, HMM, genetic algorithm, simulated annealing, and mutation tree
    public void foldProteins() throws DNAProcessingException {
        if (proteins.isEmpty()) {
            viewPort.updateStatus("No proteins to fold!");
            return;
        }

        viewPort.updateStatus("Folding proteins...");
        Task<Void> foldingTask = new Task<>() {
            @Override
            protected Void call() {
                double foldingProgress = 0;
                while (foldingProgress < 1) {
                    foldingProgress += 0.01;
                    final double progress = foldingProgress;
                    javaFxExecutor.runLater(() -> {
                        viewPort.showFoldingProgress(progress);
                        for (Protein protein : proteins) {
                            viewPort.animateProteinFolding(protein, progress);
                        }
                    });
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        viewPort.updateStatus("Folding interrupted: " + e.getMessage());
                        return null;
                    }
                }

                javaFxExecutor.runLater(() -> {
                    try {
                        for (Protein protein : proteins) {
                            if (protein == null) {
                                viewPort.updateStatus("Skipping null protein!");
                                continue;
                            }
                            // Initialize amino acids (Pevzner: Mass spectrometry or FASTA)
                            List<AminoAcid> aminoAcids = new ArrayList<>();
                            String sequence = inferProteinSequence(protein); // Pevzner
                            for (int i = 0; i < Math.min(sequence.length(), Config.MAX_AMINO_ACIDS / 10); i++) {
                                AminoAcid acid = new AminoAcid(
                                    UUID.randomUUID().toString(),
                                    String.valueOf(sequence.charAt(i)),
                                    Config.RAND.nextDouble() * 10
                                );
                                aminoAcids.add(acid);
                                aminoAcidTree.insert(acid); // CLRS Chapter 17
                            }

                            // Kolmogorov Complexity: Estimate folding difficulty
                            KolmogorovComplexity kc = new KolmogorovComplexity();
                            double foldingDifficulty = kc.estimateFoldingDifficulty(sequence);
                            boolean kcSuccess = foldingDifficulty <= 0.8; // Allow folding if difficulty is manageable

                            // CLRS Chapter 35: Approximation algorithm with Fibonacci Heap and CSP
                            List<Bond> approxBonds = approximateProteinFolding(aminoAcids);
                            double totalEnergy = approxBonds.stream().mapToDouble(Bond::getEnergy).sum();
                            boolean approxSuccess = totalEnergy <= Config.BOND_ENERGY_THRESHOLD;

                            // Noether: Symmetry check
                            boolean noetherSuccess = checkNoetherSymmetry(approxBonds);

                            // Durbin: HMM prediction
                            String foldingState = foldingHMM.predictFoldingState(sequence);
                            boolean hmmSuccess = !foldingState.equals("coil");

                            // Genetic algorithm (parallelized)
                            List<FoldingPathway> gaPopulation = runParallelGeneticAlgorithm(aminoAcids);
                            FoldingPathway gaPathway = gaPopulation.stream()
                                .max(Comparator.comparingDouble(FoldingPathway::getCompositeScore))
                                .orElse(gaPopulation.get(0));
                            boolean gaSuccess = gaPathway.getCompositeScore() >= 0.5;

                            // Simulated annealing (parallelized)
                            List<FoldingPathway> saPopulation = runParallelSimulatedAnnealing(aminoAcids);
                            FoldingPathway saPathway = saPopulation.stream()
                                .max(Comparator.comparingDouble(FoldingPathway::getCompositeScore))
                                .orElse(saPopulation.get(0));
                            boolean saSuccess = saPathway.getCompositeScore() >= 0.5;

                            // MCTS: Local policy evaluation
                            MCTS mcts = new MCTS(successfulPathways);
                            double mctsScore = mcts.evaluatePathway(gaPathway, computePossibleBonds(aminoAcids));
                            boolean mctsSuccess = mctsScore >= 0.5;

                            // Shannon: Entropy check (MacKay)
                            double entropy = calculateFoldingEntropy(Arrays.asList(gaPathway, saPathway));
                            boolean entropySuccess = entropy >= 0.5;

                            // CSP: Constraint satisfaction check
                            ConstraintPropagator csp = new ConstraintPropagator();
                            List<Bond> constrainedBonds = csp.propagateConstraints(approxBonds, 5); // Max imbalance = 5
                            boolean cspSuccess = !constrainedBonds.isEmpty() && constrainedBonds.size() >= approxBonds.size() * 0.8;

                            // Reaction-diffusion influence (Fall)
                            double diffusionFactor = calculateDiffusionFactor();
                            // Feedback loop (Alon)
                            double baseProbability = 0.7 * diffusionFactor * chaperoneLevel;
                            // CLRS Chapter 5: Randomized folding
                            boolean randomSuccess = Config.RAND.nextDouble() < baseProbability;

                            // Combine all results
                            if (approxSuccess && noetherSuccess && hmmSuccess && gaSuccess && saSuccess && mctsSuccess && entropySuccess && cspSuccess && kcSuccess && randomSuccess) {
                                protein.fold();
                                viewPort.updateStatus("Protein " + protein.getEnzymeType() + " folded as " + foldingState);
                                successfulPathways.add(gaPathway);
                                successfulPathways.add(saPathway);
                                saveSuccessfulPathways(); // ML: Save pathways
                                mutationTree.addPathway(gaPathway, null, "SuccessfulFold");
                                mutationTree.addPathway(saPathway, gaPathway, "Annealing");
                                mutationTree.seedNewPathway(gaPathway); // Von Neumann-inspired
                                chaperoneLevel = Math.max(0.5, chaperoneLevel * 0.95); // Alon
                            } else {
                                protein.failFold();
                                viewPort.updateStatus("Protein " + protein.getEnzymeType() + " failed to fold!");
                                mutationTree.addPathway(gaPathway, null, "FailedFold");
                                mutationTree.addPathway(saPathway, gaPathway, "Annealing");
                                chaperoneLevel = Math.min(1.5, chaperoneLevel * 1.05); // Alon
                            }
                            mutationTree.visualize();
                            viewPort.updateProtein(protein, protein.getX(), protein.getY(), protein.isFolded(), protein.isFoldFailed());
                        }
                        viewPort.hideFoldingProgress();
                        long foldedCount = proteins.stream().filter(p -> p != null && p.isFolded()).count();
                        viewPort.updateStatus("Protein folding complete! " + foldedCount + " folded correctly.");
                    } catch (DNAProcessingException e) {
                        viewPort.updateStatus("Folding failed: " + e.getMessage());
                    }
                });
                return null;
            }
        };
        new Thread(foldingTask).start();
    }

    // CLRS Chapter 35: Greedy approximation with Fibonacci Heap and CSP
    private List<Bond> approximateProteinFolding(List<AminoAcid> aminoAcids) throws DNAProcessingException {
        if (aminoAcids == null || aminoAcids.isEmpty()) {
            throw new DNAProcessingException("No amino acids provided for folding!");
        }
        if (aminoAcids.size() > Config.MAX_AMINO_ACIDS) {
            throw new DNAProcessingException("Too many amino acids: " + aminoAcids.size());
        }

        List<Bond> possibleBonds = computePossibleBonds(aminoAcids);
        // Apply constraint propagation (Knuth/CSP)
        ConstraintPropagator csp = new ConstraintPropagator();
        possibleBonds = csp.propagateConstraints(possibleBonds, 5); // Max direction imbalance = 5
        if (possibleBonds.isEmpty()) {
            throw new DNAProcessingException("No bonds satisfy CSP constraints!");
        }

        // Use Fibonacci Heap for fast bond retrieval (CLRS Chapter 19)
        FibonacciHeap heap = new FibonacciHeap();
        Set<Bond> selectedBondsSet = new HashSet<>();
        List<Bond> selectedBonds = new ArrayList<>();
        Set<AminoAcid> covered = new HashSet<>();

        // Insert bonds into Fibonacci Heap
        for (Bond bond : possibleBonds) {
            if (!selectedBondsSet.contains(bond) &&
                (!covered.contains(bond.getAminoAcid1()) || !covered.contains(bond.getAminoAcid2()))) {
                heap.insert(bond, bond.getEnergy());
            }
        }

        while (!heap.isEmpty() && covered.size() < aminoAcids.size()) {
            Bond bestBond = heap.extractMin();
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
    private double calculateBoltzmannProbability(List<Bond> selectedBonds, List<Bond> possibleBonds, List<AminoAcid> aminoAcids) {
        double selectedEnergy = selectedBonds.stream().mapToDouble(Bond::getEnergy).sum();
        // Approximate partition function Z by sampling bond configurations
        double Z = 0.0;
        int sampleSize = Math.min(100, possibleBonds.size()); // Limit for performance
        Set<AminoAcid> covered = new HashSet<>();
        List<Bond> sampleConfig = new ArrayList<>();

        // Include selected bonds as one configuration
        Z += Math.exp(-selectedEnergy / KT);

        // Sample other configurations
        for (int i = 0; i < sampleSize; i++) {
            sampleConfig.clear();
            covered.clear();
            Collections.shuffle(possibleBonds, Config.RAND);
            for (Bond bond : possibleBonds) {
                if (!covered.contains(bond.getAminoAcid1()) || !covered.contains(bond.getAminoAcid2())) {
                    sampleConfig.add(bond);
                    covered.add(bond.getAminoAcid1());
                    covered.add(bond.getAminoAcid2());
                    if (covered.size() >= aminoAcids.size()) break;
                }
            }
            double configEnergy = sampleConfig.stream().mapToDouble(Bond::getEnergy).sum();
            Z += Math.exp(-configEnergy / KT);
        }

        // Calculate Boltzmann probability: P = e^(-E/kT) / Z
        double probability = Math.exp(-selectedEnergy / KT) / Z;
        return Math.max(0.0, Math.min(1.0, probability));
    }

    // Noether-inspired symmetry check
    private boolean checkNoetherSymmetry(List<Bond> bonds) {
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
        double meanEnergy = pathway.getBonds().stream().mapToDouble(Bond::getEnergy).average().orElse(0.0);
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
        List<Bond> possibleBonds = computePossibleBonds(aminoAcids);
        for (int i = 0; i < populationSize; i++) {
            List<Bond> randomBonds = new ArrayList<>();
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

        List<Bond> possibleBonds = computePossibleBonds(aminoAcids);
        List<Future<FoldingPathway>> futures = new ArrayList<>();
        int numRuns = 10; // Number of parallel annealing runs

        for (int i = 0; i < numRuns; i++) {
            futures.add(executor.submit(() -> {
                List<Bond> currentBonds = new ArrayList<>();
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
                    List<Bond> neighborBonds = new ArrayList<>(currentBonds);
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
        List<Bond> childBonds = new ArrayList<>();
        int split = Config.RAND.nextInt(Math.min(parent1.getBonds().size(), parent2.getBonds().size()));
        childBonds.addAll(parent1.getBonds().subList(0, split));
        childBonds.addAll(parent2.getBonds().subList(split, parent2.getBonds().size()));
        return new FoldingPathway(childBonds);
    }

    private FoldingPathway mutate(FoldingPathway pathway, List<Bond> possibleBonds) {
        List<Bond> bonds = new ArrayList<>(pathway.getBonds());
        if (!bonds.isEmpty()) {
            int index = Config.RAND.nextInt(bonds.size());
            bonds.set(index, possibleBonds.get(Config.RAND.nextInt(possibleBonds.size())));
        }
        return new FoldingPathway(bonds);
    }

    private List<Bond> computePossibleBonds(List<AminoAcid> aminoAcids) {
        List<Bond> bonds = new ArrayList<>();
        for (int i = 0; i < aminoAcids.size(); i++) {
            for (int j = i + 1; j < aminoAcids.size(); j++) {
                double energy = Config.RAND.nextDouble() * Config.BOND_ENERGY_THRESHOLD;
                String direction = Config.RAND.nextDouble() < 0.5 ? "L" : "R"; // Noether: Assign L/R
                bonds.add(new Bond(aminoAcids.get(i), aminoAcids.get(j), energy, direction));
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
            sequenceBuilder.append(HMM.OBSERVATIONS[Config.RAND.nextInt(HMM.OBSERVATIONS.length)]);
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
            viewPort.updateStatus("Failed to read FASTA file: " + e.getMessage());
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

    public double calculateResistanceFactor() {
        if (proteins == null || proteins.isEmpty()) {
            return 0.0;
        }
        return proteins.stream()
            .filter(p -> p != null && p.isFolded())
            .mapToDouble(Protein::getViralResistance)
            .average()
            .orElse(0.0);
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
            viewPort.updateStatus("Failed to save successful pathways: " + e.getMessage());
        }
    }
}
