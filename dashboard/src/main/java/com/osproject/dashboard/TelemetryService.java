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
 */
public class TelemetryService extends ScheduledService<ProcInfo> {

    /** Poll interval – 1 second. */
    public static final long POLL_INTERVAL_MS = 1_000;

    // SSH connection parameters (defaults target the QEMU instance)
    private String  host     = "localhost";
    private int     port     = 2222;
    private String  username = "root";
    private String  password = "";       // set via withPassword() or use key-based auth
    private String  keyPath  = null;     // path to private key, if used
    private int     targetPid = 1;

    public TelemetryService() {
        setPeriod(Duration.millis(POLL_INTERVAL_MS));
        setRestartOnFailure(true);
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
        final String  h  = host;
        final int     p  = port;
        final String  u  = username;
        final String  pw = password;
        final String  kp = keyPath;
        final int     pid = targetPid;

        return new Task<>() {
            @Override
            protected ProcInfo call() throws Exception {
                return fetchTelemetry(h, p, u, pw, kp, pid);
            }
        };
    }

    // -----------------------------------------------------------------------
    // SSH telemetry retrieval
    // -----------------------------------------------------------------------

    private ProcInfo fetchTelemetry(String host, int port,
                                    String user, String pass,
                                    String keyPath, int pid) throws Exception {
        JSch jsch = new JSch();

        if (keyPath != null && !keyPath.isBlank()) {
            jsch.addIdentity(keyPath);
        }

        Session session = jsch.getSession(user, host, port);
        if (keyPath == null || keyPath.isBlank()) {
            session.setPassword(pass);
        }

        Properties config = new Properties();
        // StrictHostKeyChecking is disabled for QEMU dev environments only.
        // For production use, set StrictHostKeyChecking=yes and provide
        // a known_hosts file by calling jsch.setKnownHosts(path).
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.setTimeout(5_000);
        session.connect();

        try {
            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand("/usr/local/bin/get_proc_info " + pid);
            channel.setErrStream(System.err);

            InputStream in = channel.getInputStream();
            channel.connect();

            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                line = reader.readLine();
            }
            channel.disconnect();

            return parseProcInfoLine(pid, line);
        } finally {
            session.disconnect();
        }
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
