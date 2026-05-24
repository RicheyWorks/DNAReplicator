package com.xai.dnareplicator.controller;

import com.xai.dnareplicator.algorithm.graph.BreadthFirstInfection;
import com.xai.dnareplicator.algorithm.graph.DijkstraPathfinder;
import com.xai.dnareplicator.config.Config;
import com.xai.dnareplicator.model.Cell;
import com.xai.dnareplicator.model.Level;
import com.xai.dnareplicator.model.Protein;
import com.xai.dnareplicator.model.VirologyModel;
import com.xai.dnareplicator.model.Virus;
import com.xai.dnareplicator.presentation.contract.SimulationViewPort;
import com.xai.dnareplicator.presentation.javafx.InfectionAnimationDriver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class InfectionEngine {
    private Virus virus;
    private Cell targetCell;
    private List<Cell> cells; // For graph-based infection
    private final SimulationViewPort viewPort;
    private final InfectionAnimationDriver animationDriver;
    private boolean isSimulating;
    private Level level;
    private VirologyModel virologyModel;
    private final ObjectMapper objectMapper;

    public InfectionEngine(
            SimulationViewPort viewPort,
            InfectionAnimationDriver animationDriver,
            ObjectMapper objectMapper) {
        if (viewPort == null) {
            throw new IllegalArgumentException("SimulationViewPort cannot be null");
        }
        if (animationDriver == null) {
            throw new IllegalArgumentException("InfectionAnimationDriver cannot be null");
        }
        this.virus = null;
        this.targetCell = null;
        this.cells = new ArrayList<>();
        this.viewPort = viewPort;
        this.animationDriver = animationDriver;
        this.isSimulating = false;
        this.level = new Level();
        this.virologyModel = new VirologyModel();
        this.objectMapper = objectMapper.copy()
                .enable(SerializationFeature.INDENT_OUTPUT);
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
            viewPort.updateStatus("Invalid proteins or protein service!");
            return;
        }
        long foldedCount = proteins.stream().filter(p -> p != null && p.isFolded()).count();
        if (foldedCount < level.getFragmentsRequired()) {
            viewPort.updateStatus("Need at least " + level.getFragmentsRequired() + " correctly folded proteins to build a virus!");
            return;
        }

        // CLRS Chapter 5: Randomized virus assembly
        if (Config.RAND.nextDouble() > 0.9) {
            viewPort.updateStatus("Virus assembly failed due to structural instability!");
            return;
        }

        String virusName = viewPort.promptForVirusName();
        if (virusName == null || virusName.isEmpty()) {
            viewPort.updateStatus("Invalid virus name!");
            return;
        }

        viewPort.updateStatus("Building virus...");
        try {
            double resistanceFactor = proteinService.calculateResistanceFactor();
            double infectionEfficiency = calculateInfectionEfficiency();
            virus = new Virus(400, 300, resistanceFactor, virusName, infectionEfficiency);
            viewPort.addVirus(virus, virus.getX(), virus.getY());
            viewPort.updateVirusInfo(virus.getName(), virus.getResistanceFactor());
            proteins.clear();
            viewPort.clearAll();
            viewPort.addVirus(virus, virus.getX(), virus.getY());
        } catch (Exception e) {
            viewPort.updateStatus("Failed to build virus: " + e.getMessage());
        }
    }

    private double calculateInfectionEfficiency() {
        double successRate = virologyModel.getInfectedCells() / (double) (virologyModel.getInfectedCells() + virologyModel.getResistantCells() + 1);
        return 0.5 + successRate * 0.3; // Base efficiency + boost from past successes
    }

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

        return BreadthFirstInfection.spread(
                startCell,
                virus,
                this::canInfect,
                cell -> {
                    viewPort.updateCell(cell, true);
                    virologyModel.recordInfection(true);
                });
    }

    public List<Cell> findOptimalInfectionPath(Cell start, Cell target) throws DNAProcessingException {
        if (start == null || target == null) {
            throw new DNAProcessingException("Start or target cell is null!");
        }
        if (cells.isEmpty()) {
            throw new DNAProcessingException("No cells available for pathfinding!");
        }
        return DijkstraPathfinder.findPath(start, target, cells, this::computeInfectionWeight);
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
            viewPort.updateStatus("Build a virus first!");
            return;
        }

        // Initialize multiple cells for graph-based simulation
        if (cells.isEmpty()) {
            for (int i = 0; i < Config.MAX_CELLS / 10; i++) { // Limited for performance
                double x = 200 + Config.RAND.nextDouble() * 400;
                double y = 100 + Config.RAND.nextDouble() * 300;
                Cell cell = new Cell(x, y, level.getCellResistance());
                cells.add(cell);
                viewPort.addCell(cell, cell.getX(), cell.getY(), false);
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
            viewPort.addCell(targetCell, targetCell.getX(), targetCell.getY(), false);
        }

        viewPort.updateStatus("Simulating infection...");
        isSimulating = true;
    }

    public void startSimulation() {
        animationDriver.start(now -> handleInfectionFrame());
    }

    private void handleInfectionFrame() {
        if (!isSimulating || virus == null || targetCell == null) {
            return;
        }
        try {
            List<Cell> path = findOptimalInfectionPath(cells.get(0), targetCell);
            if (path.isEmpty()) {
                viewPort.updateStatus("No valid infection path found!");
                isSimulating = false;
                animationDriver.stop();
                return;
            }

            Cell nextCell = path.get(Math.min(path.indexOf(cells.get(0)) + 1, path.size() - 1));
            double dx = nextCell.getX() - virus.getX();
            double dy = nextCell.getY() - virus.getY();
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist > 10) {
                virus.move(dx / dist * 2, dy / dist * 2);
                viewPort.updateVirus(virus, virus.getX(), virus.getY());
            } else {
                viewPort.animateVirusAttack(virus, nextCell.getX(), nextCell.getY(), () -> {
                    try {
                        List<Cell> infectedCells = simulateInfectionBFS(nextCell, virus);
                        viewPort.updateStatus("Infected " + infectedCells.size() + " cells!");
                        if (infectedCells.contains(targetCell)) {
                            targetCell.compromise();
                            viewPort.updateCell(targetCell, true);
                            viewPort.updateStatus("Target cell compromised!");
                            virologyModel.recordInfection(true);
                            level.advanceLevel();
                        } else {
                            viewPort.updateStatus("Target cell resisted!");
                            virologyModel.recordInfection(false);
                        }
                        updateLevelAndVirology();
                        saveStatsToJSON();
                    } catch (DNAProcessingException e) {
                        viewPort.updateStatus("Infection simulation failed: " + e.getMessage());
                    }
                    isSimulating = false;
                    virus = null;
                    targetCell = null;
                    cells.clear();
                    viewPort.removeVirus();
                    viewPort.removeCell();
                    viewPort.clearVirusInfo();
                    animationDriver.stop();
                });
            }
        } catch (DNAProcessingException e) {
            viewPort.updateStatus("Pathfinding failed: " + e.getMessage());
            isSimulating = false;
            animationDriver.stop();
        }
    }

    private void updateLevelAndVirology() {
        viewPort.updateLevel(level.getLevel());
        viewPort.updateVirology(virologyModel.getInfectedCells(), virologyModel.getResistantCells());
        viewPort.updateInfectionHistory(virologyModel.getInfectionHistory());
    }

    private void saveStatsToJSON() {
        try {
            File statsFile = new File(Config.getStatsFilePath());
            statsFile.getParentFile().mkdirs();
            objectMapper.writeValue(statsFile, virologyModel);
        } catch (Exception e) {
            viewPort.updateStatus("Failed to save stats: " + e.getMessage());
        }
    }

    public void clear() {
        virus = null;
        targetCell = null;
        cells.clear();
        isSimulating = false;
        viewPort.removeVirus();
        viewPort.removeCell();
        viewPort.clearVirusInfo();
    }

    public void reset() {
        clear();
        level = new Level();
        virologyModel.reset();
        updateLevelAndVirology();
    }
}
