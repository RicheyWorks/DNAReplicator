package com.xai.dnareplicator;

import com.xai.dnareplicator.controller.SimulationController;
import com.xai.dnareplicator.view.SimulationView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class DNAReplicatorSimulator extends Application {

    private ConfigurableApplicationContext springContext;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void init() {
        springContext = new SpringApplicationBuilder(DNAReplicatorSimulator.class)
                .headless(false)
                .run(getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage primaryStage) {
        SimulationView view = springContext.getBean(SimulationView.class);
        SimulationController simulationController = springContext.getBean(SimulationController.class);

        primaryStage.setTitle("DNA Replicator / Virus Builder Simulator v6");
        primaryStage.setScene(new Scene(view.getRoot(), 800, 600));
        primaryStage.show();

        simulationController.startSimulation();
    }

    @Override
    public void stop() {
        if (springContext != null) {
            springContext.close();
        }
        Platform.exit();
    }
}
