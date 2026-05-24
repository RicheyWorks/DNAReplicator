package com.xai.dnareplicator.model;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Single source of truth for in-memory simulation state (DNA fragments, proteins).
 */
@Component
public class SimulationSession {

    private final List<DNAFragment> dnaFragments = new ArrayList<>();
    private final List<Protein> proteins = new ArrayList<>();

    public List<DNAFragment> getDnaFragments() {
        return dnaFragments;
    }

    public List<Protein> getProteins() {
        return proteins;
    }

    public void clearDnaAndProteins() {
        dnaFragments.clear();
        proteins.clear();
    }
}
