package com.xai.dnareplicator.config;

import java.util.Random;

public class Config {

    public static final String FASTA_EXPORT_PATH = "resources/dna_export.fasta";
    public static final String STATE_FILE_PATH = "resources/virus_state.vrs";
    public static final String STATS_FILE_PATH = "resources/virology_stats.json";

    public static final Random RAND = new Random();

    public static final int MAX_DNA_FRAGMENT_LENGTH = 1000;
    public static final double DNA_ALIGNMENT_SCORE_THRESHOLD = 0.8;

    public static final int MAX_AMINO_ACIDS = 500;
    public static final double BOND_ENERGY_THRESHOLD = 10.0;

    public static final int MAX_CELLS = 10000;
    public static final double INFECTION_WEIGHT_DEFAULT = 1.0;

    public static final long SIMULATION_STEP_DELAY_MS = 100;
}
