package com.ymlion.rtmp.bean;

import java.io.IOException;
import java.io.OutputStream;

/**
 * root data class
 * Created by YMlion on 2017/10/13.
 */

public abstract class RObject {
    int byteSize;
    protected byte type;

    public RObject(int size, byte type) {
        byteSize = size;
        this.type = type;
    }

    public abstract byte[] getBytes();

    public void write(OutputStream out) throws IOException {
        out.write(getBytes());
    }

    public int getSize() {
        return byteSize;
    }

    public abstract Object value();
}
