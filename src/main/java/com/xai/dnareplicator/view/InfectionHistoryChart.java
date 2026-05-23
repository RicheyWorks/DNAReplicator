// view/InfectionHistoryChart.java
package view;

import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import java.util.List;

public class InfectionHistoryChart {
    private BarChart<String, Number> chart;
    private XYChart.Series<String, Number> successSeries;
    private XYChart.Series<String, Number> failureSeries;

    public InfectionHistoryChart() {
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Level");
        yAxis.setLabel("Count");
        chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("Infection History");
        chart.setPrefHeight(150);
        chart.setPrefWidth(800);
        chart.setLayoutY(450);

        successSeries = new XYChart.Series<>();
        successSeries.setName("Success");
        failureSeries = new XYChart.Series<>();
        failureSeries.setName("Failure");
        chart.getData().addAll(successSeries, failureSeries);
    }

    public BarChart<String, Number> getChart() {
        return chart;
    }

    public void updateChart(List<Boolean> history) {
        successSeries.getData().clear();
        failureSeries.getData().clear();
        int successCount = 0;
        int failureCount = 0;
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i)) {
                successCount++;
            } else {
                failureCount++;
            }
            successSeries.getData().add(new XYChart.Data<>("Level " + (i + 1), successCount));
            failureSeries.getData().add(new XYChart.Data<>("Level " + (i + 1), failureCount));
        }
    }
}
