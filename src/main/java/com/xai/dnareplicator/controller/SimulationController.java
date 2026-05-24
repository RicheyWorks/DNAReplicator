package com.xai.dnareplicator.controller;

import com.xai.dnareplicator.application.TutorialService;
import com.xai.dnareplicator.infrastructure.persistence.SimulationStateRepository;
import com.xai.dnareplicator.model.SimulationSession;
import com.xai.dnareplicator.service.DNAService;
import com.xai.dnareplicator.view.SimulationView;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SimulationController {

    private final SimulationView view;
    private final SimulationSession session;
    private final DNAService dnaService;
    private final ProteinService proteinService;
    private final InfectionEngine infectionEngine;
    private final SimulationStateRepository stateRepository;
    private final TutorialService tutorialService;

    public SimulationController(
            SimulationView view,
            SimulationSession session,
            DNAService dnaService,
            ProteinService proteinService,
            InfectionEngine infectionEngine,
            SimulationStateRepository stateRepository,
            TutorialService tutorialService) {
        this.view = view;
        this.session = session;
        this.dnaService = dnaService;
        this.proteinService = proteinService;
        this.infectionEngine = infectionEngine;
        this.stateRepository = stateRepository;
        this.tutorialService = tutorialService;

        dnaService.spawnDNAFragments(2);
        setupEventHandlers();
    }

    private void setupEventHandlers() {
        view.getSpliceButton().setOnAction(e -> {
            dnaService.setMutationRate(view.getMutationSlider().getValue());
            int proteinsBefore = dnaService.getProteins().size();
            dnaService.spliceDNA();
            if (dnaService.getProteins().size() > proteinsBefore) {
                tutorialService.onSpliceCompleted();
            }
        });

        view.getFoldButton().setOnAction(e -> {
            try {
                proteinService.foldProteins();
            } catch (DNAProcessingException ex) {
                view.updateStatus("Protein folding failed: " + ex.getMessage());
            }
        });

        view.getBuildButton().setOnAction(e -> {
            infectionEngine.buildVirus(dnaService.getProteins(), proteinService);
            if (infectionEngine.getVirus() != null) {
                tutorialService.onVirusBuilt();
            }
        });

        view.getSimulateButton().setOnAction(e -> infectionEngine.simulateInfection());

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
        try {
            stateRepository.save(dnaService, infectionEngine, view.getMutationSlider().getValue());
            view.updateStatus("Virus state saved to " + stateRepository.getStateFile().getPath() + "!");
        } catch (IOException e) {
            view.updateStatus("Failed to save state: " + e.getMessage());
        }
    }

    private void loadState() {
        try {
            SimulationStateRepository.LoadedSimulation loaded = stateRepository.load(
                    session, view, dnaService, infectionEngine, proteinService);
            view.getMutationSlider().setValue(loaded.mutationRate());
            view.updateStatus("Virus state loaded from " + stateRepository.getStateFile().getPath() + "!");
        } catch (IOException e) {
            view.updateStatus("Failed to load state: " + e.getMessage());
        }
    }

    private void resetSimulation() {
        dnaService.clear();
        infectionEngine.reset();
        tutorialService.restart();
        dnaService.spawnDNAFragments(2);
        view.getMutationSlider().setValue(0.2);
        view.updateStatus("Simulation reset!");
    }
}
