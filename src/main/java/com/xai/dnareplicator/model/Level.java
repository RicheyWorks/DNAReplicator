package com.xai.dnareplicator.model;

public class Level {
    private int level;
    private int fragmentsRequired;
    private double cellResistance;

    public Level() {
        this.level = 1;
        this.fragmentsRequired = 2;
        this.cellResistance = 0.1;
    }

    public int getLevel() {
        return level;
    }

    public int getFragmentsRequired() {
        return fragmentsRequired;
    }

    public double getCellResistance() {
        return cellResistance;
    }

    public void advanceLevel() {
        level++;
        fragmentsRequired = 2 + level;
        cellResistance = 0.1 + level * 0.05;
    }

    public void setLevel(int level) {
        this.level = level;
    }
}
