package com.samueldev.monitorbateriapro

/**
 * Native /proc/stat sampler. Index 0 is aggregate CPU; following entries are
 * individual logical CPUs. A null result means the kernel data is unavailable.
 */
internal object NativeCpuMonitor {
    init {
        System.loadLibrary("monitor_native")
    }

    external fun readCounters(): LongArray?
}
