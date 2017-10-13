package com.ymlion.rtmp.bean;

import static com.ymlion.rtmp.util.ByteUtil.writeInt;

/**
 * Created by YMlion on 2017/10/13.
 */

public class RString extends RObject {
    /**
     * length, 2 bytes
     */
    private int length;

    /**
     * string
     */
    private String string;

    private boolean isKey;

    public RString(String s, boolean isKey) {
        super(0, (byte) 2);
        this.string = s;
        length = s.length();
        this.isKey = isKey;
        byteSize = 2 + length;
        if (!isKey) {
            byteSize++;
        }
    }

    public byte[] getBytes() {
        int size = isKey ? 2 : 3;
        byte[] data = new byte[size + length];
        if (!isKey) {
            data[0] = type;
        }
        writeInt(2, length, data, size - 2);
        for (int i = size; i < data.length; i++) {
            data[i] = (byte) string.charAt(i - size);
        }
        return data;
    }
}
