package com.ymlion.rtmp.bean;

/**
 * Created by YMlion on 2017/10/25.
 */

public class Frame {
    /**
     * true: video; false: audio
     */
    private boolean type;

    private byte[] data;

    private long time;

    public Frame(boolean type, byte[] data, long time) {
        this.time = time;
        this.type = type;
        this.data = data;
    }

    public long getTime() {
        return time;
    }

    public boolean isVideo() {
        return type;
    }

    public byte[] getData() {
        return data;
    }
}
