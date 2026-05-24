package com.xai.dnareplicator.controller;

import com.xai.dnareplicator.application.TutorialService;
import com.xai.dnareplicator.application.protein.FoldingResult;
import com.xai.dnareplicator.application.protein.ProteinFoldingOrchestrator;
import com.xai.dnareplicator.model.Protein;
import com.xai.dnareplicator.model.SimulationSession;
import com.xai.dnareplicator.presentation.contract.SimulationViewPort;
import com.xai.dnareplicator.presentation.javafx.JavaFxExecutor;
import javafx.concurrent.Task;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Application service: coordinates protein folding UI workflow and delegates algorithms to
 * {@link ProteinFoldingOrchestrator}.
 */
@Component
public class ProteinService {

    private final List<Protein> proteins;
    private final SimulationViewPort viewPort;
    private final JavaFxExecutor javaFxExecutor;
    private final ProteinFoldingOrchestrator foldingOrchestrator;
    private final TutorialService tutorialService;

    public ProteinService(
            SimulationSession session,
            SimulationViewPort viewPort,
            JavaFxExecutor javaFxExecutor,
            ProteinFoldingOrchestrator foldingOrchestrator,
            TutorialService tutorialService) {
        if (session == null) {
            throw new IllegalArgumentException("SimulationSession cannot be null");
        }
        if (viewPort == null) {
            throw new IllegalArgumentException("SimulationViewPort cannot be null");
        }
        if (javaFxExecutor == null) {
            throw new IllegalArgumentException("JavaFxExecutor cannot be null");
        }
        if (foldingOrchestrator == null) {
            throw new IllegalArgumentException("ProteinFoldingOrchestrator cannot be null");
        }
        if (tutorialService == null) {
            throw new IllegalArgumentException("TutorialService cannot be null");
        }
        this.proteins = session.getProteins();
        this.viewPort = viewPort;
        this.javaFxExecutor = javaFxExecutor;
        this.foldingOrchestrator = foldingOrchestrator;
        this.tutorialService = tutorialService;
    }

    public void foldProteins() throws DNAProcessingException {
        if (proteins.isEmpty()) {
            viewPort.updateStatus("No proteins to fold!");
            return;
        }

        viewPort.updateStatus("Folding proteins...");
        Task<Void> foldingTask = new Task<>() {
            @Override
            protected Void call() {
                double foldingProgress = 0;
                while (foldingProgress < 1) {
                    foldingProgress += 0.01;
                    final double progress = foldingProgress;
                    javaFxExecutor.runLater(() -> {
                        viewPort.showFoldingProgress(progress);
                        for (Protein protein : proteins) {
                            viewPort.animateProteinFolding(protein, progress);
                        }
                    });
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        javaFxExecutor.runLater(() ->
                                viewPort.updateStatus("Folding interrupted: " + e.getMessage()));
                        return null;
                    }
                }

                javaFxExecutor.runLater(() -> {
                    try {
                        String lastInsight = "";
                        boolean anyFolded = false;
                        for (Protein protein : proteins) {
                            if (protein == null) {
                                viewPort.updateStatus("Skipping null protein!");
                                continue;
                            }
                            FoldingResult result = foldingOrchestrator.foldProtein(protein);
                            viewPort.updateStatus(result.statusMessage());
                            if (result.algorithmInsight() != null && !result.algorithmInsight().isBlank()) {
                                lastInsight = result.algorithmInsight();
                            }
                            if (protein.isFolded()) {
                                anyFolded = true;
                            }
                            viewPort.updateProtein(
                                    protein,
                                    protein.getX(),
                                    protein.getY(),
                                    protein.isFolded(),
                                    protein.isFoldFailed());
                        }
                        if (!lastInsight.isBlank()) {
                            viewPort.updateAlgorithmInsight(lastInsight);
                        }
                        if (anyFolded) {
                            tutorialService.onFoldCompleted();
                        }
                        viewPort.hideFoldingProgress();
                        long foldedCount = proteins.stream().filter(p -> p != null && p.isFolded()).count();
                        viewPort.updateStatus("Protein folding complete! " + foldedCount + " folded correctly.");
                    } catch (DNAProcessingException e) {
                        viewPort.updateStatus("Folding failed: " + e.getMessage());
                    }
                });
                return null;
            }
        };
        new Thread(foldingTask).start();
    }

    public double calculateResistanceFactor() {
        if (proteins == null || proteins.isEmpty()) {
            return 0.0;
        }
        return proteins.stream()
                .filter(p -> p != null && p.isFolded())
                .mapToDouble(Protein::getViralResistance)
                .average()
                .orElse(0.0);
    }
}
