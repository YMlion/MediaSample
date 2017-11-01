package com.ymlion.mediasample.live

import android.app.Activity
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodec.createDecoderByType
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Bundle
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
    private lateinit var videoCodec: MediaCodec
    private val waitObj = Object()
    private val frames = ArrayList<Frame>()
    private var playEnd = true

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
        if (frame.isHeader) {
            playAudio(frame.data)
        } else {
            frames.add(frame)
            synchronized(waitObj, {
                waitObj.notify()
            })
        }
    }

    private lateinit var audioTrack: AudioTrack
    private lateinit var audioCodec: MediaCodec
    private fun playAudio(sps: ByteArray) {
        val sampleRate = configureMap!!["audiosamplerate"]!!.toDouble().toInt()
        val channelCount = configureMap!!["audiochannels"]!!.toDouble().toInt()
        val format = MediaFormat.createAudioFormat("audio/aac", sampleRate, channelCount)
        format.setByteBuffer("csd-0", ByteBuffer.wrap(sps))
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT)
        audioTrack = AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize,
                AudioTrack.MODE_STREAM)
        audioTrack.play()
        audioCodec = createDecoderByType("audio/aac")
        audioCodec.configure(format, null, null, 0)
        audioCodec.start()
    }

    override fun onPlayVideo(frame: Frame) {
        if (frame.isHeader) {
            if (!playEnd) {
                return
            }
            var l = ByteUtil.bytes2Int(2, frame.data, 0)
            val sps = ByteArray(l + 4)
            System.arraycopy(frame.data, 2, sps, 4, l)
            l = ByteUtil.bytes2Int(2, frame.data, l + 3)
            val pps = ByteArray(l + 4)
            for (i in 0..2) {
                sps[i] = 0
                pps[i] = 0
            }
            sps[3] = 1
            pps[3] = 1
            System.arraycopy(frame.data, sps.size + 1, pps, 4, l)
            Log.e("TAG", "get sps and pps")
            playVideo(sps, pps)
        } else {
            frames.add(frame)
            synchronized(waitObj, {
                waitObj.notify()
            })
        }
    }

    private fun playVideo(sps: ByteArray, pps: ByteArray) {
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

    private fun handleFrame() {
        while (!playEnd) {
            if (frames.isNotEmpty()) {
                val frame = frames.removeAt(0)
                if (frame.isVideo) {
                    decodeVideoFrame(frame)
                } else {
                    decodeAudioFrame(frame)
                }
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

    private fun decodeAudioFrame(frame: Frame) {
        var index = audioCodec.dequeueInputBuffer(10000)
        if (index >= 0) {
            var buffer = audioCodec.getInputBuffer(index)
            buffer.clear()
            buffer.put(frame.data)
            audioCodec.queueInputBuffer(index, 0, frame.data.size, frame.time * 1000, 0)
        }
        val info = BufferInfo()
        index = audioCodec.dequeueOutputBuffer(info, 10000)
        if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            // 当MediaFormat改变时，需要改变AudioTrack的sample rate
            val format = audioCodec.outputFormat
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            Log.d("TAG", "audio changed format map is : $format")
            audioTrack.playbackRate = sampleRate
            return
        }
        while (index >= 0) {
            var buffer = audioCodec.getOutputBuffer(index)
            buffer.position(info.offset)
            buffer.limit(info.offset + info.size)
            var data = ByteArray(info.size)
            buffer.get(data)
            audioTrack!!.write(data, 0, info.size)
            buffer.clear()
            audioCodec.releaseOutputBuffer(index, false)
            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                playEnd = true
            }
            index = audioCodec.dequeueOutputBuffer(info, 10000)
        }
    }

    private fun decodeVideoFrame(frame: Frame) {
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
