package com.xai.dnareplicator.view;

import com.xai.dnareplicator.controller.ViewUpdater;
import com.xai.dnareplicator.model.Cell;
import com.xai.dnareplicator.model.DNAFragment;
import com.xai.dnareplicator.model.Protein;
import com.xai.dnareplicator.model.Virus;
import javafx.scene.Group;
import javafx.animation.AnimationTimer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Slider;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import org.springframework.stereotype.Component;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class SimulationView implements ViewUpdater {
    private Pane root;
    private Label statusLabel;
    private Label levelLabel;
    private Label virologyLabel;
    private Label virusLabel;
    private Label mutationLabel;
    private Slider mutationSlider;
    private ProgressBar foldingProgress;
    private Map<Object, Group> dnaFragmentNodes;
    private Map<Object, Group> proteinNodes;
    private Group virusNode;
    private Circle cellNode;
    private Button spliceButton;
    private Button foldButton;
    private Button buildButton;
    private Button simulateButton;
    private Button saveButton;
    private Button loadButton;
    private Button resetButton;
    private Button undoButton;
    private Button exportButton;
    private Button importButton;
    private InfectionHistoryChart infectionChart;

    public SimulationView() {
        root = new Pane();
        dnaFragmentNodes = new HashMap<>();
        proteinNodes = new HashMap<>();
        virusNode = null;
        cellNode = null;
        infectionChart = new InfectionHistoryChart();
        setupUI();
    }

    public Pane getRoot() {
        return root;
    }

    public Button getSpliceButton() {
        return spliceButton;
    }

    public Button getFoldButton() {
        return foldButton;
    }

    public Button getBuildButton() {
        return buildButton;
    }

    public Button getSimulateButton() {
        return simulateButton;
    }

    public Button getSaveButton() {
        return saveButton;
    }

    public Button getLoadButton() {
        return loadButton;
    }

    public Button getResetButton() {
        return resetButton;
    }

    public Button getUndoButton() {
        return undoButton;
    }

    public Button getExportButton() {
        return exportButton;
    }

    public Button getImportButton() {
        return importButton;
    }

    public Slider getMutationSlider() {
        return mutationSlider;
    }

    public String promptForVirusName() {
        TextInputDialog dialog = new TextInputDialog("Virus_" + System.currentTimeMillis());
        dialog.setTitle("Name Your Virus");
        dialog.setHeaderText("Enter a name for your virus:");
        dialog.setContentText("Virus Name:");
        Optional<String> result = dialog.showAndWait();
        return result.orElse("UnnamedVirus");
    }

    public String promptForEnzymeType() {
        TextInputDialog dialog = new TextInputDialog("CustomEnzyme");
        dialog.setTitle("Create Custom Enzyme");
        dialog.setHeaderText("Enter a name for your enzyme type:");
        dialog.setContentText("Enzyme Type:");
        Optional<String> result = dialog.showAndWait();
        return result.orElse("CustomEnzyme");
    }

    private void setupUI() {
        // Background
        root.setStyle("-fx-background-color: black;");

        // Status Label
        statusLabel = new Label("Select DNA fragments to splice!");
        statusLabel.setFont(new Font("Arial", 20));
        statusLabel.setTextFill(Color.GREEN);
        statusLabel.setLayoutX(10);
        statusLabel.setLayoutY(10);
        root.getChildren().add(statusLabel);

        // Level Label
        levelLabel = new Label("Level: 1");
        levelLabel.setFont(new Font("Arial", 16));
        levelLabel.setTextFill(Color.CYAN);
        levelLabel.setLayoutX(10);
        levelLabel.setLayoutY(40);
        root.getChildren().add(levelLabel);

        // Virology Label
        virologyLabel = new Label("Infected: 0 | Resistant: 0");
        virologyLabel.setFont(new Font("Arial", 16));
        virologyLabel.setTextFill(Color.CYAN);
        virologyLabel.setLayoutX(10);
        virologyLabel.setLayoutY(60);
        root.getChildren().add(virologyLabel);

        // Virus Label
        virusLabel = new Label("");
        virusLabel.setFont(new Font("Arial", 16));
        virusLabel.setTextFill(Color.RED);
        virusLabel.setLayoutX(10);
        virusLabel.setLayoutY(80);
        root.getChildren().add(virusLabel);

        // Mutation Rate Slider
        mutationLabel = new Label("Mutation Rate: 0.20");
        mutationLabel.setFont(new Font("Arial", 14));
        mutationLabel.setTextFill(Color.WHITE);
        mutationLabel.setLayoutX(10);
        mutationLabel.setLayoutY(100);
        root.getChildren().add(mutationLabel);

        mutationSlider = new Slider(0, 1, 0.2);
        mutationSlider.setLayoutX(10);
        mutationSlider.setLayoutY(120);
        mutationSlider.setShowTickLabels(true);
        mutationSlider.setShowTickMarks(true);
        mutationSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            mutationLabel.setText("Mutation Rate: " + String.format("%.2f", newVal));
        });
        root.getChildren().add(mutationSlider);

        // Folding Progress Bar
        foldingProgress = new ProgressBar(0);
        foldingProgress.setLayoutX(10);
        foldingProgress.setLayoutY(150);
        foldingProgress.setVisible(false);
        root.getChildren().add(foldingProgress);

        // Buttons
        spliceButton = new Button("Splice DNA");
        spliceButton.setLayoutX(10);
        spliceButton.setLayoutY(180);
        root.getChildren().add(spliceButton);

        foldButton = new Button("Fold Proteins");
        foldButton.setLayoutX(10);
        foldButton.setLayoutY(210);
        root.getChildren().add(foldButton);

        buildButton = new Button("Build Virus");
        buildButton.setLayoutX(10);
        buildButton.setLayoutY(240);
        root.getChildren().add(buildButton);

        simulateButton = new Button("Simulate Infection");
        simulateButton.setLayoutX(10);
        simulateButton.setLayoutY(270);
        root.getChildren().add(simulateButton);

        saveButton = new Button("Save Virus");
        saveButton.setLayoutX(10);
        saveButton.setLayoutY(300);
        root.getChildren().add(saveButton);

        loadButton = new Button("Load Virus");
        loadButton.setLayoutX(10);
        loadButton.setLayoutY(330);
        root.getChildren().add(loadButton);

        resetButton = new Button("Reset Simulation");
        resetButton.setLayoutX(10);
        resetButton.setLayoutY(360);
        root.getChildren().add(resetButton);

        undoButton = new Button("Undo Splice");
        undoButton.setLayoutX(10);
        undoButton.setLayoutY(390);
        root.getChildren().add(undoButton);

        exportButton = new Button("Export DNA (.fasta)");
        exportButton.setLayoutX(10);
        exportButton.setLayoutY(420);
        root.getChildren().add(exportButton);

        importButton = new Button("Import DNA (.fasta)");
        importButton.setLayoutX(10);
        importButton.setLayoutY(450);
        root.getChildren().add(importButton);

        // Infection History Chart
        root.getChildren().add(infectionChart.getChart());
    }

    public void updateStatus(String message) {
        statusLabel.setText(message);
        playSound("click.wav");
    }

    public void updateLevel(int level) {
        levelLabel.setText("Level: " + level);
    }

    public void updateVirology(int infected, int resistant) {
        virologyLabel.setText("Infected: " + infected + " | Resistant: " + resistant);
    }

    public void updateVirusInfo(String name, double resistance) {
        virusLabel.setText("Virus: \"" + name + "\" | Resistance: " + String.format("%.2f", resistance));
    }

    public void clearVirusInfo() {
        virusLabel.setText("");
    }

    public void updateInfectionHistory(List<Boolean> history) {
        infectionChart.updateChart(history);
    }

    public void showFoldingProgress(double progress) {
        foldingProgress.setProgress(progress);
        foldingProgress.setVisible(true);
    }

    public void hideFoldingProgress() {
        foldingProgress.setVisible(false);
    }

    public void addDNAFragment(Object fragment, double x, double y, String basePairs, boolean isSelected, Runnable onClick, Runnable onDrag) {
        Group group = new Group();
        Line line1 = new Line(x, y, x + 30, y + 30);
        line1.setStroke(isSelected ? Color.YELLOW : Color.CYAN);
        Line line2 = new Line(x + 30, y, x, y + 30);
        line2.setStroke(isSelected ? Color.YELLOW : Color.CYAN);
        Text basePairText = new Text(x, y - 10, basePairs);
        basePairText.setFill(Color.WHITE);
        basePairText.setFont(new Font("Arial", 10));
        group.getChildren().addAll(line1, line2, basePairText);

        double[] dragStart = new double[2];
        group.setOnMouseClicked(e -> onClick.run());
        group.setOnMousePressed(e -> {
            dragStart[0] = e.getSceneX() - x;
            dragStart[1] = e.getSceneY() - y;
        });
        group.setOnMouseDragged(e -> {
            double newX = e.getSceneX() - dragStart[0];
            double newY = e.getSceneY() - dragStart[1];
            line1.setStartX(newX);
            line1.setStartY(newY);
            line1.setEndX(newX + 30);
            line1.setEndY(newY + 30);
            line2.setStartX(newX + 30);
            line2.setStartY(newY);
            line2.setEndX(newX);
            line2.setEndY(newY + 30);
            basePairText.setX(newX);
            basePairText.setY(newY - 10);
            onDrag.run();
        });

        dnaFragmentNodes.put(fragment, group);
        root.getChildren().add(group);
    }

    public void updateDNAFragment(Object fragment, double x, double y, String basePairs, boolean isSelected) {
        Group group = dnaFragmentNodes.get(fragment);
        if (group != null) {
            Line line1 = (Line) group.getChildren().get(0);
            Line line2 = (Line) group.getChildren().get(1);
            Text basePairText = (Text) group.getChildren().get(2);
            line1.setStartX(x);
            line1.setStartY(y);
            line1.setEndX(x + 30);
            line1.setEndY(y + 30);
            line2.setStartX(x + 30);
            line2.setStartY(y);
            line2.setEndX(x);
            line2.setEndY(y + 30);
            basePairText.setX(x);
            basePairText.setY(y - 10);
            basePairText.setText(basePairs);
            line1.setStroke(isSelected ? Color.YELLOW : Color.CYAN);
            line2.setStroke(isSelected ? Color.YELLOW : Color.CYAN);
        }
    }

    public void showMutationFailure(Object fragment) {
        Group group = dnaFragmentNodes.get(fragment);
        if (group != null) {
            Line line1 = (Line) group.getChildren().get(0);
            Line line2 = (Line) group.getChildren().get(1);
            double x = line1.getStartX();
            double y = line1.getStartY();
            AnimationTimer sparkTimer = new AnimationTimer() {
                private double time = 0;
                @Override
                public void handle(long now) {
                    time += 0.1;
                    line1.setStroke(time % 0.2 < 0.1 ? Color.RED : Color.CYAN);
                    line2.setStroke(time % 0.2 < 0.1 ? Color.RED : Color.CYAN);
                    if (time > 2) {
                        line1.setStroke(Color.CYAN);
                        line2.setStroke(Color.CYAN);
                        stop();
                    }
                }
            };
            sparkTimer.start();
            Text xOverlay = new Text(x + 15, y + 15, "X");
            xOverlay.setFill(Color.RED);
            xOverlay.setFont(new Font("Arial", 20));
            group.getChildren().add(xOverlay);
        }
    }

    public void addProtein(Object protein, double x, double y, boolean isFolded, boolean foldFailed, String enzymeType, double viralResistance) {
        Circle circle = new Circle(x, y, 5, isFolded ? (foldFailed ? Color.GRAY : Color.ORANGE) : Color.YELLOW);
        Color enzymeColor = switch (enzymeType) {
            case "Polymerase" -> Color.BLUE;
            case "Protease" -> Color.GREEN;
            case "Integrase" -> Color.MAGENTA;
            default -> Color.WHITE;
        };
        Text traitText = new Text(x, y + 15, enzymeType + " (R: " + String.format("%.2f", viralResistance) + ")");
        traitText.setFill(enzymeColor);
        traitText.setFont(new Font("Arial", 8));
        Group group = new Group(circle, traitText);
        Tooltip tooltip = new Tooltip("Enzyme: " + enzymeType + "\nResistance: " + viralResistance + "\nStatus: " +
            (foldFailed ? "Failed" : (isFolded ? "Folded" : "Unfolded")));
        Tooltip.install(group, tooltip);
        proteinNodes.put(protein, group);
        root.getChildren().add(group);
    }

    public void animateProteinFolding(Object protein, double progress) {
        Group group = proteinNodes.get(protein);
        if (group != null) {
            Circle circle = (Circle) group.getChildren().get(0);
            circle.setRadius(5 + Math.sin(progress * 10) * 3);
        }
    }

    public void updateProtein(Object protein, double x, double y, boolean isFolded, boolean foldFailed) {
        Group group = proteinNodes.get(protein);
        if (group != null) {
            Circle circle = (Circle) group.getChildren().get(0);
            Text traitText = (Text) group.getChildren().get(1);
            circle.setCenterX(x);
            circle.setCenterY(y);
            traitText.setX(x);
            traitText.setY(y + 15);
            if (foldFailed) {
                circle.setFill(Color.GRAY);
                circle.setRadius(3);
                Text xOverlay = new Text(x, y, "X");
                xOverlay.setFill(Color.RED);
                xOverlay.setFont(new Font("Arial", 10));
                root.getChildren().add(xOverlay);
            } else if (isFolded) {
                circle.setFill(Color.ORANGE);
            }
        }
    }

    public void removeProtein(Object protein) {
        Group group = proteinNodes.remove(protein);
        if (group != null) {
            root.getChildren().remove(group);
        }
    }

    public void addVirus(Object virus, double x, double y) {
        Group group = new Group();
        Rectangle capsid = new Rectangle(x - 20, y - 20, 40, 40);
        capsid.setFill(null);
        capsid.setStroke(Color.RED);
        capsid.setRotate(45);
        Circle core = new Circle(x, y, 10, Color.PURPLE);
        group.getChildren().addAll(capsid, core);
        virusNode = group;
        root.getChildren().add(group);
    }

    public void updateVirus(Object virus, double x, double y) {
        if (virusNode != null) {
            virusNode.setLayoutX(x);
            virusNode.setLayoutY(y);
        }
    }

    public void animateVirusAttack(Object virus, double targetX, double targetY, Runnable onComplete) {
        AnimationTimer attackTimer = new AnimationTimer() {
            private double time = 0;
            @Override
            public void handle(long now) {
                time += 0.05;
                double scale = 1 + Math.sin(time * 5) * 0.2;
                virusNode.setScaleX(scale);
                virusNode.setScaleY(scale);
                if (time > 2) {
                    virusNode.setScaleX(1);
                    virusNode.setScaleY(1);
                    onComplete.run();
                    stop();
                }
            }
        };
        attackTimer.start();
    }

    public void removeVirus() {
        if (virusNode != null) {
            root.getChildren().remove(virusNode);
            virusNode = null;
        }
    }

    public void addCell(Object cell, double x, double y, boolean isCompromised) {
        Circle circle = new Circle(x, y, 30, isCompromised ? Color.RED : Color.GREEN);
        cellNode = circle;
        root.getChildren().add(circle);
    }

    public void updateCell(Object cell, boolean isCompromised) {
        if (cellNode != null) {
            cellNode.setFill(isCompromised ? Color.RED : Color.GREEN);
        }
    }

    public void removeCell() {
        if (cellNode != null) {
            root.getChildren().remove(cellNode);
            cellNode = null;
        }
    }

    public void clearAll() {
        root.getChildren().removeIf(node -> node != statusLabel && node != foldingProgress && node != spliceButton &&
            node != foldButton && node != buildButton && node != simulateButton && node != saveButton &&
            node != loadButton && node != resetButton && node != undoButton && node != exportButton &&
            node != importButton && node != levelLabel && node != virologyLabel && node != virusLabel &&
            node != mutationLabel && node != mutationSlider && node != infectionChart.getChart());
        dnaFragmentNodes.clear();
        proteinNodes.clear();
        virusNode = null;
        cellNode = null;
    }

    private void playSound(String soundFile) {
        try {
            Clip clip = AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(new File(soundFile)));
            clip.start();
        } catch (Exception e) {
            statusLabel.setText("[-] Sound error (missing " + soundFile + "): " + e.getMessage());
        }
    }

    @Override
    public void addDNAFragment(DNAFragment fragment, double x, double y, String basePairs, boolean selected,
                               Runnable onSelect, Runnable onDrag) {
        addDNAFragment((Object) fragment, x, y, basePairs, selected, onSelect, onDrag);
    }

    @Override
    public void updateDNAFragment(DNAFragment fragment, double x, double y, String basePairs, boolean selected) {
        updateDNAFragment((Object) fragment, x, y, basePairs, selected);
    }

    @Override
    public void removeDNAFragment(DNAFragment fragment) {
        Group group = dnaFragmentNodes.remove(fragment);
        if (group != null) {
            root.getChildren().remove(group);
        }
    }

    @Override
    public void showMutationFailure(DNAFragment fragment) {
        showMutationFailure((Object) fragment);
    }

    @Override
    public void addProtein(Protein protein, double x, double y, boolean selected, boolean assembled,
                           String enzymeType, double viralResistance) {
        // Map ViewUpdater's flags into the existing UI method's meaning.
        // "assembled" is treated as "isFolded"; fold-failure is unknown here, so default to false.
        addProtein((Object) protein, x, y, assembled, false, enzymeType, viralResistance);
    }

    public void addVirus(Virus virus, double x, double y) {
        addVirus((Object) virus, x, y);
    }

    public void updateVirus(Virus virus, double x, double y) {
        updateVirus((Object) virus, x, y);
    }

    public void animateVirusAttack(Virus virus, double targetX, double targetY, Runnable onComplete) {
        animateVirusAttack((Object) virus, targetX, targetY, onComplete);
    }

    public void addCell(Cell cell, double x, double y, boolean isCompromised) {
        addCell((Object) cell, x, y, isCompromised);
    }

    public void updateCell(Cell cell, boolean isCompromised) {
        updateCell((Object) cell, isCompromised);
    }

    public void animateProteinFolding(Protein protein, double progress) {
        animateProteinFolding((Object) protein, progress);
    }

    public void updateProtein(Protein protein, double x, double y, boolean isFolded, boolean foldFailed) {
        updateProtein((Object) protein, x, y, isFolded, foldFailed);
    }
}
