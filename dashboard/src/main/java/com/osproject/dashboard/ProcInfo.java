package com.osproject.dashboard;

/**
 * Immutable snapshot of kernel telemetry for a single PID,
 * mirroring the {@code struct proc_info} defined in the kernel syscall.
 */
public record ProcInfo(
        int    pid,
        long   nvcsw,
        long   nivcsw,
        long   minFlt,
        long   majFlt,
        long   timestampMs
) {}
