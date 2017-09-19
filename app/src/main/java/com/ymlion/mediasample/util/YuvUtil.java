package com.ymlion.mediasample.util;

/**
 * Created by YMlion on 2017/9/18.
 */

public class YuvUtil {
    public static native void convertToRgba(byte[] yuvBytes, int width, int height, byte[] argb,
            int mode);

    public static native void scaleNV21(byte[] src, int width, int height, byte[] dst, int dstWidth,
            int dstHeight, int mode);

    public static native void scaleARGB(byte[] src, int width, int height, byte[] dst, int dstWidth,
            int dstHeight, int mode);

    public static native void rotateARGB(byte[] src, byte[] dst, int width, int height, int mode);
}
