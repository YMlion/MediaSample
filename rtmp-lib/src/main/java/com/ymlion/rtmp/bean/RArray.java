package com.ymlion.rtmp.bean;

import com.ymlion.rtmp.util.ByteUtil;

/**
 * Created by YMlion on 2017/10/17.
 */

public class RArray extends CommandObject {
    /**
     * array length, 4 bytes
     */
    private int length;

    public RArray() {
        byteSize = 8;
        type = 8;
    }

    @Override public byte[] getBytes() {
        byte[] data = new byte[byteSize];
        data[0] = type;
        ByteUtil.writeInt(4, objects.size() / 2, data, 1);
        int p = 5;
        for (RObject object : objects) {
            System.arraycopy(object.getBytes(), 0, data, p, object.byteSize);
            p += object.getSize();
        }
        ByteUtil.writeInt(3, endMarker, data, p);
        return data;
    }
}
