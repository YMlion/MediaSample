package com.ymlion.rtmp.bean;

import com.ymlion.rtmp.util.ByteUtil;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.IllegalFormatFlagsException;
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

    protected int byteSize = 4;

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

    public int getByteSize() {
        return byteSize;
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

    public void write(OutputStream out) throws IOException {
        out.write(getBytes());
    }

    public static CommandObject read(byte[] chunkBody, int off) throws IOException {
        int type = chunkBody[off];
        if (type != 3) {
            throw new IllegalFormatFlagsException(
                    "this type " + type + "is not object!!! object type is 3");
        }
        off++;
        List<RObject> objects = new ArrayList<>();
        while (off < chunkBody.length - 3) {
            int l = ByteUtil.bytes2Int(2, chunkBody, off);
            off += 2;
            RObject key = new RString(new String(chunkBody, off, l, "UTF-8"), true);
            off += l;
            RObject value = null;
            int valueType = chunkBody[off];
            off++;
            switch (valueType) {
                case 0: // number
                    value = new RNumber(ByteUtil.bytes2Double(chunkBody, off, true));
                    off += 8;
                    break;
                case 1: // boolean
                    value = new RBoolean((chunkBody[off] & 0xff) == 1);
                    off++;
                    break;
                case 2: // string
                    int length = ByteUtil.bytes2Int(2, chunkBody, off);
                    off += 2;
                    value = new RString(new String(chunkBody, off, length, "UTF-8"), false);
                    off += length;
                    break;
                case 5: // null
                    value = new RNull();
                    break;
            }
            objects.add(key);
            objects.add(value);
            System.out.println(key.value() + " : " + value.value());
        }
        CommandObject commandObject = new CommandObject();
        commandObject.objects = objects;
        return commandObject;
    }
}
