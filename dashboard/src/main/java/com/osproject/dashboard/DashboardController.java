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

import java.net.URL;
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
    @FXML private Label  statusLabel;

    // -----------------------------------------------------------------------
    // Chart series
    // -----------------------------------------------------------------------

    private final XYChart.Series<Number, Number> nvcsw  = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> nivcsw = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> minFlt = new XYChart.Series<>();
    private final XYChart.Series<Number, Number> majFlt = new XYChart.Series<>();

    // Elapsed tick counter (incremented every poll cycle)
    private final AtomicLong tick = new AtomicLong(0);

    private TelemetryService service;

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

        stopButton.setDisable(true);
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
