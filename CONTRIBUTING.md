# Contributing Guide – OS-JavaFX-GenAI Integrated Telemetry System

Thank you for contributing! Please read this guide before opening a pull request.

---

## Branching Strategy

```
main
└── develop
    ├── feat/kernel-<description>   (Hwang, Gyeong-Ryun – 114502558)
    ├── feat/ui-<description>       (JavaFX UI / CSS contributors)
    └── docs/genai                  (AI interaction logs for Prof. Chang)
```

### Branch Descriptions

| Branch | Owner | Purpose |
|--------|-------|---------|
| `main` | All | **Production-only**. Merge here only for demo-ready releases. No direct commits. |
| `develop` | All | Main integration branch. All feature branches target `develop`. |
| `feat/kernel-*` | Hwang, Gyeong-Ryun (114502558) | Kernel tasks: custom syscall, QEMU, benchmarks (Tasks 1–4). |
| `feat/ui-*` | UI contributors | JavaFX dashboard, FXML layouts, CSS themes. |
| `docs/genai` | All | Syncs AI prompt logs required by Prof. Chang's GenAI course. |

---

## Workflow

1. **Fork or clone** the repository.
2. **Create a branch** from `develop` (never from `main`):
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feat/kernel-proc-info   # example
   ```
3. **Make your changes** in small, focused commits:
   ```bash
   git add <files>
   git commit -m "feat(kernel): add sys_get_proc_info syscall"
   ```
4. **Push** your branch and open a Pull Request targeting `develop`.
5. **Request a review** from at least one teammate before merging.

---

## Commit Message Convention

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short description>

[optional body]
[optional footer]
```

| Type | Use for |
|------|---------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `chore` | Build / tooling changes |
| `bench` | Benchmark additions or changes |
| `refactor` | Code refactor (no behaviour change) |

**Examples:**
```
feat(kernel): implement sys_get_proc_info for nvcsw/nivcsw
fix(dashboard): handle SSH timeout in TelemetryService
docs(genai): add prompt log for syscall session 2024-05-10
chore(scripts): add uname -m check to build_kernel.sh
```

---

## Coding Standards

### C (Kernel)
- Follow [Linux kernel coding style](https://www.kernel.org/doc/html/latest/process/coding-style.html).
- Use `checkpatch.pl` before committing:
  ```bash
  scripts/checkpatch.pl --no-tree -f kernel/syscalls/sys_get_proc_info.c
  ```

### Java (Dashboard)
- Java 17 language level.
- Format with `google-java-format` or match existing indentation (4 spaces).
- Javadoc all public classes and methods.

### Shell Scripts
- `set -euo pipefail` at the top of every script.
- Check for required commands with `command -v`.
- Handle ARM64 vs x86_64 via `uname -m`.

---

## GenAI Logging Requirements (Prof. Chang)

Every AI-assisted coding session **must** be logged:

1. Copy `docs/prompt_logs/TEMPLATE.md` and rename it:
   ```
   docs/prompt_logs/YYYY-MM-DD_<topic>.md
   ```
2. Fill in all sections (objective, prompts, responses, reflection).
3. Commit to the `docs/genai` branch and push.
4. Open a PR targeting `develop` when the log is complete.

---

## Pull Request Checklist

Before opening a PR, confirm:

- [ ] Branch targets `develop` (not `main`)
- [ ] Commit messages follow the convention above
- [ ] Code compiles without warnings
- [ ] Any new shell scripts are executable (`chmod +x`)
- [ ] GenAI prompt log added (if AI was used)
- [ ] `docs/reports/TEMPLATE.md` updated if deliverable scope changed
