package com.xai.dnareplicator.controller;

import com.xai.dnareplicator.model.DNAFragment;
import com.xai.dnareplicator.model.Protein;
import com.xai.dnareplicator.model.SimulationState;
import com.xai.dnareplicator.view.SimulationView;
import org.springframework.stereotype.Component;
import com.xai.dnareplicator.service.DNAService;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import com.xai.dnareplicator.config.Config;
@Component
public class SimulationController {

    private final SimulationView view;
    private final DNAService dnaService;
    private final ProteinService proteinService;
    private final InfectionEngine infectionEngine;

    public SimulationController(SimulationView view,
                                DNAService dnaService,
                                ProteinService proteinService,
                                InfectionEngine infectionEngine) {
        this.view = view;
        this.dnaService = dnaService;
        this.proteinService = proteinService;
        this.infectionEngine = infectionEngine;

        dnaService.spawnDNAFragments(2);
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        view.getSpliceButton().setOnAction(e -> {
            dnaService.setMutationRate(view.getMutationSlider().getValue());
            dnaService.spliceDNA();
        });

        view.getFoldButton().setOnAction(e -> {
            try {
                proteinService.foldProteins();
            } catch (DNAProcessingException ex) {
                view.updateStatus("Protein folding failed: " + ex.getMessage());
            }
        });

        view.getBuildButton().setOnAction(e ->
                infectionEngine.buildVirus(dnaService.getProteins(), proteinService));

        view.getSimulateButton().setOnAction(e ->
                infectionEngine.simulateInfection());

        view.getSaveButton().setOnAction(e -> saveState());
        view.getLoadButton().setOnAction(e -> loadState());
        view.getResetButton().setOnAction(e -> resetSimulation());
        view.getUndoButton().setOnAction(e -> dnaService.undoSplice());
        view.getExportButton().setOnAction(e -> dnaService.exportDNA());
        view.getImportButton().setOnAction(e -> dnaService.importDNA());
    }

    public void startSimulation() {
        infectionEngine.startSimulation();
    }

    private void saveState() {
        SimulationState state = new SimulationState();
        state.setDnaFragments(dnaService.getDNAFragments());
        state.setProteins(dnaService.getProteins());
        state.setVirus(infectionEngine.getVirus());
        state.setLevel(infectionEngine.getLevel().getLevel());
        state.setInfectedCells(infectionEngine.getVirologyModel().getInfectedCells());
        state.setResistantCells(infectionEngine.getVirologyModel().getResistantCells());
        state.setInfectionHistory(infectionEngine.getVirologyModel().getInfectionHistory());
        state.setMutationRate(view.getMutationSlider().getValue());

        try (PrintWriter writer = new PrintWriter(new FileWriter(Config.STATE_FILE_PATH))) {

            writer.println("DNA Fragments: " + state.getDnaFragments().size());
            for (DNAFragment fragment : state.getDnaFragments()) {
                writer.println(fragment.getX() + "," + fragment.getY() + "," +
                        fragment.getName() + "," + fragment.isSelected() + "," +
                        fragment.getBasePairs());
            }

            writer.println("Proteins: " + state.getProteins().size());
            for (Protein protein : state.getProteins()) {
                writer.println(protein.getX() + "," + protein.getY() + "," +
                        protein.isFolded() + "," + protein.isFoldFailed() + "," +
                        protein.getEnzymeType() + "," + protein.getViralResistance());
            }

            writer.println("Virus: " + (state.getVirus() != null));
            if (state.getVirus() != null) {
                writer.println(state.getVirus().getX() + "," +
                        state.getVirus().getY() + "," +
                        state.getVirus().getResistanceFactor() + "," +
                        state.getVirus().getName() + "," +
                        state.getVirus().getInfectionEfficiency());
            }

            writer.println("Level: " + state.getLevel());
            writer.println("Infected Cells: " + state.getInfectedCells());
            writer.println("Resistant Cells: " + state.getResistantCells());

            writer.println("Infection History: " + state.getInfectionHistory().size());
            for (Boolean success : state.getInfectionHistory()) {
                writer.println(success);
            }

            writer.println("Mutation Rate: " + state.getMutationRate());

            view.updateStatus("Virus state saved to " + Config.STATE_FILE_PATH + "!");
        } catch (IOException e) {
            view.updateStatus("Failed to save state: " + e.getMessage());
        }
    }

