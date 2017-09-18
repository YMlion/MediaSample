//
// Created by duoyi on 2017/9/18.
//
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <string.h>
#include <stdlib.h>

#define TAG "jni-log-jni" // 这个是自定义的LOG的标识
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG,TAG ,__VA_ARGS__) // 定义LOGD类型
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,TAG ,__VA_ARGS__) // 定义LOGI类型
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN,TAG ,__VA_ARGS__) // 定义LOGW类型
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,TAG ,__VA_ARGS__) // 定义LOGE类型
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL,TAG ,__VA_ARGS__) // 定义LOGF类型

JNIEXPORT void JNICALL
Java_com_ymlion_mediasample_util_YuvUtil_convertToRgba(JNIEnv *env, jclass type,
                                                       jbyteArray yuvBytes_, jint width,
                                                       jint height, jbyteArray rgba_) {

    LOGD("convert to argb");
    uint8_t *yuvData = (uint8_t *) (*env)->GetByteArrayElements(env, yuvBytes_, NULL);
    uint8_t *rgbData = (uint8_t *) (*env)->GetByteArrayElements(env, rgba_, NULL);

    // TODO
    int rgba_stride = width * 4;
    int y_stride = width;
    int uv_stride = (width + 1) / 2 * 2;
    size_t ySize = (size_t) (y_stride * height);

//    I420ToRGBA((const uint8_t *) yuvData, y_stride, yuvData + ySize, u_stride,
//               yuvData + ySize + uSize, v_stride, rgbData, rgba_stride, width, height);

    NV12ToARGB(yuvData, y_stride, yuvData + ySize, (width + 1) / 2 * 2,
               rgbData, rgba_stride, width, height);

//    int NV12ToARGB(const uint8* src_y,
//                   int src_stride_y,
//                   const uint8* src_uv,
//                   int src_stride_uv,
//                   uint8* dst_argb,
//                   int dst_stride_argb,
//                   int width,
//                   int height);

    (*env)->ReleaseByteArrayElements(env, yuvBytes_, (jbyte *) yuvData, 0);
    (*env)->ReleaseByteArrayElements(env, rgba_, (jbyte *) rgbData, 0);
}

JNIEXPORT void JNICALL
Java_com_ymlion_mediasample_util_YuvUtil_scaleNV21(JNIEnv *env, jclass type, jbyteArray src,
                                                   jint width, jint height, jbyteArray dst,
                                                   jint dst_width, jint dst_height, int mode) {
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
    I420Scale((uint8_t *) srcData, width, (uint8_t *) srcData + ySize, u_stride,
              (uint8_t *) srcData + ySize + uSize, v_stride, width, height, (uint8_t *) dstData,
              dst_y_stride, (uint8_t *) dstData + dst_ySize, dst_u_stride,
              (uint8_t *) dstData + dst_ySize + dst_uSize, dst_v_stride, dst_width, dst_height,
              mode);


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

    ARGBScale((uint8_t *) src, src_stride, width, height, (uint8_t *) dst, dst_stride, dstWidth,
              dstHeight, mode);

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
    int dst_stride = width * 4;
    if (mode == 180) {
        dst_stride = height * 4;
        src_stride = width * 4;
    }

    ARGBRotate((uint8_t *) src, src_stride, (uint8_t *) dst, dst_stride, width, height, mode);

    (*env)->ReleaseByteArrayElements(env, src_, src, 0);
    (*env)->ReleaseByteArrayElements(env, dst_, dst, 0);
}