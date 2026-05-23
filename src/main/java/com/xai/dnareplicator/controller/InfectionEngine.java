package com.xai.dnareplicator.controller;

import com.xai.dnareplicator.model.Virus;
import com.xai.dnareplicator.model.Cell;
import com.xai.dnareplicator.model.Level;
import com.xai.dnareplicator.model.VirologyModel;
import com.xai.dnareplicator.model.Protein;
import com.xai.dnareplicator.config.Config;

import java.io.FileWriter;
import java.util.*;
import java.util.stream.Collectors;
import javafx.animation.AnimationTimer;
import org.json.JSONObject;

import org.springframework.stereotype.Component;

@Component
public class InfectionEngine {
    private Virus virus;
    private Cell targetCell;
    private List<Cell> cells; // For graph-based infection
    private ViewUpdater viewUpdater; // Use interface to avoid circular dependency
    private boolean isSimulating;
    private Level level;
    private VirologyModel virologyModel;

    public InfectionEngine(ViewUpdater viewUpdater) {
        if (viewUpdater == null) {
            throw new IllegalArgumentException("ViewUpdater cannot be null");
        }
        this.virus = null;
        this.targetCell = null;
        this.cells = new ArrayList<>();
        this.viewUpdater = viewUpdater;
        this.isSimulating = false;
        this.level = new Level();
        this.virologyModel = new VirologyModel();
        updateLevelAndVirology();
    }

    public Virus getVirus() {
        return virus;
    }

    public Level getLevel() {
        return level;
    }

    public VirologyModel getVirologyModel() {
        return virologyModel;
    }

    public void buildVirus(List<Protein> proteins, ProteinService proteinService) {
        if (proteins == null || proteinService == null) {
            viewUpdater.updateStatus("Invalid proteins or protein service!");
            return;
        }
        long foldedCount = proteins.stream().filter(p -> p != null && p.isFolded()).count();
        if (foldedCount < level.getFragmentsRequired()) {
            viewUpdater.updateStatus("Need at least " + level.getFragmentsRequired() + " correctly folded proteins to build a virus!");
            return;
        }

        // CLRS Chapter 5: Randomized virus assembly
        if (Config.RAND.nextDouble() > 0.9) {
            viewUpdater.updateStatus("Virus assembly failed due to structural instability!");
            return;
        }

        String virusName = viewUpdater.promptForVirusName();
        if (virusName == null || virusName.isEmpty()) {
            viewUpdater.updateStatus("Invalid virus name!");
            return;
        }

        viewUpdater.updateStatus("Building virus...");
        try {
            double resistanceFactor = proteinService.calculateResistanceFactor();
            double infectionEfficiency = calculateInfectionEfficiency();
            virus = new Virus(400, 300, resistanceFactor, virusName, infectionEfficiency);
            viewUpdater.addVirus(virus, virus.getX(), virus.getY());
            viewUpdater.updateVirusInfo(virus.getName(), virus.getResistanceFactor());
            proteins.clear();
            viewUpdater.clearAll();
            viewUpdater.addVirus(virus, virus.getX(), virus.getY());
        } catch (Exception e) {
            viewUpdater.updateStatus("Failed to build virus: " + e.getMessage());
        }
    }

    private double calculateInfectionEfficiency() {
        double successRate = virologyModel.getInfectedCells() / (double) (virologyModel.getInfectedCells() + virologyModel.getResistantCells() + 1);
        return 0.5 + successRate * 0.3; // Base efficiency + boost from past successes
    }

    // CLRS Chapter 22: BFS for infection spread
    public List<Cell> simulateInfectionBFS(Cell startCell, Virus virus) throws DNAProcessingException {
        if (virus == null) {
            throw new DNAProcessingException("No virus available for infection!");
        }
        if (startCell == null) {
            throw new DNAProcessingException("No starting cell provided!");
        }
        if (cells.isEmpty()) {
            throw new DNAProcessingException("No cells available for infection simulation!");
        }

        List<Cell> infected = new ArrayList<>();
        Queue<Cell> queue = new LinkedList<>();
        Set<Cell> visited = new HashSet<>();
        queue.add(startCell);
        visited.add(startCell);
        infected.add(startCell);

        while (!queue.isEmpty()) {
            Cell current = queue.poll();
            viewUpdater.updateCell(current, true);
            virologyModel.recordInfection(true);

            for (Cell neighbor : current.getNeighbors()) {
                if (!visited.contains(neighbor) && canInfect(neighbor, virus)) {
                    queue.add(neighbor);
                    visited.add(neighbor);
                    infected.add(neighbor);
                }
            }
        }
        return infected;
    }

    // CLRS Chapter 24: Dijkstra’s for optimal infection path
    public List<Cell> findOptimalInfectionPath(Cell start, Cell target) throws DNAProcessingException {
        if (start == null || target == null) {
            throw new DNAProcessingException("Start or target cell is null!");
        }
        if (cells.isEmpty()) {
            throw new DNAProcessingException("No cells available for pathfinding!");
        }

        PriorityQueue<Cell> pq = new PriorityQueue<>(Comparator.comparingDouble(Cell::getDistance));
        Map<Cell, Double> distances = new HashMap<>();
        Map<Cell, Cell> predecessors = new HashMap<>();
        for (Cell cell : cells) {
            distances.put(cell, Double.MAX_VALUE);
        }
        distances.put(start, 0.0);
        start.setDistance(0.0);
        pq.add(start);

        while (!pq.isEmpty()) {
            Cell current = pq.poll();
            if (current == target) break;
            for (Cell neighbor : current.getNeighbors()) {
                double weight = computeInfectionWeight(current, neighbor);
                if (distances.get(current) + weight < distances.get(neighbor)) {
                    distances.put(neighbor, distances.get(current) + weight);
                    predecessors.put(neighbor, current);
                    neighbor.setDistance(distances.get(neighbor));
                    pq.add(neighbor);
                }
            }
        }

        List<Cell> path = new ArrayList<>();
        Cell current = target;
        while (current != null) {
            path.add(current);
            current = predecessors.get(current);
        }
        Collections.reverse(path);
        return path.isEmpty() || path.get(0) != start ? new ArrayList<>() : path;
    }

