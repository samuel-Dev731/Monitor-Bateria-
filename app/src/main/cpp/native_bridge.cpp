#include <jni.h>
#include "cpu_reader.h"
#include <vector>

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_samueldev_monitorbateriapro_NativeCpuMonitor_readCounters(JNIEnv *env, jobject) {
    CpuCounters counters[65] = {};
    size_t count = 0;
    if (!read_cpu_counters(counters, 65, &count) || count == 0) return nullptr;
    std::vector<jlong> values;
    values.reserve(count * 2);
    for (size_t i = 0; i < count; ++i) {
        values.push_back(static_cast<jlong>(counters[i].total));
        values.push_back(static_cast<jlong>(counters[i].idle));
    }
    jlongArray result = env->NewLongArray(static_cast<jsize>(values.size()));
    if (result) env->SetLongArrayRegion(result, 0, static_cast<jsize>(values.size()), values.data());
    return result;
}
