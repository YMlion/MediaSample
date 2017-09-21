//
// Created by duoyi on 2017/9/18.
//
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string.h>
#include <stdlib.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include "include/libyuv.h"

#define TAG "jni-log-jni" // 这个是自定义的LOG的标识
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,TAG ,__VA_ARGS__) // 定义LOGD类型
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG ,__VA_ARGS__) // 定义LOGI类型
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,TAG ,__VA_ARGS__) // 定义LOGW类型
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG ,__VA_ARGS__) // 定义LOGE类型
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL,TAG ,__VA_ARGS__) // 定义LOGF类型

JNIEXPORT void JNICALL
Java_com_ymlion_mediasample_util_YuvUtil_convertToRgba(JNIEnv *env, jclass type,
                                                       jbyteArray yuvBytes_, jint width,
                                                       jint height, jbyteArray rgba_, jint mode) {

    LOGD("convert to argb");
    uint8 *yuvData = (uint8 *) (*env)->GetByteArrayElements(env, yuvBytes_, NULL);
    uint8 *rgbData = (uint8 *) (*env)->GetByteArrayElements(env, rgba_, NULL);

    int rgba_stride = width * 4;
    int y_stride = width;
    int uv_stride = (width + 1) / 2 * 2;
    size_t ySize = (size_t) (y_stride * height);

    switch (mode) {
        case 1:
        NV12ToARGB(yuvData, y_stride, yuvData + ySize, (width + 1) / 2 * 2,
                       rgbData, rgba_stride, width, height);
            break;
        case 2:
            NV21ToARGB(yuvData, y_stride, yuvData + ySize, (width + 1) / 2 * 2,
                       rgbData, rgba_stride, width, height);
            break;
        default:
            ConvertToARGB(yuvData, ySize * 3 / 2, rgbData, rgba_stride, 0, 0, width, height, width,
                          height, (enum RotationMode) 0, FOURCC('N', 'V', '1', '2'));
    }

    (*env)->ReleaseByteArrayElements(env, yuvBytes_, (jbyte *) yuvData, 0);
    (*env)->ReleaseByteArrayElements(env, rgba_, (jbyte *) rgbData, 0);
}

JNIEXPORT void JNICALL
Java_com_ymlion_mediasample_util_YuvUtil_scaleNV21(JNIEnv *env, jclass type, jbyteArray src,
                                                   jint width, jint height, jbyteArray dst,
                                                   jint dst_width, jint dst_height, jint mode) {
    jbyte *srcData = (*env)->GetByteArrayElements(env, src, NULL);
    jbyte *dstData = (*env)->GetByteArrayElements(env, dst, NULL);

    int y_stride = width;
    int u_stride = (width + 1) / 2;
    int v_stride = u_stride;
    size_t ySize = (size_t) (y_stride * height);
    size_t uSize = (size_t) (ySize >> 2);
    int dst_y_stride = dst_width;
    int dst_u_stride = (dst_width + 1) / 2;
    int dst_v_stride = dst_u_stride;
    size_t dst_ySize = (size_t) (dst_y_stride * dst_height);
    size_t dst_uSize = (size_t) (dst_ySize >> 2);
    I420Scale((uint8 *) srcData, width, (uint8 *) srcData + ySize, u_stride,
              (uint8 *) srcData + ySize + uSize, v_stride, width, height, (uint8 *) dstData,
              dst_y_stride, (uint8 *) dstData + dst_ySize, dst_u_stride,
              (uint8 *) dstData + dst_ySize + dst_uSize, dst_v_stride, dst_width, dst_height,
              (enum FilterMode) mode);

    (*env)->ReleaseByteArrayElements(env, src, srcData, 0);
    (*env)->ReleaseByteArrayElements(env, dst, dstData, 0);
}

JNIEXPORT void JNICALL
Java_com_ymlion_mediasample_util_YuvUtil_scaleARGB(JNIEnv *env, jclass type, jbyteArray src_,
                                                   jint width, jint height, jbyteArray dst_,
                                                   jint dstWidth, jint dstHeight, jint mode) {
    jbyte *src = (*env)->GetByteArrayElements(env, src_, NULL);
    jbyte *dst = (*env)->GetByteArrayElements(env, dst_, NULL);

    int src_stride = 4 * width;
    int dst_stride = 4 * dstWidth;
    // TODO

    ARGBScale((uint8 *) src, src_stride, width, height, (uint8 *) dst, dst_stride, dstWidth,
              dstHeight, (enum FilterMode) mode);

    (*env)->ReleaseByteArrayElements(env, src_, src, 0);
    (*env)->ReleaseByteArrayElements(env, dst_, dst, 0);
}

