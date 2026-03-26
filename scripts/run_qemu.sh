#!/usr/bin/env bash
# =============================================================================
# run_qemu.sh – Boot the custom kernel in QEMU (x86_64) with SSH port-forward.
#
# SSH access:  ssh -p 2222 root@localhost
# Dashboard:   Configure the JavaFX app to connect to localhost:2222
#
# Usage:
#   ./scripts/run_qemu.sh [BZIMAGE] [ROOTFS]
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration – override via environment variables
# ---------------------------------------------------------------------------
KERNEL_SRC="${KERNEL_SRC:-$HOME/linux}"
BUILD_DIR="${BUILD_DIR:-${KERNEL_SRC}/build-x86_64}"
BZIMAGE="${1:-${BZIMAGE:-${BUILD_DIR}/arch/x86/boot/bzImage}}"
ROOTFS="${2:-${ROOTFS:-${HOME}/rootfs.ext4}}"

# QEMU settings
QEMU_MEM="${QEMU_MEM:-2G}"
QEMU_CPUS="${QEMU_CPUS:-2}"
SSH_HOST_PORT="${SSH_HOST_PORT:-2222}"
SSH_GUEST_PORT="${SSH_GUEST_PORT:-22}"

# ---------------------------------------------------------------------------
# Host architecture check (Apple Silicon compatibility)
# ---------------------------------------------------------------------------
HOST_ARCH="$(uname -m)"
echo "[qemu] Host architecture: ${HOST_ARCH}"

# On macOS ARM64 QEMU runs x86_64 guests via TCG emulation.
# Warn if hvf is unavailable (it isn't for cross-arch emulation).
if [[ "${HOST_ARCH}" == "arm64" || "${HOST_ARCH}" == "aarch64" ]]; then
    echo "[qemu] WARNING: Running x86_64 guest on ARM64 host via TCG (software emulation)."
    echo "[qemu]          Expect ~3-5× slowdown compared to native execution."
    ACCEL_OPTION="-accel tcg,tb-size=1024"
else
    # x86_64 host: use KVM for near-native speed
    if [[ -e /dev/kvm ]]; then
        echo "[qemu] KVM detected – enabling hardware acceleration."
        ACCEL_OPTION="-accel kvm"
    else
        echo "[qemu] KVM not available – falling back to TCG."
        ACCEL_OPTION="-accel tcg"
    fi
fi

# ---------------------------------------------------------------------------
# Sanity checks
# ---------------------------------------------------------------------------
if ! command -v qemu-system-x86_64 &>/dev/null; then
    echo "[ERROR] qemu-system-x86_64 not found."
    echo "  macOS:  brew install qemu"
    echo "  Linux:  sudo apt install qemu-system-x86"
    exit 1
fi

if [[ ! -f "${BZIMAGE}" ]]; then
    echo "[ERROR] Kernel image not found: ${BZIMAGE}"
    echo "  Run ./scripts/build_kernel.sh first."
    exit 1
fi

if [[ ! -f "${ROOTFS}" ]]; then
    echo "[WARN] Root filesystem not found: ${ROOTFS}"
    echo "  Attempting to create a minimal initramfs on the fly..."
    ROOTFS=""   # handled below
fi

# ---------------------------------------------------------------------------
# Boot the kernel
# ---------------------------------------------------------------------------
echo "[qemu] Starting QEMU..."
echo "[qemu]   SSH: ssh -p ${SSH_HOST_PORT} root@localhost"
echo "[qemu]   Memory: ${QEMU_MEM}   CPUs: ${QEMU_CPUS}"
echo "[qemu]   Kernel: ${BZIMAGE}"

DISK_OPTION=""
if [[ -n "${ROOTFS}" ]]; then
    DISK_OPTION="-drive file=${ROOTFS},format=raw,if=virtio"
fi

qemu-system-x86_64 \
    ${ACCEL_OPTION} \
    -m "${QEMU_MEM}" \
    -smp "${QEMU_CPUS}" \
    -kernel "${BZIMAGE}" \
    -append "root=/dev/vda rw console=ttyS0 nokaslr" \
    ${DISK_OPTION} \
    -net nic,model=virtio \
    -net user,hostfwd=tcp::${SSH_HOST_PORT}-:${SSH_GUEST_PORT} \
    -nographic \
    -serial mon:stdio
