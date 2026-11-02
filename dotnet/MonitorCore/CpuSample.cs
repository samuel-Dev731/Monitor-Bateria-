namespace MonitorCore;

/// <summary>Immutable CPU sample expressed as cumulative kernel tick counters.</summary>
public sealed class CpuSample
{
    public CpuSample(ulong totalTicks, ulong idleTicks)
    {
        if (idleTicks > totalTicks) throw new ArgumentOutOfRangeException(nameof(idleTicks));
        TotalTicks = totalTicks;
        IdleTicks = idleTicks;
    }

    public ulong TotalTicks { get; }
    public ulong IdleTicks { get; }
}

/// <summary>Calculates CPU utilization from two cumulative kernel samples.</summary>
public static class CpuMath
{
    public static double? CalculatePercent(CpuSample previous, CpuSample current)
    {
        if (current.TotalTicks <= previous.TotalTicks || current.IdleTicks < previous.IdleTicks)
            return null;

        var total = current.TotalTicks - previous.TotalTicks;
        var idle = current.IdleTicks - previous.IdleTicks;
        if (idle > total) return null;
        return Math.Clamp((total - idle) * 100.0 / total, 0.0, 100.0);
    }
}
