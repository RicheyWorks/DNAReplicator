package com.xai.dnareplicator.application;

import com.xai.dnareplicator.presentation.contract.SimulationViewPort;
import org.springframework.stereotype.Component;

/**
 * Onboarding tutorial steps tied to the main player pipeline.
 */
@Component
public class TutorialService {

    private static final String[] STEP_TITLES = {
            "Step 1: Splice DNA",
            "Step 2: Fold Proteins",
            "Step 3: Build Virus",
            "Step 4: Simulate Infection",
            "Tutorial Complete"
    };

    private static final String[] STEP_BODIES = {
            "Select two DNA fragments, drag them close, then click Splice DNA. Uses LCS alignment (CLRS Ch.15).",
            "After splicing creates proteins, click Fold Proteins. Runs GA, simulated annealing, HMM, and more.",
            "Fold enough proteins, then Build Virus to assemble your pathogen.",
            "Click Simulate Infection. Dijkstra finds a path; BFS spreads infection across cells.",
            "You have seen the full loop. Experiment with mutation rate, save/load, and FASTA import."
    };

    private final SimulationViewPort viewPort;
    private int currentStep;

    public TutorialService(SimulationViewPort viewPort) {
        this.viewPort = viewPort;
        showStep(0);
    }

    public void showStep(int step) {
        currentStep = Math.max(0, Math.min(step, STEP_TITLES.length - 1));
        viewPort.updateTutorial(STEP_TITLES[currentStep], STEP_BODIES[currentStep]);
    }

    public void onSpliceCompleted() {
        showStep(1);
    }

    public void onFoldCompleted() {
        showStep(2);
    }

    public void onVirusBuilt() {
        showStep(3);
    }

    public void onInfectionSimulated() {
        showStep(4);
    }

    public void restart() {
        showStep(0);
    }

    public int getCurrentStep() {
        return currentStep;
    }
}
