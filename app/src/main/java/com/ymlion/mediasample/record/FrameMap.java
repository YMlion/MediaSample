package com.ymlion.mediasample.record;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by YMlion on 2017/10/25.
 */

public class FrameMap {
    private List<byte[]> frames;
    private List<Long> times;

    public FrameMap() {
        frames = new ArrayList<>();
        times = new ArrayList<>();
    }

    public void put(byte[] frame, long time) {
        frames.add(frame);
        times.add(time);
    }

    public byte[] getFrame() {
        return frames.remove(0);
    }

    public long getTime() {
        return times.remove(0);
    }

    public boolean isEmpty() {
        return frames.isEmpty();
    }
}
