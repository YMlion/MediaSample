package com.ymlion.rtmp.bean;

import com.ymlion.rtmp.util.ByteUtil;
import java.util.ArrayList;
import java.util.List;

/**
 * command arguments
 * <p>
 * Created by YMlion on 2017/10/13.
 */
public class CommandObject {
    /**
     * object type, 1 byte
     */
    public byte type = 0x3;
    /**
     * command arguments map
     */
    public List<RObject> objects;
    /**
     * end of object marker, 3 bytes
     */
    public int endMarker = 0x9;

    private int byteSize = 4;

    public void put(String k, String v) {
        if (objects == null) {
            objects = new ArrayList<>();
        }
        RObject key = new RString(k, true);
        RObject value = new RString(v, false);
        objects.add(key);
        objects.add(value);
        byteSize += key.getSize() + value.getSize();
    }

    public void put(String k, boolean v) {
        if (objects == null) {
            objects = new ArrayList<>();
        }
        RObject key = new RString(k, true);
        RObject value = new RBoolean(v);
        objects.add(key);
        objects.add(value);
        byteSize += key.getSize() + value.getSize();
    }

    public void put(String k, double v) {
        if (objects == null) {
            objects = new ArrayList<>();
        }
        RObject key = new RString(k, true);
        RObject value = new RNumber(v);
        objects.add(key);
        objects.add(value);
        byteSize += key.getSize() + value.getSize();
    }

    public byte[] getBytes() {
        byte[] data = new byte[byteSize];
        data[0] = type;
        int p = 1;
        for (RObject object : objects) {
            System.arraycopy(object.getBytes(), 0, data, p, object.byteSize);
            p += object.getSize();
        }
        ByteUtil.writeInt(3, endMarker, data, p);
        return data;
    }
}
