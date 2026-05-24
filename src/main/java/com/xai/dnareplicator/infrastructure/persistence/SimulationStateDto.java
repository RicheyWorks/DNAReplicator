package com.xai.dnareplicator.infrastructure.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Versioned simulation snapshot for JSON persistence (replaces comma-separated .vrs).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SimulationStateDto {

    public static final int CURRENT_VERSION = 1;

    private int version = CURRENT_VERSION;
    private double mutationRate = 0.2;
    private int level = 1;
    private int infectedCells;
    private int resistantCells;
    private List<Boolean> infectionHistory = new ArrayList<>();
    private List<DnaFragmentDto> dnaFragments = new ArrayList<>();
    private List<ProteinDto> proteins = new ArrayList<>();
    private VirusDto virus;

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public double getMutationRate() {
        return mutationRate;
    }

    public void setMutationRate(double mutationRate) {
        this.mutationRate = mutationRate;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public int getInfectedCells() {
        return infectedCells;
    }

    public void setInfectedCells(int infectedCells) {
        this.infectedCells = infectedCells;
    }

    public int getResistantCells() {
        return resistantCells;
    }

    public void setResistantCells(int resistantCells) {
        this.resistantCells = resistantCells;
    }

    public List<Boolean> getInfectionHistory() {
        return infectionHistory;
    }

    public void setInfectionHistory(List<Boolean> infectionHistory) {
        this.infectionHistory = infectionHistory != null ? infectionHistory : new ArrayList<>();
    }

    public List<DnaFragmentDto> getDnaFragments() {
        return dnaFragments;
    }

    public void setDnaFragments(List<DnaFragmentDto> dnaFragments) {
        this.dnaFragments = dnaFragments != null ? dnaFragments : new ArrayList<>();
    }

    public List<ProteinDto> getProteins() {
        return proteins;
    }

    public void setProteins(List<ProteinDto> proteins) {
        this.proteins = proteins != null ? proteins : new ArrayList<>();
    }

    public VirusDto getVirus() {
        return virus;
    }

    public void setVirus(VirusDto virus) {
        this.virus = virus;
    }

    public static class DnaFragmentDto {
        private double x;
        private double y;
        private String name;
        private boolean selected;
        private String basePairs;

        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public boolean isSelected() { return selected; }
        public void setSelected(boolean selected) { this.selected = selected; }
        public String getBasePairs() { return basePairs; }
        public void setBasePairs(String basePairs) { this.basePairs = basePairs; }
    }

    public static class ProteinDto {
        private double x;
        private double y;
        private boolean folded;
        private boolean foldFailed;
        private String enzymeType;
        private double viralResistance;

        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public boolean isFolded() { return folded; }
        public void setFolded(boolean folded) { this.folded = folded; }
        public boolean isFoldFailed() { return foldFailed; }
        public void setFoldFailed(boolean foldFailed) { this.foldFailed = foldFailed; }
        public String getEnzymeType() { return enzymeType; }
        public void setEnzymeType(String enzymeType) { this.enzymeType = enzymeType; }
        public double getViralResistance() { return viralResistance; }
        public void setViralResistance(double viralResistance) { this.viralResistance = viralResistance; }
    }

    public static class VirusDto {
        private double x;
        private double y;
        private double resistanceFactor;
        private String name;
        private double infectionEfficiency;

        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public double getResistanceFactor() { return resistanceFactor; }
        public void setResistanceFactor(double resistanceFactor) { this.resistanceFactor = resistanceFactor; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getInfectionEfficiency() { return infectionEfficiency; }
        public void setInfectionEfficiency(double infectionEfficiency) { this.infectionEfficiency = infectionEfficiency; }
    }
}
