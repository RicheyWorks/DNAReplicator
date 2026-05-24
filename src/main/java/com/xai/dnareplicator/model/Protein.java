package com.xai.dnareplicator.model;

public class Protein {
    private double x, y;
    private boolean isFolded;
    private boolean foldFailed;
    private String enzymeType;
    private double viralResistance;

    public Protein(double x, double y, String enzymeType) {
        this.x = x;
        this.y = y;
        this.isFolded = false;
        this.foldFailed = false;
        this.enzymeType = enzymeType;
        this.viralResistance = evolveViralResistance();
    }

    private double evolveViralResistance() {
        return new java.util.Random().nextDouble() * 0.5;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public boolean isFolded() {
        return isFolded;
    }

    public boolean isFoldFailed() {
        return foldFailed;
    }

    public String getEnzymeType() {
        return enzymeType;
    }

    public double getViralResistance() {
        return viralResistance;
    }

    public void fold() {
        this.isFolded = true;
    }

    public void failFold() {
        this.foldFailed = true;
    }
}
