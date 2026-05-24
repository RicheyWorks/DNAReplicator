package com.xai.dnareplicator.algorithm.protein;

import com.xai.dnareplicator.controller.DNAProcessingException;

import java.util.Arrays;

/** Simplified HMM for folding state prediction (Durbin). */
public class FoldingHmm {

    public static final String[] OBSERVATIONS = {"A", "C", "G", "T"};

    private static final String[] STATES = {"helix", "sheet", "coil"};
    private final double[][] transition;
    private final double[][] emission;
    private final double[] initial;

    public FoldingHmm() {
        int stateCount = STATES.length;
        int obsCount = OBSERVATIONS.length;
        transition = new double[stateCount][stateCount];
        emission = new double[stateCount][obsCount];
        initial = new double[stateCount];
        for (int i = 0; i < stateCount; i++) {
            initial[i] = 1.0 / stateCount;
            for (int j = 0; j < stateCount; j++) {
                transition[i][j] = 1.0 / stateCount;
            }
            for (int j = 0; j < obsCount; j++) {
                emission[i][j] = 1.0 / obsCount;
            }
        }
    }

    public String predictFoldingState(String sequence) throws DNAProcessingException {
        if (sequence == null || sequence.isEmpty()) {
            throw new DNAProcessingException("Invalid sequence for HMM prediction!");
        }
        int stateCount = STATES.length;
        int length = sequence.length();
        double[][] viterbi = new double[stateCount][length];
        int[][] backtrack = new int[stateCount][length];

        for (int i = 0; i < stateCount; i++) {
            int obsIndex = Arrays.asList(OBSERVATIONS).indexOf(String.valueOf(sequence.charAt(0)));
            if (obsIndex < 0) {
                throw new DNAProcessingException("Invalid observation in sequence: " + sequence.charAt(0));
            }
            viterbi[i][0] = initial[i] * emission[i][obsIndex];
        }

        for (int t = 1; t < length; t++) {
            int obsIndex = Arrays.asList(OBSERVATIONS).indexOf(String.valueOf(sequence.charAt(t)));
            if (obsIndex < 0) {
                throw new DNAProcessingException("Invalid observation in sequence: " + sequence.charAt(t));
            }
            for (int i = 0; i < stateCount; i++) {
                double maxProb = 0;
                int maxState = 0;
                for (int j = 0; j < stateCount; j++) {
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

        int[] path = new int[length];
        double maxProb = 0;
        for (int i = 0; i < stateCount; i++) {
            if (viterbi[i][length - 1] > maxProb) {
                maxProb = viterbi[i][length - 1];
                path[length - 1] = i;
            }
        }
        for (int t = length - 2; t >= 0; t--) {
            path[t] = backtrack[path[t + 1]][t + 1];
        }
        return STATES[path[length - 1]];
    }
}
