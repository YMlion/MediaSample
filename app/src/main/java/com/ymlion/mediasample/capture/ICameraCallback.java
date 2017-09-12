package com.ymlion.mediasample.capture;

/**
 * Created by Kongjie(9154) on 2017/4/6.
 */

public interface ICameraCallback {
    void onInitFinished(int previewWidth, int previewHeight);

    void onImageAvailable(byte[] data);
}
