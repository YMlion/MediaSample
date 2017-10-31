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

    private boolean header;

    /**
     * create a instance
     *
     * @param type true : video; false : audio
     * @param data original frame data
     * @param time timestamp
     * @param isHeader true is sps(pps) data;  false is original frame data
     */
    public Frame(boolean type, byte[] data, long time, boolean isHeader) {
        this.time = time;
        this.type = type;
        this.data = data;
        this.header = isHeader;
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

    public boolean isHeader() {
        return header;
    }
}
