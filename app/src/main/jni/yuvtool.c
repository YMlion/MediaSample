//
// Created by duoyi on 2017/9/18.
//
#include <jni.h>
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
Java_com_ymlion_mediasample_util_YuvUtil_yuvToRgb(JNIEnv *env, jclass type,
                                                  jbyteArray src, jint width, jint height,
                                                  jint dst_width, jint dst_height,
                                                  jint orientation, jint format,
                                                  jint scaleMode, jobject surface,
                                                  jboolean front) {
    jbyte *srcData = (*env)->GetByteArrayElements(env, src, NULL);

    int y_stride = width;
    int uv_stride = width;
    int u_stride = (width + 1) / 2;
    int v_stride = u_stride;
    size_t ySize = (size_t) (y_stride * height);
    size_t uSize = ySize >> 2;

    uint8 *I420Data = malloc(sizeof(u_char) * width * height * 3 / 2);

    NV12ToI420((const uint8 *) srcData, y_stride, (const uint8 *) (srcData + ySize),
               uv_stride,
               I420Data, y_stride,
               I420Data + ySize, u_stride, I420Data + ySize + uSize, v_stride, width, height);

    uint8 *dstData = malloc(sizeof(u_char) * dst_width * dst_height * 3 / 2);

    int dst_y_stride = dst_width;
    int dst_u_stride = (dst_width + 1) / 2;
    int dst_v_stride = dst_u_stride;
    size_t dst_ySize = (size_t) (dst_y_stride * dst_height);
    size_t dst_uSize = dst_ySize >> 2;
    I420Scale(I420Data, width, I420Data + ySize, u_stride,
              I420Data + ySize + uSize, v_stride, width, height, dstData,
              dst_y_stride, dstData + dst_ySize, dst_u_stride,
              dstData + dst_ySize + dst_uSize, dst_v_stride, dst_width, dst_height,
              (enum FilterMode) scaleMode);

    ANativeWindow *nativeWindow;
    ANativeWindow_Buffer windowBuffer;
    //获取界面传下来的surface
    nativeWindow = ANativeWindow_fromSurface(env, surface);
    if (0 == nativeWindow) {
        LOGD("Couldn't get native window from surface.\n");
        return;
    }
    if (0 >
        ANativeWindow_setBuffersGeometry(nativeWindow, dst_width, dst_height,
                                         WINDOW_FORMAT_RGBA_8888)) {
        LOGD("Couldn't set buffers geometry.\n");
        ANativeWindow_release(nativeWindow);
        return;
    }
    if (ANativeWindow_lock(nativeWindow, &windowBuffer, NULL) < 0) {
        LOGD("cannot lock window");
    } else {
        I420ToARGB(dstData, dst_y_stride, dstData + dst_ySize, dst_u_stride,
                   dstData + dst_ySize + dst_uSize, dst_v_stride, windowBuffer.bits,
                   windowBuffer.stride * 4,
                   dst_width, dst_height);
        ANativeWindow_unlockAndPost(nativeWindow);
    }

    (*env)->ReleaseByteArrayElements(env, src, srcData, 0);

    free(I420Data);
    free(dstData);
}

JNIEXPORT void JNICALL
Java_com_ymlion_mediasample_util_YuvUtil_convertToARGB(JNIEnv *env, jclass type,
                                                       jbyteArray yuvData_, jint width, jint height,
                                                       jint dstWidth, jint dstHeight,
                                                       jint orientation, jint format,
                                                       jint scaleMode, jobject surface,
                                                       jboolean front) {
    uint8_t *yuvData = (uint8_t *) (*env)->GetByteArrayElements(env, yuvData_, NULL);

    int rgba_stride = width * 4;
    int y_stride = width;
    size_t ySize = (size_t) (y_stride * height);
    char *rgbData = malloc(sizeof(char) * width * height * 4);

    // yuv convert to argb，只能是NV12，不清楚原因
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
            ConvertToARGB(yuvData, ySize * 3 / 2, (uint8 *) rgbData, rgba_stride, 0, 0, width,
                          height, width,
                          height, (enum RotationMode) 0, FOURCC('N', 'V', '1', '2'));
    }

    char *rotateData;

    // rotate argb
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

    // scale and render, 直接将scale之后的渲染到surface上
    int w = width;
    int h = height;
    if (orientation == 90 || orientation == 270) {
        h = width;
        w = height;
    }
    int src_stride = 4 * w;

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
        uint8_t *dst;
        int stride = windowBuffer.stride * 4;
        if (front) {// 前置需要翻转镜像
            dst = (uint8 *) windowBuffer.bits + (stride * dstHeight);
            stride = -stride;
        } else {
            dst = (uint8 *) windowBuffer.bits;
        }

        ARGBScale((uint8 *) rotateData, src_stride, w, h, dst, stride, dstWidth,
                  dstHeight, (enum FilterMode) scaleMode);
        ANativeWindow_unlockAndPost(nativeWindow);
    }

    // release
    (*env)->ReleaseByteArrayElements(env, yuvData_, (jbyte *) yuvData, 0);
    free(rgbData);
    free(rotateData);
}