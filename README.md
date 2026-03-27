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

Clone the Linux kernel source before proceeding (required for all platforms):

```bash
git clone --depth=1 https://git.kernel.org/pub/scm/linux/kernel/git/torvalds/linux.git ~/linux
```

---

### Linux (Native x86_64)

#### 1. Install Prerequisites

```bash
sudo apt update
sudo apt install gcc-x86-64-linux-gnu qemu-system-x86 openjdk-17-jdk maven \
                 build-essential flex bison libncurses-dev libssl-dev libelf-dev bc
```

#### 2. Build the Kernel

```bash
export KERNEL_SRC=~/linux
./scripts/build_kernel.sh
# Produces: ~/linux/build-x86_64/arch/x86/boot/bzImage
```

`build_kernel.sh` detects the native x86_64 host and builds without a cross-compiler prefix.

#### 3. Boot in QEMU

```bash
./scripts/run_qemu.sh
# QEMU uses KVM for near-native speed when /dev/kvm is available.
# SSH: ssh -p 2222 root@localhost
```

#### 4. Run the JavaFX Dashboard

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

### Windows (WSL2 Hybrid Mode)

This project uses a hybrid workflow on Windows: the kernel is compiled and QEMU runs inside **WSL2 (Ubuntu)**, while the JavaFX Dashboard runs natively on **Windows**.

#### 1. Set Up WSL2 Toolchain

Open a WSL2 (Ubuntu) terminal and install the build dependencies:

```bash
sudo apt update
sudo apt install gcc-x86-64-linux-gnu qemu-system-x86 build-essential \
                 flex bison libncurses-dev libssl-dev libelf-dev bc
```

`build_kernel.sh` automatically detects the WSL2 environment. If `/lib/modules/$(uname -r)/build` is missing, the kernel Makefile falls back to `~/linux/build-x86_64`.

#### 2. Build and Boot the Kernel (inside WSL2)

```bash
export KERNEL_SRC=~/linux
./scripts/build_kernel.sh
./scripts/run_qemu.sh
# QEMU runs under TCG in WSL2 – hardware acceleration (KVM/WHPX) is unavailable inside WSL.
# SSH is forwarded to localhost:2222 on the Windows host.
```

Allow port **2222** through the Windows Firewall if prompted.

#### 3. Run the JavaFX Dashboard (natively on Windows)

Open a **Windows** PowerShell terminal (outside WSL) and install the Java runtime and build tool:

```powershell
winget install EclipseAdoptium.Temurin.17.JDK
winget install Maven.Maven
```

Then launch the dashboard:

```powershell
cd dashboard
mvn clean javafx:run
```

The JavaFX Maven plugin auto-detects `windows-x86_64` and downloads the correct native libraries. Connect to **Host** = `127.0.0.1`, **Port** = `2222` to reach the QEMU guest running in WSL2.

---

### macOS (Apple Silicon M4 Pro)

This configuration is optimised for the 12-core Apple Silicon M4 Pro. QEMU runs the x86_64 guest via **software TCG emulation** with several M4 Pro-specific tunings applied automatically by the scripts.

#### 1. Install Prerequisites

```bash
brew install x86_64-elf-gcc qemu openjdk@17 maven
```

#### 2. Build the Kernel

```bash
export KERNEL_SRC=~/linux
./scripts/build_kernel.sh
# Produces: ~/linux/build-x86_64/arch/x86/boot/bzImage
```

`build_kernel.sh` detects the `arm64` host, sets `CROSS_COMPILE=x86_64-linux-gnu-`, and compiles with **`-j12`** to saturate all 12 cores of the M4 Pro. Adjust the `-j` value in the script if your machine has a different core count.

#### 3. Boot in QEMU

```bash
./scripts/run_qemu.sh
# Runs with: -accel tcg,thread=multi,tb-size=2048
# tb-size=2048 enlarges the translation-block cache; thread=multi enables
# parallel vCPU execution across the M4 Pro's performance cores.
# SSH: ssh -p 2222 root@localhost
```

#### 4. Run the JavaFX Dashboard

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

### Run Benchmarks (inside the QEMU guest)

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
