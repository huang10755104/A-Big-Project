package com.osproject.dashboard;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.Locale;

/**
 * Background {@link ScheduledService} that polls the QEMU guest every
 * {@code POLL_INTERVAL_MS} milliseconds via SSH and returns a {@link ProcInfo}
 * snapshot.
 *
 * <p>The remote helper binary {@code /usr/local/bin/get_proc_info <PID>}
 * must be deployed on the guest; it calls {@code sys_get_proc_info} and
 * prints one line in the format:
 * <pre>nvcsw=&lt;n&gt; nivcsw=&lt;n&gt; min_flt=&lt;n&gt; maj_flt=&lt;n&gt;</pre>
 *
 * Connection details default to {@code localhost:2222} (QEMU hostfwd) and
 * can be overridden before the service is started.
 *
 * <p><strong>Performance Optimization:</strong> This service maintains a persistent
 * SSH session instead of creating a new connection for each poll. If the connection
 * drops, it will automatically reconnect on the next poll attempt.
 */
public class TelemetryService extends ScheduledService<ProcInfo> {

    /** Poll interval – 1 second. */
    public static final long POLL_INTERVAL_MS = 1_000;

    /** Maximum reconnection attempts before giving up. */
    private static final int MAX_RECONNECT_ATTEMPTS = 3;

    // SSH connection parameters (defaults target the QEMU instance)
    private String  host     = "localhost";
    private int     port     = 2222;
    private String  username = "root";
    private String  password = "";       // set via withPassword() or use key-based auth
    private String  keyPath  = null;     // path to private key, if used
    private int     targetPid = 1;

    // Persistent SSH session and JSch instance
    private JSch    jsch     = null;
    private Session session  = null;

    public TelemetryService() {
        setPeriod(Duration.millis(POLL_INTERVAL_MS));
        setRestartOnFailure(true);
    }

    @Override
    protected void succeeded() {
        super.succeeded();
    }

    @Override
    protected void cancelled() {
        super.cancelled();
        closeSession();
    }

    @Override
    protected void failed() {
        super.failed();
        closeSession();
    }

    // -----------------------------------------------------------------------
    // Fluent setters
    // -----------------------------------------------------------------------

    public TelemetryService withHost(String host)           { this.host = host;             return this; }
    public TelemetryService withPort(int port)              { this.port = port;             return this; }
    public TelemetryService withUsername(String u)          { this.username = u;            return this; }
    public TelemetryService withPassword(String p)          { this.password = p;            return this; }
    public TelemetryService withKeyPath(String k)           { this.keyPath = k;             return this; }
    public TelemetryService withTargetPid(int pid)          { this.targetPid = pid;         return this; }

    // -----------------------------------------------------------------------
    // ScheduledService contract
    // -----------------------------------------------------------------------

    @Override
    protected Task<ProcInfo> createTask() {
        final int pid = targetPid;

        return new Task<>() {
            @Override
            protected ProcInfo call() throws Exception {
                // Ensure we have a valid session (create or reconnect if needed)
                ensureConnected();
                return fetchTelemetry(pid);
            }
        };
    }

    // -----------------------------------------------------------------------
    // SSH session management
    // -----------------------------------------------------------------------

    /**
     * Ensures that the SSH session is connected. If the session is null or not
     * connected, this method will attempt to establish a new connection with
     * automatic retry logic.
     */
    private synchronized void ensureConnected() throws Exception {
        if (session != null && session.isConnected()) {
            return; // Already connected
        }

        // Need to (re)connect
        closeSession();

        int attempts = 0;
        Exception lastException = null;

        while (attempts < MAX_RECONNECT_ATTEMPTS) {
            try {
                connectSession();
                return; // Success
            } catch (Exception e) {
                lastException = e;
                attempts++;
                if (attempts < MAX_RECONNECT_ATTEMPTS) {
                    // Brief pause before retry
                    Thread.sleep(500);
                }
            }
        }

        // All attempts failed
        throw new Exception("Failed to connect after " + MAX_RECONNECT_ATTEMPTS +
                          " attempts: " + (lastException != null ? lastException.getMessage() : "unknown error"));
    }

    /**
     * Establishes a new SSH session using the configured connection parameters.
     */
    private void connectSession() throws Exception {
        if (jsch == null) {
            jsch = new JSch();
            if (keyPath != null && !keyPath.isBlank()) {
                jsch.addIdentity(keyPath);
            }
        }

        String targetHost = resolveLoopbackHost();

        session = jsch.getSession(username, targetHost, port);
        if (keyPath == null || keyPath.isBlank()) {
            session.setPassword(password);
        }

        Properties config = new Properties();
        // StrictHostKeyChecking is disabled for QEMU dev environments only.
        // For production use, set StrictHostKeyChecking=yes and provide
        // a known_hosts file by calling jsch.setKnownHosts(path).
        config.put("StrictHostKeyChecking", "no");
        // Windows firewalls sometimes block IPv6 loopback; force IPv4 loopback when host=localhost.
        config.put("PreferredAuthentications", "publickey,password,keyboard-interactive");
        session.setConfig(config);
        session.setTimeout(5_000);
        session.connect();
    }

    /**
     * Closes the current SSH session if it exists.
     */
    private synchronized void closeSession() {
        if (session != null) {
            if (session.isConnected()) {
                session.disconnect();
            }
            session = null;
        }
    }

    // -----------------------------------------------------------------------
    // SSH telemetry retrieval
    // -----------------------------------------------------------------------

    /**
     * Fetches telemetry data using the persistent SSH session.
     * Uses a new ChannelExec for each command execution.
     */
    private ProcInfo fetchTelemetry(int pid) throws Exception {
        ChannelExec channel = null;
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("/usr/local/bin/get_proc_info " + pid);
            channel.setErrStream(System.err);

            InputStream in = channel.getInputStream();
            channel.connect();

            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                line = reader.readLine();
            }

            return parseProcInfoLine(pid, line);
        } finally {
            if (channel != null && channel.isConnected()) {
                channel.disconnect();
            }
        }
    }

    private String resolveLoopbackHost() {
        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase(Locale.ENGLISH)
                .contains("win");

        if (isWindows && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host))) {
            // Avoid Windows firewall rules that may drop IPv6 localhost traffic.
            return "127.0.0.1";
        }

        return host;
    }

    /**
     * Parses a line of the form:
     * {@code nvcsw=123 nivcsw=45 min_flt=6789 maj_flt=0}
     */
    private ProcInfo parseProcInfoLine(int pid, String line) {
        if (line == null || line.isBlank()) {
            throw new IllegalStateException("Empty response from QEMU guest");
        }

        long nvcsw = 0, nivcsw = 0, minFlt = 0, majFlt = 0;
        for (String token : line.trim().split("\\s+")) {
            String[] kv = token.split("=", 2);
            if (kv.length != 2) continue;
            long val = Long.parseLong(kv[1].trim());
            switch (kv[0].trim()) {
                case "nvcsw"   -> nvcsw  = val;
                case "nivcsw"  -> nivcsw = val;
                case "min_flt" -> minFlt = val;
                case "maj_flt" -> majFlt = val;
            }
        }
        return new ProcInfo(pid, nvcsw, nivcsw, minFlt, majFlt, System.currentTimeMillis());
    }
}
