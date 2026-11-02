#include <jni.h>
#include "cpu_reader.h"
#include <vector>
#include <dlfcn.h>

using CpuPercentFn = double (*)(unsigned long long, unsigned long long,
                                unsigned long long, unsigned long long);

static CpuPercentFn get_dotnet_cpu_calculator() {
    static void* handle = dlopen("libmonitorcore.so", RTLD_NOW | RTLD_LOCAL);
    static CpuPercentFn function = handle
        ? reinterpret_cast<CpuPercentFn>(dlsym(handle, "monitor_cpu_percent"))
        : nullptr;
    return function;
}

extern "C" JNIEXPORT jdouble JNICALL
Java_com_samueldev_monitorbateriapro_NativeCpuMonitor_calculateDotNetPercent(
    JNIEnv*, jobject, jlong previousTotal, jlong previousIdle,
    jlong currentTotal, jlong currentIdle) {
    CpuPercentFn calculate = get_dotnet_cpu_calculator();
    if (!calculate || previousTotal < 0 || previousIdle < 0 ||
        currentTotal < 0 || currentIdle < 0) return -1.0;
    return calculate(static_cast<unsigned long long>(previousTotal),
                     static_cast<unsigned long long>(previousIdle),
                     static_cast<unsigned long long>(currentTotal),
                     static_cast<unsigned long long>(currentIdle));
}

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
