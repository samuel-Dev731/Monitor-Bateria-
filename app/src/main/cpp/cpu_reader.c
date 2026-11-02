#include "cpu_reader.h"
#include <stdio.h>
#include <string.h>

int read_cpu_counters(CpuCounters *out, size_t capacity, size_t *count) {
    if (!out || !count || capacity == 0) return 0;
    FILE *file = fopen("/proc/stat", "r");
    if (!file) return 0;
    char line[512];
    size_t used = 0;
    while (fgets(line, sizeof(line), file) && used < capacity) {
        if (strncmp(line, "cpu", 3) != 0) break;
        if (line[3] != ' ' && (line[3] < '0' || line[3] > '9')) break;
        unsigned long long v[10] = {0};
        int n = sscanf(line + 3, "%llu %llu %llu %llu %llu %llu %llu %llu %llu %llu",
                       &v[0], &v[1], &v[2], &v[3], &v[4], &v[5], &v[6], &v[7], &v[8], &v[9]);
        if (n < 4) continue;
        unsigned long long total = 0;
        for (int i = 0; i < n; ++i) total += v[i];
        out[used].total = total;
        out[used].idle = v[3] + (n > 4 ? v[4] : 0);
        ++used;
    }
    fclose(file);
    *count = used;
    return used > 0;
}
