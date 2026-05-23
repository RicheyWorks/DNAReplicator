package com.xai.dnareplicator.model;

public class Virus {
    private double x, y;
    private double resistanceFactor;
    private String name;
    private double infectionEfficiency;

    public Virus(double x, double y, double resistanceFactor, String name, double infectionEfficiency) {
        this.x = x;
        this.y = y;
        this.resistanceFactor = resistanceFactor;
        this.name = name;
        this.infectionEfficiency = infectionEfficiency;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getResistanceFactor() {
        return resistanceFactor;
    }

    public String getName() {
        return name;
    }

    public double getInfectionEfficiency() {
        return infectionEfficiency;
    }

    public void move(double dx, double dy) {
        x += dx;
        y += dy;
    }
}
