package org.eclipse.jifa.tda.model;

import org.eclipse.jifa.tda.enums.OSTreadState;

import lombok.Data;

@Data
public class ThreadDumpRow {

    private String name;

    private int tid;

    private OSTreadState state;

    private int stackDepth;

    private Frame[] frames;

    private double elapsedTime;

    private double cpuTime;

    private int lineNumberStart;

    private int lineNumberEnd;

    public ThreadDumpRow(String name, int id, OSTreadState osThreadState, int length, Frame[] frames, double elapsed, double cpu, int lineStart, int lineEnd) {
        this.name = name;
        this.tid = id;
        this.state = osThreadState;
        this.stackDepth = length;
        this.frames = frames;
        this.elapsedTime = elapsed;
        this.cpuTime = cpu;
        this.lineNumberStart = lineStart;
        this.lineNumberEnd = lineEnd;
    }
}