    private void loadState() {
        SimulationState state = new SimulationState();

        try (BufferedReader reader = new BufferedReader(new FileReader(Config.STATE_FILE_PATH))) {

            resetSimulation();

            String line = reader.readLine();
            int dnaCount = Integer.parseInt(line.split(": ")[1]);

            for (int i = 0; i < dnaCount; i++) {
                line = reader.readLine();
                String[] parts = line.split(",");

                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                String name = parts[2];
                boolean isSelected = Boolean.parseBoolean(parts[3]);
                String basePairs = parts[4];

                DNAFragment fragment = new DNAFragment(x, y, name);
                fragment.setSelected(isSelected);

                dnaService.getDNAFragments().add(fragment);

                view.addDNAFragment(fragment, x, y, basePairs, isSelected,
                        () -> dnaService.toggleSelection(fragment),
                        () -> dnaService.updateFragmentPosition(fragment));
            }

            line = reader.readLine();
            int proteinCount = Integer.parseInt(line.split(": ")[1]);

            for (int i = 0; i < proteinCount; i++) {
                line = reader.readLine();
                String[] parts = line.split(",");

                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                boolean isFolded = Boolean.parseBoolean(parts[2]);
                boolean foldFailed = Boolean.parseBoolean(parts[3]);
                String enzymeType = parts[4];
                double viralResistance = Double.parseDouble(parts[5]);

                Protein protein = new Protein(x, y, enzymeType);

                if (isFolded) protein.fold();
                if (foldFailed) protein.failFold();

                dnaService.getProteins().add(protein);

                view.addProtein(protein, x, y, isFolded, foldFailed,
                        enzymeType, viralResistance);
            }

            line = reader.readLine();
            boolean hasVirus = Boolean.parseBoolean(line.split(": ")[1]);

            if (hasVirus) {
                line = reader.readLine();
                String[] parts = line.split(",");

                double x = Double.parseDouble(parts[0]);
                double y = Double.parseDouble(parts[1]);
                double resistanceFactor = Double.parseDouble(parts[2]);
                String name = parts[3];

                infectionEngine.buildVirus(dnaService.getProteins(), proteinService);
                infectionEngine.getVirus().move(x - 400, y - 300);

                view.updateVirus(infectionEngine.getVirus(), x, y);
                view.updateVirusInfo(name, resistanceFactor);
            }

            line = reader.readLine();
            state.setLevel(Integer.parseInt(line.split(": ")[1]));

            line = reader.readLine();
            state.setInfectedCells(Integer.parseInt(line.split(": ")[1]));

            line = reader.readLine();
            state.setResistantCells(Integer.parseInt(line.split(": ")[1]));

            line = reader.readLine();
            int historyCount = Integer.parseInt(line.split(": ")[1]);

            List<Boolean> history = new ArrayList<>();
            for (int i = 0; i < historyCount; i++) {
                history.add(Boolean.parseBoolean(reader.readLine()));
            }

            state.setInfectionHistory(history);

            line = reader.readLine();
            state.setMutationRate(Double.parseDouble(line.split(": ")[1]));

            view.getMutationSlider().setValue(state.getMutationRate());

            infectionEngine.getLevel().setLevel(state.getLevel());
            infectionEngine.getVirologyModel().setInfectedCells(state.getInfectedCells());
            infectionEngine.getVirologyModel().setResistantCells(state.getResistantCells());
            infectionEngine.getVirologyModel().setInfectionHistory(state.getInfectionHistory());

            view.updateLevel(state.getLevel());
            view.updateVirology(state.getInfectedCells(), state.getResistantCells());
            view.updateInfectionHistory(state.getInfectionHistory());

            view.updateStatus("Virus state loaded from " + Config.STATE_FILE_PATH + "!");
        } catch (IOException e) {
            view.updateStatus("Failed to load state: " + e.getMessage());
        }
    }

    private void resetSimulation() {
        dnaService.clear();
        infectionEngine.reset();
        dnaService.spawnDNAFragments(2);
        view.getMutationSlider().setValue(0.2);
        view.updateStatus("Simulation reset!");
    }
}
