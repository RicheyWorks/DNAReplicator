package com.xai.dnareplicator.model;

import java.util.ArrayList;
import java.util.List;

public class Cell {
    private double x, y;
    private boolean isCompromised;
    private double resistance;

    // ✅ NEW: graph + pathfinding support
    private List<Cell> neighbors;
    private double distance;

    public Cell(double x, double y, double resistance) {
        this.x = x;
        this.y = y;
        this.isCompromised = false;
        this.resistance = resistance;

        // ✅ initialize new fields
        this.neighbors = new ArrayList<>();
        this.distance = Double.MAX_VALUE; // standard for pathfinding
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public boolean isCompromised() {
        return isCompromised;
    }

    public double getResistance() {
        return resistance;
    }

    public void compromise() {
        this.isCompromised = true;
    }

    // =========================
    // ✅ NEW METHODS (minimal)
    // =========================

    public List<Cell> getNeighbors() {
        return neighbors;
    }

    public void addNeighbor(Cell cell) {
        if (cell != null && !neighbors.contains(cell)) {
            neighbors.add(cell);
        }
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }
}
