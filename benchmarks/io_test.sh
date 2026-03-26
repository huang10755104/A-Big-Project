#!/usr/bin/env bash
# =============================================================================
# io_test.sh – I/O-bound benchmark for OS telemetry analysis (Task 4)
#
# Generates sustained disk and file-system activity so that page-fault and
# context-switch behaviour can be studied via sys_get_proc_info while the
# benchmark runs.
#
# Usage:
#   ./benchmarks/io_test.sh [DURATION_SECONDS] [FILE_SIZE_MB]
#
#   DURATION_SECONDS – how long to run the I/O loop (default: 30)
#   FILE_SIZE_MB     – size of the test file in MiB (default: 64)
# =============================================================================

set -euo pipefail

DURATION="${1:-30}"
FILE_SIZE_MB="${2:-64}"
TMPFILE="${TMPDIR:-/tmp}/io_test_$$"

echo "================================================="
echo " I/O Benchmark"
echo " Duration  : ${DURATION}s"
echo " File size : ${FILE_SIZE_MB} MiB"
echo " Temp file : ${TMPFILE}"
echo "================================================="

# Cleanup on exit
trap 'rm -f "${TMPFILE}"; echo "[io_test] Cleaned up temp file."' EXIT

# ---------------------------------------------------------------------------
# Phase 1: Sequential write
# ---------------------------------------------------------------------------
echo "[io_test] Phase 1/3 – Sequential write (dd)..."
T0="$(date +%s%N)"
dd if=/dev/urandom of="${TMPFILE}" bs=1M count="${FILE_SIZE_MB}" \
    conv=fsync status=none
T1="$(date +%s%N)"
WRITE_MS=$(( (T1 - T0) / 1000000 ))
echo "[io_test]  Write: ${FILE_SIZE_MB} MiB in ${WRITE_MS} ms"

# ---------------------------------------------------------------------------
# Phase 2: Sequential read
# ---------------------------------------------------------------------------
echo "[io_test] Phase 2/3 – Sequential read (dd)..."
T0="$(date +%s%N)"
dd if="${TMPFILE}" of=/dev/null bs=1M status=none
T1="$(date +%s%N)"
READ_MS=$(( (T1 - T0) / 1000000 ))
echo "[io_test]  Read : ${FILE_SIZE_MB} MiB in ${READ_MS} ms"

# ---------------------------------------------------------------------------
# Phase 3: Sustained random-read loop for DURATION seconds
# ---------------------------------------------------------------------------
echo "[io_test] Phase 3/3 – Random read loop for ${DURATION}s..."
END_TIME=$(( $(date +%s) + DURATION ))
LOOP_COUNT=0

while [[ $(date +%s) -lt ${END_TIME} ]]; do
    # Read a random 4 KiB block (simulate many small random reads)
    OFFSET=$(( RANDOM % (FILE_SIZE_MB * 256) ))   # 256 × 4 KiB = 1 MiB blocks
    dd if="${TMPFILE}" of=/dev/null bs=4K count=1 \
       skip="${OFFSET}" status=none 2>/dev/null || true
    LOOP_COUNT=$(( LOOP_COUNT + 1 ))
done

echo "[io_test]  Random reads: ${LOOP_COUNT} iterations in ${DURATION}s"

echo "================================================="
echo " I/O Benchmark complete."
echo " Write : ${WRITE_MS} ms for ${FILE_SIZE_MB} MiB"
echo " Read  : ${READ_MS} ms for ${FILE_SIZE_MB} MiB"
echo "================================================="
