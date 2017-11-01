package com.ymlion.mediasample.live

import android.app.Activity
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
import android.os.SystemClock
import android.util.ArrayMap
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceHolder.Callback
import com.ymlion.mediasample.R
import com.ymlion.rtmp.ChunkReader
import com.ymlion.rtmp.RtmpPlay
import com.ymlion.rtmp.bean.CommandObject
import com.ymlion.rtmp.bean.Frame
import com.ymlion.rtmp.util.ByteUtil
import kotlinx.android.synthetic.main.activity_live.sv_live
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class LiveActivity : Activity(), ChunkReader.ChunkReaderListener {

    private lateinit var player: RtmpPlay
    private var configureMap: ArrayMap<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live)
        sv_live.holder.addCallback(object : Callback {
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int,
                    height: Int) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                rtmpConnect()
            }
        })
    }

    private fun rtmpConnect() {
        thread {
            player = RtmpPlay("10.32.10.219", "test", "live")
//            player = RtmpPlay("23.106.136.168", "test", "live")
            player.setListener(this)
            player.connect()
        }
    }

    override fun onPlayStart(metaData: CommandObject) {
        val objects = metaData.objects
        configureMap = ArrayMap()
        for (i in 0 until objects.size step 2) {
            configureMap!!.put(objects[i].value().toString(), objects[i + 1].value().toString())
        }
        runOnUiThread {
            val lp = sv_live.layoutParams
            lp.width = configureMap!!.get("width")!!.toDouble().toInt()
            lp.height = configureMap!!.get("height")!!.toDouble().toInt()
            sv_live.layoutParams = lp
        }
    }

    override fun onPlayAudio(frame: Frame) {

    }

    override fun onPlayVideo(frame: Frame) {
        if (frame.isHeader) {
            if (!playEnd) {
                return
            }
            var l = ByteUtil.bytes2Int(2, frame.data, 0)
            sps = ByteArray(l + 4)
            System.arraycopy(frame.data, 2, sps, 4, l)
            l = ByteUtil.bytes2Int(2, frame.data, l + 3)
            pps = ByteArray(l + 4)
            for (i in 0..2) {
                sps[i] = 0
                pps[i] = 0
            }
            sps[3] = 1
            pps[3] = 1
            System.arraycopy(frame.data, sps.size + 1, pps, 4, l)
            Log.e("TAG", "get sps and pps")
            playVideo()
        } else {
            videoFrames.add(frame)
            /*if ((frame.data[4] and 0x1f) == 5.toByte()) {
                val firstFrame = ByteArray(sps.size + pps.size + frame.data.size)
                System.arraycopy(sps, 0, firstFrame, 0, sps.size)
                System.arraycopy(pps, 0, firstFrame, sps.size, pps.size)
                System.arraycopy(frame.data, 0, firstFrame, sps.size + pps.size, frame.data.size)
                videoFrames.add(Frame(true, firstFrame, frame.time, true))
                Log.e("TAG", "key frame")
            } else {
                val firstFrame = ByteArray(pps.size + frame.data.size)
                System.arraycopy(pps, 0, firstFrame, 0, pps.size)
                System.arraycopy(frame.data, 0, firstFrame, pps.size, frame.data.size)
                videoFrames.add(Frame(true, firstFrame, frame.time, true))
                Log.e("TAG", "P frame " + pps.size)
            }*/
            synchronized(waitObj, {
                waitObj.notify()
            })
        }
    }

    private lateinit var sps: ByteArray
    private lateinit var pps: ByteArray

    private fun playVideo() {
        playEnd = false
        val format = MediaFormat.createVideoFormat("video/avc",
                configureMap!!.get("width")!!.toDouble().toInt(),
                configureMap!!.get("height")!!.toDouble().toInt())
        format.setInteger(MediaFormat.KEY_FRAME_RATE,
                configureMap!!.get("framerate")!!.toDouble().toInt())
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
        format.setByteBuffer("csd-1", ByteBuffer.wrap(pps))
        videoCodec = MediaCodec.createDecoderByType("video/avc")

        videoCodec.configure(format, sv_live.holder.surface, null, 0)
        videoCodec.start()
        Log.d("TAG", "play started...")
        thread {
            handleFrame()
        }
    }

    private lateinit var videoCodec: MediaCodec
    private val waitObj = Object()
    private val videoFrames = ArrayList<Frame>()
    private var playEnd = true

    private fun handleFrame() {
        while (!playEnd) {
            if (videoFrames.isNotEmpty()) {
                val frame = videoFrames.removeAt(0)
                if (frame.isVideo) {
                    decodeFrame(frame)
                }
                SystemClock.sleep(35)
            } else {
                synchronized(waitObj, {
                    try {
                        waitObj.wait(100)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        playEnd = true
                    }
                })
            }
        }
    }

    private fun decodeFrame(frame: Frame) {
        var index = videoCodec.dequeueInputBuffer(10000)
        if (index >= 0) {
            var buffer = videoCodec.getInputBuffer(index)
            buffer.clear()
            buffer.put(frame.data)
            videoCodec.queueInputBuffer(index, 0, frame.data.size, frame.time * 1000, 0)
        }
        val info = BufferInfo()
        index = videoCodec.dequeueOutputBuffer(info, 10000)
        if (index >= 0) {
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                playEnd = true
            }
            videoCodec.releaseOutputBuffer(index, true)
        }
    }


    override fun onStop() {
        super.onStop()
        player.close()
        playEnd = true
    }
}
