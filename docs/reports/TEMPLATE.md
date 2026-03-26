# OS Project Report Template
# Course: Operating Systems
# Project: OS-JavaFX-GenAI Integrated Telemetry System
#
# Instructions:
#   Replace every placeholder in angle brackets <…> with your own content.
#   Export to PDF before submission using your preferred Markdown-to-PDF tool
#   (e.g., Pandoc: pandoc report.md -o report.pdf --pdf-engine=xelatex).

---
title: "OS-JavaFX-GenAI Integrated Telemetry System – Project Report"
author: "<Your Name> (<Student ID>)"
date: "<YYYY-MM-DD>"
---

## 1. Executive Summary
(1–2 paragraphs summarising the project goal, your approach, and key results.)

## 2. System Architecture
(Describe the monorepo structure.  Include a diagram if possible.)

## 3. Kernel Modifications (Tasks 1–4)
### 3.1 Custom System Call – `sys_get_proc_info`
(Explain how you added the syscall to the kernel source tree and describe the
fields returned: nvcsw, nivcsw, min_flt, maj_flt.)

### 3.2 Cross-Compilation Setup
(Describe the toolchain, CROSS_COMPILE variable, and any issues encountered on
the Apple Silicon M4 Pro host.)

### 3.3 QEMU Emulation
(Explain the QEMU command line, hostfwd port mapping, and how the guest was
configured.)

## 4. JavaFX Dashboard
### 4.1 SSH Telemetry Collection
(Describe the `TelemetryService` polling loop, connection parameters, and error
handling.)

### 4.2 Real-Time Line Chart
(Explain the chart design decisions: delta vs. absolute values, data windowing,
update frequency.)

## 5. Performance Benchmarks
### 5.1 CPU-Bound (matrix_mult.c)
| N   | Iterations | Elapsed (s) | Checksum |
|-----|-----------|-------------|---------|
| 256 | 10        | <measured>  | <value> |

### 5.2 I/O-Bound (io_test.sh)
| Phase          | Size  | Time (ms) |
|---------------|-------|-----------|
| Sequential Write | 64 MiB | <measured> |
| Sequential Read  | 64 MiB | <measured> |
| Random Reads     | 30 s   | <count> iterations |

### 5.3 Context-Switch Analysis
(Include screenshots of the dashboard chart during benchmark execution and
discuss the observed nvcsw / nivcsw trends.)

## 6. GenAI Integration
(Summarise how Generative AI tools were used during development.  Reference the
prompt logs in docs/prompt_logs/.)

## 7. Challenges & Solutions
(List at least three technical challenges and how you resolved them.)

## 8. Conclusion
(Summarise what was achieved and what could be improved in future work.)

## References
- Linux Kernel Documentation: https://www.kernel.org/doc/html/latest/
- JavaFX Documentation: https://openjfx.io/
- JSch Library: https://github.com/mwiede/jsch
- QEMU Documentation: https://www.qemu.org/docs/master/
