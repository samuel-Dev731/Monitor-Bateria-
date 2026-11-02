#ifndef MONITOR_CPU_READER_H
#define MONITOR_CPU_READER_H
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct {
    unsigned long long total;
    unsigned long long idle;
} CpuCounters;
int read_cpu_counters(CpuCounters *out, size_t capacity, size_t *count);
#ifdef __cplusplus
}
#endif
#endif
