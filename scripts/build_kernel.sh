#!/usr/bin/env bash
# =============================================================================
# build_kernel.sh – Cross-compile the Linux kernel from an ARM64 host (M4 Pro)
#                   for an x86_64 QEMU target.
#
# Usage:
#   ./scripts/build_kernel.sh [KERNEL_SRC_DIR]
#
# Prerequisites (macOS via Homebrew):
#   brew install x86_64-elf-gcc qemu
#   sudo apt install gcc-x86-64-linux-gnu   # on Linux
#
# The script auto-detects the host architecture and sets CROSS_COMPILE.
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration – override via environment variables if needed
# ---------------------------------------------------------------------------
KERNEL_SRC="${1:-${KERNEL_SRC:-$HOME/linux}}"
BUILD_DIR="${BUILD_DIR:-${KERNEL_SRC}/build-x86_64}"
BZIMAGE="${BUILD_DIR}/arch/x86/boot/bzImage"

# Auto-detect host architecture
HOST_ARCH="$(uname -m)"
echo "[build] Host architecture: ${HOST_ARCH}"

if [[ "${HOST_ARCH}" == "arm64" || "${HOST_ARCH}" == "aarch64" ]]; then
    # Apple Silicon / generic aarch64 – cross-compile for x86_64
    export ARCH=x86_64
    export CROSS_COMPILE=x86_64-linux-gnu-
    echo "[build] Cross-compilation mode: CROSS_COMPILE=${CROSS_COMPILE}"
else
    # Native x86_64 host
    export ARCH=x86_64
    export CROSS_COMPILE=""
    echo "[build] Native x86_64 build – no cross-compiler prefix needed."
fi

# Verify the compiler is available
CC="${CROSS_COMPILE}gcc"
if ! command -v "${CC}" &>/dev/null; then
    echo "[ERROR] Compiler '${CC}' not found."
    echo "  macOS:  brew install x86_64-elf-gcc"
    echo "  Linux:  sudo apt install gcc-x86-64-linux-gnu"
    exit 1
fi
echo "[build] Compiler: $(${CC} --version | head -1)"

# ---------------------------------------------------------------------------
# Kernel source sanity check
# ---------------------------------------------------------------------------
if [[ ! -d "${KERNEL_SRC}" ]]; then
    echo "[ERROR] Kernel source directory not found: ${KERNEL_SRC}"
    echo "  Download the kernel: https://www.kernel.org"
    echo "  Or clone: git clone --depth=1 https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git"
    exit 1
fi

# ---------------------------------------------------------------------------
# Build steps
# ---------------------------------------------------------------------------
echo "[build] Using kernel source: ${KERNEL_SRC}"
mkdir -p "${BUILD_DIR}"

# Step 1: Generate a minimal defconfig for QEMU x86_64
echo "[build] Step 1/3 – Generating defconfig..."
make -C "${KERNEL_SRC}" O="${BUILD_DIR}" \
     ARCH=${ARCH} CROSS_COMPILE=${CROSS_COMPILE} \
     x86_64_defconfig

# Enable virtio drivers required by QEMU (idempotent via scripts/config)
"${KERNEL_SRC}/scripts/config" --file "${BUILD_DIR}/.config" \
    --enable CONFIG_VIRTIO \
    --enable CONFIG_VIRTIO_PCI \
    --enable CONFIG_VIRTIO_NET \
    --enable CONFIG_VIRTIO_BLK

# Step 2: Compile – -j12 exploits all 12 cores of the M4 Pro
echo "[build] Step 2/3 – Compiling kernel with -j12..."
make -C "${KERNEL_SRC}" O="${BUILD_DIR}" \
     ARCH=${ARCH} CROSS_COMPILE=${CROSS_COMPILE} \
     -j12

# Step 3: Build our custom syscall object
echo "[build] Step 3/3 – Compiling custom syscall objects..."
make -C "$(dirname "$0")/../kernel" \
     ARCH=${ARCH} CROSS_COMPILE=${CROSS_COMPILE} \
     KDIR="${BUILD_DIR}"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
if [[ -f "${BZIMAGE}" ]]; then
    SIZE="$(du -sh "${BZIMAGE}" | cut -f1)"
    echo ""
    echo "============================================================="
    echo " Build successful!"
    echo "  bzImage : ${BZIMAGE} (${SIZE})"
    echo "  Use run_qemu.sh to boot the image."
    echo "============================================================="
else
    echo "[ERROR] bzImage not found at ${BZIMAGE}. Build may have failed."
    exit 1
fi