    private double computeInfectionWeight(Cell c1, Cell c2) {
        // Weight based on cell resistance and distance
        double distance = Math.sqrt(Math.pow(c1.getX() - c2.getX(), 2) + Math.pow(c1.getY() - c2.getY(), 2));
        return Config.INFECTION_WEIGHT_DEFAULT * (1 + c2.getResistance()) * (distance / 100);
    }

    private boolean canInfect(Cell cell, Virus virus) {
        // CLRS Chapter 5: Probabilistic infection chance
        double infectionChance = (0.6 + virus.getInfectionEfficiency()) * (1 - cell.getResistance());
        return Config.RAND.nextDouble() < infectionChance;
    }

    public void simulateInfection() {
        if (virus == null) {
            viewUpdater.updateStatus("Build a virus first!");
            return;
        }

        // Initialize multiple cells for graph-based simulation
        if (cells.isEmpty()) {
            for (int i = 0; i < Config.MAX_CELLS / 10; i++) { // Limited for performance
                double x = 200 + Config.RAND.nextDouble() * 400;
                double y = 100 + Config.RAND.nextDouble() * 300;
                Cell cell = new Cell(x, y, level.getCellResistance());
                cells.add(cell);
                viewUpdater.addCell(cell, cell.getX(), cell.getY(), false);
            }
            // Set up neighbors (simplified grid-like connections)
            for (Cell cell : cells) {
                for (Cell other : cells) {
                    if (cell != other && Math.sqrt(Math.pow(cell.getX() - other.getX(), 2) + Math.pow(cell.getY() - other.getY(), 2)) < 100) {
                        cell.addNeighbor(other);
                    }
                }
            }
        }

        if (targetCell == null) {
            targetCell = cells.get(Config.RAND.nextInt(cells.size()));
            viewUpdater.addCell(targetCell, targetCell.getX(), targetCell.getY(), false);
        }

        viewUpdater.updateStatus("Simulating infection...");
        isSimulating = true;
    }

    public void startSimulation() {
        AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (isSimulating && virus != null && targetCell != null) {
                    try {
                        // Find optimal path to target cell (CLRS Chapter 24)
                        List<Cell> path = findOptimalInfectionPath(cells.get(0), targetCell);
                        if (path.isEmpty()) {
                            viewUpdater.updateStatus("No valid infection path found!");
                            isSimulating = false;
                            return;
                        }

                        // Move virus along path
                        Cell nextCell = path.get(Math.min(path.indexOf(cells.get(0)) + 1, path.size() - 1));
                        double dx = nextCell.getX() - virus.getX();
                        double dy = nextCell.getY() - virus.getY();
                        double dist = Math.sqrt(dx * dx + dy * dy);
                        if (dist > 10) {
                            virus.move(dx / dist * 2, dy / dist * 2);
                            viewUpdater.updateVirus(virus, virus.getX(), virus.getY());
                        } else {
                            viewUpdater.animateVirusAttack(virus, nextCell.getX(), nextCell.getY(), () -> {
                                try {
                                    // Simulate infection spread from nextCell (CLRS Chapter 22)
                                    List<Cell> infectedCells = simulateInfectionBFS(nextCell, virus);
                                    viewUpdater.updateStatus("Infected " + infectedCells.size() + " cells!");
                                    if (infectedCells.contains(targetCell)) {
                                        targetCell.compromise();
                                        viewUpdater.updateCell(targetCell, true);
                                        viewUpdater.updateStatus("Target cell compromised!");
                                        virologyModel.recordInfection(true);
                                        level.advanceLevel();
                                    } else {
                                        viewUpdater.updateStatus("Target cell resisted!");
                                        virologyModel.recordInfection(false);
                                    }
                                    updateLevelAndVirology();
                                    saveStatsToJSON();
                                } catch (DNAProcessingException e) {
                                    viewUpdater.updateStatus("Infection simulation failed: " + e.getMessage());
                                }
                                isSimulating = false;
                                virus = null;
                                targetCell = null;
                                cells.clear();
                                viewUpdater.removeVirus();
                                viewUpdater.removeCell();
                                viewUpdater.clearVirusInfo();
                            });
                        }
                    } catch (DNAProcessingException e) {
                        viewUpdater.updateStatus("Pathfinding failed: " + e.getMessage());
                        isSimulating = false;
                    }
                }
            }
        };
        timer.start();
    }

    private void updateLevelAndVirology() {
        viewUpdater.updateLevel(level.getLevel());
        viewUpdater.updateVirology(virologyModel.getInfectedCells(), virologyModel.getResistantCells());
        viewUpdater.updateInfectionHistory(virologyModel.getInfectionHistory());
    }

    private void saveStatsToJSON() {
        try (FileWriter writer = new FileWriter(Config.STATS_FILE_PATH)) {
            JSONObject json = virologyModel.toJSON();
            writer.write(json.toString(4));
        } catch (Exception e) {
            viewUpdater.updateStatus("Failed to save stats: " + e.getMessage());
        }
    }

    public void clear() {
        virus = null;
        targetCell = null;
        cells.clear();
        isSimulating = false;
        viewUpdater.removeVirus();
        viewUpdater.removeCell();
        viewUpdater.clearVirusInfo();
    }

    public void reset() {
        clear();
        level = new Level();
        virologyModel.reset();
        updateLevelAndVirology();
    }
}
