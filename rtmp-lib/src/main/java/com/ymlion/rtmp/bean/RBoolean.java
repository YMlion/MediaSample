package com.ymlion.rtmp.bean;

/**
 * Created by YMlion on 2017/10/13.
 */

public class RBoolean extends RObject {

    private byte value;

    public RBoolean(boolean value) {
        super(2, (byte) 0x1);
        this.value = (byte) (value ? 0x1 : 0x0);
    }

    @Override public byte[] getBytes() {
        byte[] data = new byte[2];
        data[0] = type;
        data[1] = value;
        return data;
    }

    @Override public Boolean value() {
        return value == 1;
    }
}
