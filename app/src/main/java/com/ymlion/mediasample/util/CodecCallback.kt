package com.ymlion.mediasample.util

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodec.Callback
import android.media.MediaCodec.CodecException
import android.media.MediaFormat
import android.util.Log

/**
 * Created by YMlion on 2017/9/7.
 */
open class CodecCallback : Callback() {
    override fun onOutputBufferAvailable(codec: MediaCodec?, index: Int, info: BufferInfo?) {
    }

    override fun onInputBufferAvailable(codec: MediaCodec?, index: Int) {
    }

    override fun onOutputFormatChanged(codec: MediaCodec?, format: MediaFormat?) {
    }

    override fun onError(codec: MediaCodec?, e: CodecException?) {
        Log.e("CodecCallback", "onError ${e?.message}")
    }
}