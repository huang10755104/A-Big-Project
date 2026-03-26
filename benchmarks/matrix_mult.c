/*
 * matrix_mult.c – CPU-bound benchmark for OS telemetry analysis (Task 4)
 *
 * Performs a naive N×N matrix multiplication in a tight loop.  Designed to
 * generate sustained CPU load so that context-switch and page-fault
 * behaviour visible via sys_get_proc_info can be studied.
 *
 * Usage:
 *   gcc -O0 -o matrix_mult matrix_mult.c
 *   ./matrix_mult [N] [ITERATIONS]
 *
 *   N          – matrix dimension (default: 256)
 *   ITERATIONS – number of multiply rounds (default: 10)
 *
 * The program prints elapsed wall-clock time and the final checksum to
 * prevent the compiler from optimising away the work.
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define DEFAULT_N    256
#define DEFAULT_ITER 10

/* Allocate an N×N double matrix (row-major) */
static double *alloc_matrix(int n)
{
    double *m = malloc((size_t)n * n * sizeof(double));
    if (!m) {
        perror("malloc");
        exit(EXIT_FAILURE);
    }
    return m;
}

/* Fill matrix with deterministic pseudo-random values */
static void init_matrix(double *m, int n, double seed)
{
    for (int i = 0; i < n * n; i++)
        m[i] = seed * (i + 1) * 0.001;
}

/* C = A × B  (naive O(n³), no BLAS) */
static void multiply(const double *A, const double *B, double *C, int n)
{
    memset(C, 0, (size_t)n * n * sizeof(double));
    for (int i = 0; i < n; i++)
        for (int k = 0; k < n; k++) {
            double aik = A[i * n + k];
            for (int j = 0; j < n; j++)
                C[i * n + j] += aik * B[k * n + j];
        }
}

/* Sum all elements to produce a checksum */
static double checksum(const double *m, int n)
{
    double s = 0.0;
    for (int i = 0; i < n * n; i++)
        s += m[i];
    return s;
}

int main(int argc, char *argv[])
{
    int n    = (argc > 1) ? atoi(argv[1]) : DEFAULT_N;
    int iter = (argc > 2) ? atoi(argv[2]) : DEFAULT_ITER;

    if (n <= 0 || iter <= 0) {
        fprintf(stderr, "Usage: %s [N] [ITERATIONS]\n", argv[0]);
        return EXIT_FAILURE;
    }

    printf("Matrix multiply benchmark: N=%d, iterations=%d\n", n, iter);

    double *A = alloc_matrix(n);
    double *B = alloc_matrix(n);
    double *C = alloc_matrix(n);

    init_matrix(A, n, 1.0);
    init_matrix(B, n, 2.0);

    struct timespec t_start, t_end;
    clock_gettime(CLOCK_MONOTONIC, &t_start);

    for (int i = 0; i < iter; i++) {
        multiply(A, B, C, n);
        if (i % 2 == 0)
            printf("  iteration %d/%d …\n", i + 1, iter);
    }

    clock_gettime(CLOCK_MONOTONIC, &t_end);

    double elapsed = (t_end.tv_sec  - t_start.tv_sec) +
                     (t_end.tv_nsec - t_start.tv_nsec) * 1e-9;

    printf("Elapsed: %.3f s | Checksum: %.6e\n", elapsed, checksum(C, n));

    free(A);
    free(B);
    free(C);
    return EXIT_SUCCESS;
}
