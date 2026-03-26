// SPDX-License-Identifier: GPL-2.0
/*
 * sys_get_proc_info.c
 *
 * Custom Linux system call: sys_get_proc_info
 *
 * Retrieves per-process telemetry (voluntary/involuntary context switches and
 * page faults) for a given PID from the kernel's task_struct.
 *
 * Target kernel: Linux 6.x  (x86_64)
 * Cross-compiled on: Apple Silicon M4 Pro (ARM64) using x86_64-linux-gnu-gcc
 *
 * Usage (from user-space):
 *   struct proc_info info;
 *   long ret = syscall(__NR_get_proc_info, pid, &info);
 */

#include <linux/kernel.h>
#include <linux/syscalls.h>
#include <linux/sched.h>
#include <linux/pid.h>
#include <linux/uaccess.h>
#include <linux/mm.h>

/**
 * struct proc_info - telemetry snapshot exported to user-space
 * @pid:          Process ID that was queried
 * @nvcsw:        Voluntary context switches (task yielded the CPU)
 * @nivcsw:       Involuntary context switches (task was preempted)
 * @min_flt:      Minor page faults (page present, no disk I/O required)
 * @maj_flt:      Major page faults (page not in memory, disk I/O required)
 */
struct proc_info {
    pid_t  pid;
    unsigned long nvcsw;
    unsigned long nivcsw;
    unsigned long min_flt;
    unsigned long maj_flt;
};

/**
 * sys_get_proc_info - retrieve telemetry for a target PID
 * @pid:      PID of the process to inspect
 * @uinfo:    user-space pointer to a struct proc_info to fill
 *
 * Returns 0 on success, negative errno on failure.
 */
SYSCALL_DEFINE2(get_proc_info, pid_t, pid, struct proc_info __user *, uinfo)
{
    struct task_struct *task;
    struct proc_info kinfo;

    if (!uinfo)
        return -EINVAL;

    rcu_read_lock();
    task = find_task_by_vpid(pid);
    if (!task) {
        rcu_read_unlock();
        return -ESRCH;
    }
    get_task_struct(task);
    rcu_read_unlock();

    /* Populate kernel-side struct from task_struct fields */
    kinfo.pid    = task_pid_vnr(task);
    kinfo.nvcsw  = task->nvcsw;       /* voluntary context switches   */
    kinfo.nivcsw = task->nivcsw;      /* involuntary context switches */
    kinfo.min_flt = task->min_flt;    /* minor page faults            */
    kinfo.maj_flt = task->maj_flt;    /* major page faults            */

    put_task_struct(task);

    if (copy_to_user(uinfo, &kinfo, sizeof(kinfo)))
        return -EFAULT;

    return 0;
}
