package com.xai.dnareplicator.model;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public class VirologyModel {
    private int infectedCells;
    private int resistantCells;
    private List<Boolean> infectionHistory;

    public VirologyModel() {
        this.infectedCells = 0;
        this.resistantCells = 0;
        this.infectionHistory = new ArrayList<>();
    }

    public void recordInfection(boolean success) {
        if (success) {
            infectedCells++;
        } else {
            resistantCells++;
        }
        infectionHistory.add(success);
    }

    public int getInfectedCells() {
        return infectedCells;
    }

    public int getResistantCells() {
        return resistantCells;
    }

    public List<Boolean> getInfectionHistory() {
        return infectionHistory;
    }

    public void setInfectedCells(int infectedCells) {
        this.infectedCells = infectedCells;
    }

    public void setResistantCells(int resistantCells) {
        this.resistantCells = resistantCells;
    }

    public void setInfectionHistory(List<Boolean> infectionHistory) {
        this.infectionHistory = (infectionHistory != null)
                ? new ArrayList<>(infectionHistory)
                : new ArrayList<>();
    }

    public void reset() {
        infectedCells = 0;
        resistantCells = 0;
        infectionHistory.clear();
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("infectedCells", infectedCells);
        json.put("resistantCells", resistantCells);
        json.put("infectionHistory", infectionHistory);
        return json;
    }

    public void fromJSON(JSONObject json) {
        infectedCells = json.getInt("infectedCells");
        resistantCells = json.getInt("resistantCells");
        infectionHistory = new ArrayList<>();
        for (Object success : json.getJSONArray("infectionHistory")) {
            infectionHistory.add((Boolean) success);
        }
    }
}
