package com.ymlion.rtmp.util;

/**
 * byte tools class
 * <p>
 * Created by YMlion on 2017/10/13.
 */

public class ByteUtil {
    /**
     * write source to bytes
     *
     * @param size source byte size
     * @param s source
     * @param dst bytes to write
     * @param off offset to write
     */
    public static void writeInt(int size, int s, byte[] dst, int off) {
        for (int i = 0; i < size; i++) {
            dst[i + off] = (byte) (s >>> ((size - 1 - i) * 8));
        }
    }

    public static int bytes2Int(int size, byte[] src, int off) {
        switch (size) {
            case 1:
                return src[off] & 0xff;
            case 2:
                return ((src[off] & 0xff) << 8) | (src[off + 1] & 0xff);
            case 3:
                return ((src[off] & 0xff) << 16) | ((src[off + 1] & 0xff) << 8) | (src[off + 2]
                        & 0xff);
            case 4:
                return ((src[off] & 0xff) << 24) | ((src[off + 1] & 0xff) << 16) | ((src[off + 2]
                        & 0xff) << 8) | (src[off + 3] & 0xff);
        }
        return 0;
    }

    /**
     * little-endian
     *
     * @param d source num
     * @return dst bytes
     */
    public static byte[] double2Bytes(double d) {
        long value = Double.doubleToRawLongBits(d);
        byte[] byteRet = new byte[8];
        for (int i = 0; i < 8; i++) {
            //byteRet[i] = (byte) ((value >> 8 * i) & 0xff);
            byteRet[i] = (byte) ((value >> 8 * (8 - 1 - i)) & 0xff);
        }
        return byteRet;
    }

    public static void writeString(String s, byte[] dst, int off) {
        for (int i = 0; i < s.length(); i++) {
            dst[i + off] = (byte) s.charAt(i);
        }
    }
}
