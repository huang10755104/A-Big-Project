package com.osproject.dashboard;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Entry point for the OS telemetry dashboard.
 * Loads the primary FXML layout and launches the JavaFX application.
 */
public class DashboardApp extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/osproject/dashboard/Dashboard.fxml"));
        Scene scene = new Scene(loader.load(), 900, 600);
        primaryStage.setTitle("OS Kernel Telemetry Dashboard");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
