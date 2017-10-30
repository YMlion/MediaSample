package com.ymlion.rtmp.bean;

/**
 * Created by YMlion on 2017/10/13.
 */

public class RNull extends RObject {
    public RNull() {
        super(1, (byte) 5);
    }

    @Override public byte[] getBytes() {
        return new byte[] { 5 };
    }

    @Override public Object value() {
        return null;
    }
}
