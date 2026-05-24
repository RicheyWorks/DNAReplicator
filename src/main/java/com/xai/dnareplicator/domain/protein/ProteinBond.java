package com.xai.dnareplicator.domain.protein;

import com.xai.dnareplicator.algorithm.protein.ProteinFoldingConstants;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bond between two amino acids for folding approximation (CLRS Chapter 35).
 */
public class ProteinBond {

    private final AminoAcid aminoAcid1;
    private final AminoAcid aminoAcid2;
    private final double energy;
    private final String direction;

    public ProteinBond(AminoAcid aminoAcid1, AminoAcid aminoAcid2, double energy, String direction) {
        this.aminoAcid1 = aminoAcid1;
        this.aminoAcid2 = aminoAcid2;
        this.energy = energy;
        this.direction = direction;
    }

    public AminoAcid getAminoAcid1() {
        return aminoAcid1;
    }

    public AminoAcid getAminoAcid2() {
        return aminoAcid2;
    }

    public double getEnergy() {
        return energy;
    }

    public String getDirection() {
        return direction;
    }

    public boolean passesModularFilter() {
        int energyInt = (int) Math.round(energy * 100);
        int hash = energyInt % ProteinFoldingConstants.LARGE_PRIME;
        return ProteinFoldingConstants.KNOWN_HASHES.contains(hash);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("aminoAcid1", aminoAcid1.getId());
        json.put("aminoAcid2", aminoAcid2.getId());
        json.put("energy", energy);
        json.put("direction", direction);
        return json;
    }
}
