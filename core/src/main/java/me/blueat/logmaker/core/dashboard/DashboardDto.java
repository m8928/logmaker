package me.blueat.logmaker.core.dashboard;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardDto {
    private int maker;
    private int log;
    private int sender;
    private int plugin;
    private long eps;
    private long actualEps;
    private long bps;
    private long actualBps;
    private double cpu;
    private long memory;
    private long maxMemory;
    private int thread;
    private int scenario;
}
