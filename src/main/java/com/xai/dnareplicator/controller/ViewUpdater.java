package com.xai.dnareplicator.controller;

import java.util.List;
import com.xai.dnareplicator.model.Cell;
import com.xai.dnareplicator.model.DNAFragment;
import com.xai.dnareplicator.model.Protein;
import com.xai.dnareplicator.model.Virus;

public interface ViewUpdater {
    void updateStatus(String message);

    void addDNAFragment(DNAFragment fragment, double x, double y, String basePairs, boolean selected,
                        Runnable onSelect, Runnable onDrag);
    void updateDNAFragment(DNAFragment fragment, double x, double y, String basePairs, boolean selected);
    void removeDNAFragment(DNAFragment fragment);
    void showMutationFailure(DNAFragment fragment);

    String promptForEnzymeType();

    String promptForVirusName();

    void addProtein(Protein protein, double x, double y, boolean selected, boolean assembled,
                    String enzymeType, double viralResistance);

    void showFoldingProgress(double progress);
    void hideFoldingProgress();
    void animateProteinFolding(Protein protein, double progress);
    void updateProtein(Protein protein, double x, double y, boolean isFolded, boolean foldFailed);

    void addVirus(Virus virus, double x, double y);
    void updateVirus(Virus virus, double x, double y);
    void updateVirusInfo(String name, double resistance);
    void clearVirusInfo();
    void removeVirus();
    void animateVirusAttack(Virus virus, double targetX, double targetY, Runnable onComplete);

    void addCell(Cell cell, double x, double y, boolean isCompromised);
    void updateCell(Cell cell, boolean isCompromised);
    void removeCell();

    void updateLevel(int level);
    void updateVirology(int infected, int resistant);
    void updateInfectionHistory(List<Boolean> history);

    void clearAll();
}
