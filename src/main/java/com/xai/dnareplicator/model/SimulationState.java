package com.xai.dnareplicator.model;

import java.util.ArrayList;
import java.util.List;

public class SimulationState {
    private List<DNAFragment> dnaFragments;
    private List<Protein> proteins;
    private Virus virus;
    private int level;
    private int infectedCells;
    private int resistantCells;
    private List<Boolean> infectionHistory;
    private double mutationRate;

    public SimulationState() {
        this.dnaFragments = new ArrayList<>();
        this.proteins = new ArrayList<>();
        this.virus = null;
        this.level = 1;
        this.infectedCells = 0;
        this.resistantCells = 0;
        this.infectionHistory = new ArrayList<>();
        this.mutationRate = 0.2;
    }

    public List<DNAFragment> getDnaFragments() {
        return dnaFragments;
    }

    public void setDnaFragments(List<DNAFragment> dnaFragments) {
        this.dnaFragments = dnaFragments;
    }

    public List<Protein> getProteins() {
        return proteins;
    }

    public void setProteins(List<Protein> proteins) {
        this.proteins = proteins;
    }

    public Virus getVirus() {
        return virus;
    }

    public void setVirus(Virus virus) {
        this.virus = virus;
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
        this.infectionHistory = infectionHistory;
    }

    public double getMutationRate() {
        return mutationRate;
    }

    public void setMutationRate(double mutationRate) {
        this.mutationRate = mutationRate;
    }
}