JNIEXPORT void JNICALL
Java_com_ymlion_mediasample_util_YuvUtil_rotateARGB(JNIEnv *env, jclass type, jbyteArray src_,
                                                    jbyteArray dst_, jint width, jint height,
                                                    jint mode) {
    jbyte *src = (*env)->GetByteArrayElements(env, src_, NULL);
    jbyte *dst = (*env)->GetByteArrayElements(env, dst_, NULL);

    int src_stride = width * 4;
    int dst_stride = height * 4;
    if (mode == 180) {
        src_stride = height * 4;
        dst_stride = width * 4;
    }

    ARGBRotate((const uint8 *) src, src_stride, (uint8 *) dst, dst_stride, width, height,
               (enum RotationMode) mode);

    (*env)->ReleaseByteArrayElements(env, src_, src, 0);
    (*env)->ReleaseByteArrayElements(env, dst_, dst, 0);
}

JNIEXPORT void JNICALL
Java_com_ymlion_mediasample_util_YuvUtil_convertToARGB(JNIEnv *env, jclass type,
                                                       jbyteArray yuvData_, jint width, jint height,
                                                       jint dstWidth, jint dstHeight,
                                                       jint orientation, jint format,
                                                       jint scaleMode, jobject surface) {
    uint8_t *yuvData = (uint8_t *) (*env)->GetByteArrayElements(env, yuvData_, NULL);

    int rgba_stride = width * 4;
    int y_stride = width;
    int uv_stride = (width + 1) / 2 * 2;
    size_t ySize = (size_t) (y_stride * height);
    char *rgbData = malloc(sizeof(char) * width * height * 4);

    switch (format) {
        case 1:
            NV12ToARGB(yuvData, y_stride, yuvData + ySize, (width + 1) / 2 * 2,
                       (uint8 *) rgbData, rgba_stride, width, height);
            break;
        case 2:
            NV21ToARGB(yuvData, y_stride, yuvData + ySize, (width + 1) / 2 * 2,
                       (uint8 *) rgbData, rgba_stride, width, height);
            break;
        default:
            ConvertToARGB(yuvData, ySize * 3 / 2, (uint8 *) rgbData, rgba_stride, 0, 0, width, height, width,
                          height, (enum RotationMode) 0, FOURCC('N', 'V', '1', '2'));
    }

    char *rotateData;

    if (orientation > 0) {
        rotateData = malloc(sizeof(char) * width * height * 4);
        int src_stride = width * 4;
        int dst_stride = height * 4;
        if (orientation == 180) {
            src_stride = height * 4;
            dst_stride = width * 4;
        }

        ARGBRotate((const uint8 *) rgbData, src_stride, (uint8 *) rotateData, dst_stride, width,
                   height, (enum RotationMode) orientation);
    } else {
        rotateData = rgbData;
    }

    int w = width;
    int h = height;
    if (orientation == 90 || orientation == 270) {
        h = width;
        w = height;
    }
    int src_stride = 4 * w;
    int dst_stride = 4 * dstWidth;

    ANativeWindow *nativeWindow;
    ANativeWindow_Buffer windowBuffer;
    //获取界面传下来的surface
    nativeWindow = ANativeWindow_fromSurface(env, surface);
    if (0 == nativeWindow) {
        LOGD("Couldn't get native window from surface.\n");
        return;
    }
    if (0 >
        ANativeWindow_setBuffersGeometry(nativeWindow, dstWidth, dstHeight,
                                         WINDOW_FORMAT_RGBA_8888)) {
        LOGD("Couldn't set buffers geometry.\n");
        ANativeWindow_release(nativeWindow);
        return;
    }
    if (ANativeWindow_lock(nativeWindow, &windowBuffer, NULL) < 0) {
        LOGD("cannot lock window");
    } else {
        uint8_t *dst = (uint8 *) windowBuffer.bits;
        ARGBScale((uint8 *) rotateData, src_stride, w, h, dst, windowBuffer.stride * 4, dstWidth,
                  dstHeight, (enum FilterMode) scaleMode);
        ANativeWindow_unlockAndPost(nativeWindow);
    }

    (*env)->ReleaseByteArrayElements(env, yuvData_, (jbyte *) yuvData, 0);
    free(rgbData);
    free(rotateData);
}

JNIEXPORT void JNICALL
Java_com_ymlion_mediasample_util_YuvUtil_fillBitmap(JNIEnv *env, jclass type, jobject dst,
                                                    jbyteArray src_, jint size) {
    jbyte *src = (*env)->GetByteArrayElements(env, src_, NULL);

    u_char *bitmap = NULL;
    AndroidBitmap_lockPixels(env, dst, (
            void **) &bitmap);
    memcpy(bitmap, src, (size_t) size);
    AndroidBitmap_unlockPixels(env, dst);

    (*env)->ReleaseByteArrayElements(env, src_, src, 0);
}