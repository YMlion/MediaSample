package com.ymlion.rtmp.bean;

import com.ymlion.rtmp.util.ByteUtil;

/**
 * Created by YMlion on 2017/10/13.
 */

public class RNumber extends RObject {
    /**
     * double, 8 bytes
     */
    public double number = 1.0;

    public RNumber(double value) {
        super(9, (byte) 0);
        number = value;
    }

    public byte[] getBytes() {
        byte[] bytes = new byte[byteSize];
        bytes[0] = type;
        System.arraycopy(ByteUtil.double2Bytes(number), 0, bytes, 1, 8);

        return bytes;
    }
}
