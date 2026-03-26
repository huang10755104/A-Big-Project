# OS-JavaFX-GenAI Integrated Telemetry System

> A monorepo that captures kernel-level process telemetry via a custom Linux
> system call and visualises it in real-time with a JavaFX dashboard.

| Component | Technology |
|-----------|-----------|
| **Kernel** | Linux 6.x (x86_64), custom `sys_get_proc_info` syscall |
| **Emulator** | QEMU (x86_64 guest on ARM64 / x86_64 host) |
| **Dashboard** | JavaFX 21 + JSch SSH client |
| **Host (dev)** | Apple Silicon M4 Pro (ARM64) |
| **AI tooling** | GitHub Copilot / ChatGPT (prompt logs in `docs/prompt_logs/`) |

---

## Repository Structure

```
A-Big-Project/
├── kernel/
│   ├── patches/          # Kernel diff/patch files
│   ├── syscalls/
│   │   └── sys_get_proc_info.c   # Custom syscall (nvcsw, nivcsw, page faults)
│   └── Makefile          # Cross-compile: ARM64 host → x86_64 target
├── dashboard/
│   ├── pom.xml           # JavaFX + JSch Maven dependencies
│   └── src/main/
│       ├── java/com/osproject/dashboard/
│       │   ├── DashboardApp.java       # JavaFX entry point
│       │   ├── DashboardController.java # LineChart + UI logic
│       │   ├── TelemetryService.java   # SSH polling (1 s interval)
│       │   └── ProcInfo.java           # Data record
│       └── resources/com/osproject/dashboard/
│           └── Dashboard.fxml          # UI layout
├── scripts/
│   ├── build_kernel.sh   # Cross-compile kernel (-j12 for M4 Pro)
│   └── run_qemu.sh       # Boot QEMU with SSH hostfwd tcp::2222->:22
├── benchmarks/
│   ├── matrix_mult.c     # CPU-bound benchmark (Task 4)
│   └── io_test.sh        # I/O-bound benchmark (Task 4)
├── docs/
│   ├── prompt_logs/      # GenAI interaction logs (Prof. Chang)
│   └── reports/          # PDF-ready OS project report templates
├── .gitignore
├── CONTRIBUTING.md
└── README.md
```

---

## Quick Start

### Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| JDK  | 17+     | `brew install openjdk@17` (macOS) or `sudo apt install openjdk-17-jdk` |
| Maven | 3.9+   | `brew install maven` |
| QEMU | 8+      | `brew install qemu` |
| x86_64 cross-compiler | – | `brew install x86_64-elf-gcc` (macOS) or `sudo apt install gcc-x86-64-linux-gnu` |
| Linux kernel source | 6.x | `git clone --depth=1 https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git` |

---

## Windows/WSL2 Setup Guide

1) Install WSL2 (Ubuntu) from the Microsoft Store and update the toolchain:
```bash
sudo apt update
sudo apt install gcc-x86-64-linux-gnu build-essential flex bison libncurses-dev libssl-dev libelf-dev bc
```
`build_kernel.sh` will detect WSL2 automatically. If `/lib/modules/$(uname -r)/build` is missing, the kernel Makefile falls back to `~/linux/build-x86_64` (the default `KERNEL_SRC` build dir).

2) Build and boot the kernel from WSL2:
```bash
export KERNEL_SRC=~/linux
./scripts/build_kernel.sh
./scripts/run_qemu.sh    # uses TCG in WSL2 (no KVM/WHPX inside WSL)
```
SSH stays on `localhost:2222`; allow this port through the Windows firewall if prompted.

3) Run the JavaFX dashboard natively on Windows (outside WSL):
```powershell
winget install EclipseAdoptium.Temurin.17.JDK Maven.Maven
cd dashboard
mvn clean javafx:run
```
The JavaFX Maven plugin now auto-detects `windows-x86_64` and downloads the right natives. Use host `127.0.0.1` / port `2222` to connect to QEMU.

---

### 1. Build the Kernel

```bash
# Set the path to your cloned kernel source
export KERNEL_SRC=~/linux

./scripts/build_kernel.sh
# Produces: ~/linux/build-x86_64/arch/x86/boot/bzImage
```

The script auto-detects the host architecture (`uname -m`) and sets
`CROSS_COMPILE=x86_64-linux-gnu-` automatically on ARM64 / Apple Silicon hosts.

---

### 2. Boot in QEMU

```bash
./scripts/run_qemu.sh
# QEMU guest is reachable at: ssh -p 2222 root@localhost
```

---

### 3. Run the JavaFX Dashboard

```bash
cd dashboard
mvn javafx:run
```

In the dashboard UI:
1. Set **Host** = `localhost`, **Port** = `2222`.
2. Enter the SSH **User** / **Password** for the QEMU guest.
3. Enter the **PID** you want to monitor.
4. Click **Connect** – the LineChart updates every second.

---

### 4. Run Benchmarks (inside the QEMU guest)

```bash
# CPU-bound
gcc -O0 -o benchmarks/matrix_mult benchmarks/matrix_mult.c
./benchmarks/matrix_mult 512 20

# I/O-bound
bash benchmarks/io_test.sh 30 128
```

---

## Branching Strategy

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full contribution guide.

| Branch | Purpose |
|--------|---------|
| `main` | Production-only, demo-ready code |
| `develop` | Main integration branch |
| `feat/kernel-*` | Kernel tasks (Hwang, Gyeong-Ryun – 114502558) |
| `feat/ui-*` | JavaFX UI & CSS work |
| `docs/genai` | AI interaction logs for Prof. Chang |

---

## License

This project is for academic purposes (Operating Systems course).
