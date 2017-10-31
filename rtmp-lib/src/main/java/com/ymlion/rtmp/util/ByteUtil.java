package com.ymlion.rtmp.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
     * big-endian
     *
     * @param d source num
     * @return dst bytes
     */
    public static byte[] double2Bytes(double d) {
        long value = Double.doubleToRawLongBits(d);
        byte[] byteRet = new byte[8];
        for (int i = 0; i < 8; i++) {
            //byteRet[i] = (byte) ((value >> 8 * i) & 0xff);
            byteRet[i] = (byte) ((value >> 8 * (7 - i)) & 0xff);
        }
        return byteRet;
    }

    public static byte[] double2Bytes(double d, boolean bigEndian) {
        byte[] dst = new byte[8];
        ByteBuffer.wrap(dst)
                .order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN)
                .putDouble(d);

        return dst;
    }

    /**
     * big endian
     */
    public static double bytes2Double(byte[] arr, int off) {
        long value = 0;
        for (int i = off; i < 8 + off; i++) {
            //value |= ((long) (arr[i] & 0xff)) << (8 * (i - off));
            value |= ((long) (arr[i] & 0xff)) << (8 * (7 - i + off));
        }
        return Double.longBitsToDouble(value);
    }

    public static double bytes2Double(byte[] arr, int off, boolean bigEndian) {
        return ByteBuffer.wrap(arr, off, 8)
                .order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN)
                .getDouble();
    }

    public static void writeString(String s, byte[] dst, int off) {
        for (int i = 0; i < s.length(); i++) {
            dst[i + off] = (byte) s.charAt(i);
        }
    }

    /*public static void main(String[] args) {
        byte[] d = new byte[] {0x40, 0x0, 0, 0 ,0,0,0,0};
        System.out.println(bytes2Double(d, 0));
        System.out.println(bytes2Double(d, 0, true));
        //System.out.println(bytes2Double(double2Bytes(8.0), 0));
        //System.out.println(bytes2Double(double2Bytes(99.0, false), 0, true));
        //System.out.println(bytes2Double(double2Bytes(121.518), 0));

        byte[] b1 = double2Bytes(3.0);
        byte[] b2 = double2Bytes(4.0, false);
        byte[] b3 = double2Bytes(3.0, true);
        for (byte b : b1) {
            System.out.print(b + " ");
        }
        System.out.println();
        for (byte b : b2) {
            System.out.print(b + " ");
        }
        System.out.println();
        for (byte b : b3) {
            System.out.print(b + " ");
        }
        System.out.println();
    }*/

}
