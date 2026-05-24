package com.xai.dnareplicator.config;

import java.util.Random;

/**
 * Static accessors for legacy call sites. Initialized from {@link SimulationProperties} at startup.
 *
 * @deprecated Prefer injecting {@link SimulationProperties} directly in new code.
 */
@Deprecated
public final class Config {

    private static SimulationProperties properties;
    public static Random RAND;

    private Config() {
    }

    public static void initialize(SimulationProperties simulationProperties, Random random) {
        properties = simulationProperties;
        RAND = random;
    }

    public static String getFastaExportPath() {
        return properties.getDna().getFastaExportPath();
    }

    public static String getStateFilePath() {
        return properties.getDna().getStateFilePath();
    }

    public static String getStatsFilePath() {
        return properties.getDna().getStatsFilePath();
    }

    public static int getMaxDnaFragmentLength() {
        return properties.getDna().getMaxFragmentLength();
    }

    public static double getDnaAlignmentScoreThreshold() {
        return properties.getDna().getAlignmentScoreThreshold();
    }

    public static int getMaxAminoAcids() {
        return properties.getProtein().getMaxAminoAcids();
    }

    public static double getBondEnergyThreshold() {
        return properties.getProtein().getBondEnergyThreshold();
    }

    public static int getMaxCells() {
        return properties.getInfection().getMaxCells();
    }

    public static double getInfectionWeightDefault() {
        return properties.getInfection().getWeightDefault();
    }

    public static long getSimulationStepDelayMs() {
        return properties.getSimulation().getStepDelayMs();
    }

    // Legacy constant names used across the codebase
    public static final String FASTA_EXPORT_PATH = "resources/dna_export.fasta";
    public static final String STATE_FILE_PATH = "resources/virus_state.vrs";
    public static final String STATS_FILE_PATH = "resources/virology_stats.json";
    public static final int MAX_DNA_FRAGMENT_LENGTH = 1000;
    public static final double DNA_ALIGNMENT_SCORE_THRESHOLD = 0.8;
    public static final int MAX_AMINO_ACIDS = 500;
    public static final double BOND_ENERGY_THRESHOLD = 10.0;
    public static final int MAX_CELLS = 10000;
    public static final double INFECTION_WEIGHT_DEFAULT = 1.0;
    public static final long SIMULATION_STEP_DELAY_MS = 100;

    static {
        // Defaults until Spring initializes ConfigBridge
        properties = new SimulationProperties();
        RAND = new Random();
    }
}
