package com.xai.dnareplicator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties
public class SimulationProperties {

    private Dna dna = new Dna();
    private Protein protein = new Protein();
    private Infection infection = new Infection();
    private Simulation simulation = new Simulation();
    private Visualization visualization = new Visualization();

    public Dna getDna() {
        return dna;
    }

    public void setDna(Dna dna) {
        this.dna = dna;
    }

    public Protein getProtein() {
        return protein;
    }

    public void setProtein(Protein protein) {
        this.protein = protein;
    }

    public Infection getInfection() {
        return infection;
    }

    public void setInfection(Infection infection) {
        this.infection = infection;
    }

    public Simulation getSimulation() {
        return simulation;
    }

    public void setSimulation(Simulation simulation) {
        this.simulation = simulation;
    }

    public Visualization getVisualization() {
        return visualization;
    }

    public void setVisualization(Visualization visualization) {
        this.visualization = visualization;
    }

    public static class Simulation {
        private long stepDelayMs = 100;
        private int eventQueueCapacity = 1000;

        public long getStepDelayMs() {
            return stepDelayMs;
        }

        public void setStepDelayMs(long stepDelayMs) {
            this.stepDelayMs = stepDelayMs;
        }

        public int getEventQueueCapacity() {
            return eventQueueCapacity;
        }

        public void setEventQueueCapacity(int eventQueueCapacity) {
            this.eventQueueCapacity = eventQueueCapacity;
        }
    }

    public static class Visualization {
        private int maxTimestamp = 100000;
        private boolean displayComplexity = true;

        public int getMaxTimestamp() {
            return maxTimestamp;
        }

        public void setMaxTimestamp(int maxTimestamp) {
            this.maxTimestamp = maxTimestamp;
        }

        public boolean isDisplayComplexity() {
            return displayComplexity;
        }

        public void setDisplayComplexity(boolean displayComplexity) {
            this.displayComplexity = displayComplexity;
        }
    }

    public static class Dna {
        private String fastaExportPath = "resources/dna_export.fasta";
        private String stateFilePath = "resources/virus_state.vrs";
        private String statsFilePath = "resources/virology_stats.json";
        private int maxFragmentLength = 1000;
        private double alignmentScoreThreshold = 0.8;

        public String getFastaExportPath() {
            return fastaExportPath;
        }

        public void setFastaExportPath(String fastaExportPath) {
            this.fastaExportPath = fastaExportPath;
        }

        public String getStateFilePath() {
            return stateFilePath;
        }

        public void setStateFilePath(String stateFilePath) {
            this.stateFilePath = stateFilePath;
        }

        public String getStatsFilePath() {
            return statsFilePath;
        }

        public void setStatsFilePath(String statsFilePath) {
            this.statsFilePath = statsFilePath;
        }

        public int getMaxFragmentLength() {
            return maxFragmentLength;
        }

        public void setMaxFragmentLength(int maxFragmentLength) {
            this.maxFragmentLength = maxFragmentLength;
        }

        public double getAlignmentScoreThreshold() {
            return alignmentScoreThreshold;
        }

        public void setAlignmentScoreThreshold(double alignmentScoreThreshold) {
            this.alignmentScoreThreshold = alignmentScoreThreshold;
        }
    }

    public static class Protein {
        private int maxAminoAcids = 500;
        private double bondEnergyThreshold = 10.0;
        private double approximationRatioTolerance = 2.0;

        public int getMaxAminoAcids() {
            return maxAminoAcids;
        }

        public void setMaxAminoAcids(int maxAminoAcids) {
            this.maxAminoAcids = maxAminoAcids;
        }

        public double getBondEnergyThreshold() {
            return bondEnergyThreshold;
        }

        public void setBondEnergyThreshold(double bondEnergyThreshold) {
            this.bondEnergyThreshold = bondEnergyThreshold;
        }

        public double getApproximationRatioTolerance() {
            return approximationRatioTolerance;
        }

        public void setApproximationRatioTolerance(double approximationRatioTolerance) {
            this.approximationRatioTolerance = approximationRatioTolerance;
        }
    }

    public static class Infection {
        private int maxCells = 10000;
        private double weightDefault = 1.0;
        private int maxInfectedClusters = 100;

        public int getMaxCells() {
            return maxCells;
        }

        public void setMaxCells(int maxCells) {
            this.maxCells = maxCells;
        }

        public double getWeightDefault() {
            return weightDefault;
        }

        public void setWeightDefault(double weightDefault) {
            this.weightDefault = weightDefault;
        }

        public int getMaxInfectedClusters() {
            return maxInfectedClusters;
        }

        public void setMaxInfectedClusters(int maxInfectedClusters) {
            this.maxInfectedClusters = maxInfectedClusters;
        }
    }
}
