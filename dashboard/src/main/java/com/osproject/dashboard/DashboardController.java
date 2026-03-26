package com.osproject.dashboard;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Button;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JavaFX controller for {@code Dashboard.fxml}.
 *
 * <p>Wires a {@link TelemetryService} to four {@link LineChart} series:
 * <ul>
 *   <li>Voluntary context switches (nvcsw)</li>
 *   <li>Involuntary context switches (nivcsw)</li>
 *   <li>Minor page faults (min_flt)</li>
 *   <li>Major page faults (maj_flt)</li>
 * </ul>
 *
 * <p>The chart keeps at most {@value #MAX_DATA_POINTS} points to avoid
 * unbounded memory growth during long monitoring sessions.
 */
public class DashboardController implements Initializable {

    /** Maximum number of data points visible in the chart at once. */
    private static final int MAX_DATA_POINTS = 60;

    // -----------------------------------------------------------------------
    // FXML-injected controls
    // -----------------------------------------------------------------------

    @FXML private LineChart<Number, Number> telemetryChart;
    @FXML private NumberAxis xAxis;
    @FXML private NumberAxis yAxis;

    @FXML private TextField hostField;
    @FXML private TextField portField;
    @FXML private TextField userField;
    @FXML private TextField passField;
    @FXML private TextField pidField;

    @FXML private Button connectButton;
    @FXML private Button stopButton;
    @FXML private Button recordButton;
    @FXML private Button exportButton;
    @FXML private Button snapshotButton;
    @FXML private Label  statusLabel;
    @FXML private Label  rssLabel;

    // -----------------------------------------------------------------------
    // Chart series
    // -----------------------------------------------------------------------

    private final XYChart.Series<Number, Number> nvcsw  = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> nivcsw = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> minFlt = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> majFlt = new XYChart.Series<>();

    // Snapshot series for comparison (overlay)
    private final XYChart.Series<Number, Number> snapshotNvcsw  = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> snapshotNivcsw = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> snapshotMinFlt = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> snapshotMajFlt = new XYChart.Series<>();

    // Elapsed tick counter (incremented every poll cycle)
    private final AtomicLong tick = new AtomicLong(0);

    private TelemetryService service;

    // Recording state for CSV export
    private boolean isRecording = false;
    private final List<DataPoint> recordedData = new ArrayList<>();

    // Data point for CSV recording
    private static class DataPoint {
        long timestamp;
        long tick;
        long nvcsw;
        long nivcsw;
        long minFlt;
        long majFlt;
        long rss;

        DataPoint(long timestamp, long tick, long nvcsw, long nivcsw, long minFlt, long majFlt, long rss) {
            this.timestamp = timestamp;
            this.tick = tick;
            this.nvcsw = nvcsw;
            this.nivcsw = nivcsw;
            this.minFlt = minFlt;
            this.majFlt = majFlt;
            this.rss = rss;
        }
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        nvcsw.setName("Voluntary ctx-sw (nvcsw)");
        nivcsw.setName("Involuntary ctx-sw (nivcsw)");
        minFlt.setName("Minor page faults");
        majFlt.setName("Major page faults");

        telemetryChart.setAnimated(false);
        telemetryChart.getData().addAll(nvcsw, nivcsw, minFlt, majFlt);

        xAxis.setLabel("Time (s)");
        xAxis.setAutoRanging(true);
        yAxis.setLabel("Count (delta)");
        yAxis.setAutoRanging(true);

        // Initialize snapshot series (hidden by default)
        snapshotNvcsw.setName("Snapshot: nvcsw");
        snapshotNivcsw.setName("Snapshot: nivcsw");
        snapshotMinFlt.setName("Snapshot: min_flt");
        snapshotMajFlt.setName("Snapshot: maj_flt");

        stopButton.setDisable(true);
        exportButton.setDisable(true);
        snapshotButton.setDisable(true);
    }

    // -----------------------------------------------------------------------
    // Button handlers
    // -----------------------------------------------------------------------

    /**
     * Immutable snapshot of mutable delta state used inside the polling callback.
     * Avoids the "effectively final" requirement for lambdas while keeping
     * the code readable.
     */
    private static final class DeltaState {
        long prevNvcsw;
        long prevNivcsw;
        long prevMinFlt;
        long prevMajFlt;
        boolean first = true;
    }

    @FXML
    private void onConnect() {
        if (service != null && service.isRunning()) {
            service.cancel();
        }

        int port;
        int pid;
        try {
            port = portField.getText().isBlank() ? 2222 : Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            statusLabel.setText("Error: Port must be a number.");
            return;
        }
        try {
            pid = pidField.getText().isBlank() ? 1 : Integer.parseInt(pidField.getText().trim());
        } catch (NumberFormatException e) {
            statusLabel.setText("Error: PID must be a number.");
            return;
        }

        service = new TelemetryService()
                .withHost(hostField.getText().isBlank() ? "localhost" : hostField.getText().trim())
                .withPort(port)
                .withUsername(userField.getText().isBlank() ? "root" : userField.getText().trim())
                .withPassword(passField.getText())
                .withTargetPid(pid);

        final DeltaState state = new DeltaState();

        service.setOnSucceeded(e -> {
            ProcInfo info = (ProcInfo) e.getSource().getValue();
            // Already on JavaFX Application Thread, no need for Platform.runLater
            updateChart(info, state);
        });

        service.setOnFailed(e -> {
            Throwable ex = service.getException();
            // Already on JavaFX Application Thread, no need for Platform.runLater
            statusLabel.setText(
                    "Error: " + (ex != null ? ex.getMessage() : "unknown"));
        });

        tick.set(0);
        statusLabel.setText("Connected – polling PID " + pid);
        connectButton.setDisable(true);
        stopButton.setDisable(false);
        snapshotButton.setDisable(false);
        service.start();
    }

    @FXML
    private void onStop() {
        if (service != null) {
            service.cancel();
        }
        statusLabel.setText("Stopped.");
        connectButton.setDisable(false);
        stopButton.setDisable(true);
        snapshotButton.setDisable(true);
        if (isRecording) {
            exportButton.setDisable(false);
        }
    }

    @FXML
    private void onRecord() {
        if (!isRecording) {
            // Start recording
            isRecording = true;
            recordedData.clear();
            recordButton.setText("Stop Recording");
            recordButton.setStyle("-fx-background-color: #f44336; -fx-text-fill: white;");
            statusLabel.setText("Recording data...");
            exportButton.setDisable(true);
        } else {
            // Stop recording
            isRecording = false;
            recordButton.setText("Record");
            recordButton.setStyle("-fx-background-color: #ff9800; -fx-text-fill: white;");
            statusLabel.setText("Recording stopped. " + recordedData.size() + " data points captured.");
            exportButton.setDisable(false);
        }
    }

    @FXML
    private void onExport() {
        if (recordedData.isEmpty()) {
            statusLabel.setText("Error: No recorded data to export.");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Telemetry Data");
        fileChooser.setInitialFileName("telemetry_data.csv");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        Stage stage = (Stage) exportButton.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                // Write CSV header
                writer.println("Timestamp,Tick,NVCSW,NIVCSW,MinFlt,MajFlt,RSS");

                // Write data points
                for (DataPoint dp : recordedData) {
                    writer.printf("%d,%d,%d,%d,%d,%d,%d%n",
                            dp.timestamp, dp.tick, dp.nvcsw, dp.nivcsw,
                            dp.minFlt, dp.majFlt, dp.rss);
                }

                statusLabel.setText("Exported " + recordedData.size() + " data points to " + file.getName());
            } catch (IOException e) {
                statusLabel.setText("Error: Failed to export data - " + e.getMessage());
            }
        }
    }

    @FXML
    private void onSnapshot() {
        // Save current chart data as a snapshot for overlay comparison
        snapshotNvcsw.getData().clear();
        snapshotNivcsw.getData().clear();
        snapshotMinFlt.getData().clear();
        snapshotMajFlt.getData().clear();

        // Deep copy current series data
        for (XYChart.Data<Number, Number> data : nvcsw.getData()) {
            snapshotNvcsw.getData().add(new XYChart.Data<>(data.getXValue(), data.getYValue()));
        }
        for (XYChart.Data<Number, Number> data : nivcsw.getData()) {
            snapshotNivcsw.getData().add(new XYChart.Data<>(data.getXValue(), data.getYValue()));
        }
        for (XYChart.Data<Number, Number> data : minFlt.getData()) {
            snapshotMinFlt.getData().add(new XYChart.Data<>(data.getXValue(), data.getYValue()));
        }
        for (XYChart.Data<Number, Number> data : majFlt.getData()) {
            snapshotMajFlt.getData().add(new XYChart.Data<>(data.getXValue(), data.getYValue()));
        }

        // Add snapshot series to chart if not already added
        if (!telemetryChart.getData().contains(snapshotNvcsw)) {
            telemetryChart.getData().addAll(snapshotNvcsw, snapshotNivcsw, snapshotMinFlt, snapshotMajFlt);
        }

        statusLabel.setText("Snapshot saved! Now you can compare with live data.");
    }

    // -----------------------------------------------------------------------
    // Chart update helper
    // -----------------------------------------------------------------------

    /**
     * Appends one data point per series. Uses delta values to make transient
     * spikes visible even when absolute counters are large.
     */
    private void updateChart(ProcInfo info, DeltaState state) {
        long t = tick.incrementAndGet();

        long dNvcsw  = state.first ? 0 : info.nvcsw()  - state.prevNvcsw;
        long dNivcsw = state.first ? 0 : info.nivcsw() - state.prevNivcsw;
        long dMinFlt = state.first ? 0 : info.minFlt() - state.prevMinFlt;
        long dMajFlt = state.first ? 0 : info.majFlt() - state.prevMajFlt;

        state.prevNvcsw  = info.nvcsw();
        state.prevNivcsw = info.nivcsw();
        state.prevMinFlt = info.minFlt();
        state.prevMajFlt = info.majFlt();
        state.first = false;

        addPoint(nvcsw,  t, dNvcsw);
        addPoint(nivcsw, t, dNivcsw);
        addPoint(minFlt, t, dMinFlt);
        addPoint(majFlt, t, dMajFlt);

        trimSeries(nvcsw);
        trimSeries(nivcsw);
        trimSeries(minFlt);
        trimSeries(majFlt);

        // Update RSS display (convert pages to MB, assuming 4KB page size)
        double rssMB = (info.rss() * 4.0) / 1024.0;
        if (rssLabel != null) {
            rssLabel.setText(String.format("RSS: %.2f MB (%d pages)", rssMB, info.rss()));
        }

        // Record data point if recording is active
        if (isRecording) {
            recordedData.add(new DataPoint(
                    info.timestampMs(), t,
                    dNvcsw, dNivcsw, dMinFlt, dMajFlt, info.rss()));
        }
    }

    private void addPoint(XYChart.Series<Number, Number> series, long x, long y) {
        series.getData().add(new XYChart.Data<>(x, y));
    }

    private void trimSeries(XYChart.Series<Number, Number> series) {
        while (series.getData().size() > MAX_DATA_POINTS) {
            series.getData().remove(0);
        }
    }
}
