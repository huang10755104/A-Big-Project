# =============================================================================
# Kernel cross-compilation Makefile
#
# Host:   Apple Silicon M4 Pro (ARM64 / aarch64)
# Target: x86_64 Linux 6.x
#
# Prerequisites (install on macOS with Homebrew):
#   brew install x86_64-elf-gcc  OR  use a Docker/Lima x86_64 cross-toolchain
#   On Linux hosts: sudo apt install gcc-x86-64-linux-gnu
#
# Usage:
#   make            – build the custom syscall object file
#   make clean      – remove build artefacts
#   make help       – print this message
# =============================================================================

# ---------------------------------------------------------------------------
# Toolchain detection: respect an explicit CROSS_COMPILE env variable,
# otherwise auto-detect based on the build host architecture.
# ---------------------------------------------------------------------------
HOST_ARCH := $(shell uname -m)

ifeq ($(CROSS_COMPILE),)
    ifeq ($(HOST_ARCH),arm64)
        # Apple Silicon (M-series) – use the Linux cross-compiler
        CROSS_COMPILE := x86_64-linux-gnu-
    else ifeq ($(HOST_ARCH),aarch64)
        CROSS_COMPILE := x86_64-linux-gnu-
    else
        # Native x86_64 host – no prefix required
        CROSS_COMPILE :=
    endif
endif

CC      := $(CROSS_COMPILE)gcc
OBJDUMP := $(CROSS_COMPILE)objdump

# ---------------------------------------------------------------------------
# Kernel source tree – override with KDIR=/path/to/kernel on the command line
# ---------------------------------------------------------------------------
KDIR ?= /lib/modules/$(shell uname -r)/build

# ---------------------------------------------------------------------------
# Build flags
# ---------------------------------------------------------------------------
CFLAGS_MODULE := -Wall -Wextra -O2 -fno-pie

# Source / object lists
SYSCALL_SRCS := syscalls/sys_get_proc_info.c
SYSCALL_OBJS := $(SYSCALL_SRCS:.c=.o)

# ---------------------------------------------------------------------------
# Targets
# ---------------------------------------------------------------------------
.PHONY: all clean help

all: $(SYSCALL_OBJS)
	@echo "[kernel] Build complete. Objects: $(SYSCALL_OBJS)"
	@echo "[kernel] Host arch detected: $(HOST_ARCH)"
	@echo "[kernel] Cross-compiler prefix: '$(CROSS_COMPILE)'"

$(SYSCALL_OBJS): $(SYSCALL_SRCS)
	$(CC) $(CFLAGS_MODULE) -I$(KDIR)/include -c $< -o $@

clean:
	@echo "[kernel] Cleaning build artefacts..."
	rm -f $(SYSCALL_OBJS)

help:
	@echo "============================================================="
	@echo " Kernel cross-compilation Makefile"
	@echo " Host: $(HOST_ARCH)   CROSS_COMPILE: $(CROSS_COMPILE)"
	@echo "-------------------------------------------------------------"
	@echo " make            Build syscall objects"
	@echo " make KDIR=<path> Override kernel header path"
	@echo " make clean      Remove build artefacts"
	@echo " make help       Show this help"
	@echo "============================================================="
