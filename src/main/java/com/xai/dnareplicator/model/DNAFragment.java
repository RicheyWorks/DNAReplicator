package com.xai.dnareplicator.model;

import java.util.Random;
import java.util.UUID;

public class DNAFragment {
    private final String id;
    private double x, y;
    private String name;
    private boolean isSelected;
    private String basePairs;

    public DNAFragment(double x, double y, String name) {
        this.id = UUID.randomUUID().toString();
        this.x = x;
        this.y = y;
        this.name = name;
        this.isSelected = false;
        this.basePairs = generateBasePairs();
    }

    private String generateBasePairs() {
        Random rand = new Random();
        StringBuilder sb = new StringBuilder();
        String bases = "AGTC";
        for (int i = 0; i < 10; i++) {
            sb.append(bases.charAt(rand.nextInt(4)));
        }
        return sb.toString();
    }

    public void mutate() {
        Random rand = new Random();
        StringBuilder newBasePairs = new StringBuilder(basePairs);
        int pos = rand.nextInt(basePairs.length());
        char currentBase = basePairs.charAt(pos);
        String bases = "AGTC";
        char newBase;
        do {
            newBase = bases.charAt(rand.nextInt(4));
        } while (newBase == currentBase);
        newBasePairs.setCharAt(pos, newBase);
        basePairs = newBasePairs.toString();
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public void setX(double x) {
        this.x = x;
    }

    public void setY(double y) {
        this.y = y;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        this.isSelected = selected;
    }

    public String getBasePairs() {
        return basePairs;
    }

    public void setBasePairs(String basePairs) {
        this.basePairs = basePairs;
    }
}
