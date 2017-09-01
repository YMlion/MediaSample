package com.ymlion.mediasample

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import kotlin.experimental.or


/**
 * Created by YMlion on 2017/8/30.
 */
class AudioPlayer {

    var playEnd = true
    var filePath = ""
    var player: AudioTrack? = null
    var pause = false
    var resumePosition = 0L

    private lateinit var extractor: MediaExtractor

    fun start(file: String) {
        resumePosition = 0L
        filePath = file
        initCodec()
    }

    fun resume(file: String) {
        filePath = file
        initCodec()
    }

    fun pause() {
        pause = !pause
    }

    private fun initCodec() {
        playEnd = false
        var trackIndex = getTrackIndex("audio")
        if (trackIndex < 0) {
            Log.w("TAG", "no audio found!!!")
            return
        }
        extractor.selectTrack(trackIndex)
        var mediaFormat: MediaFormat = extractor.getTrackFormat(trackIndex)
        var mimeType = mediaFormat.getString(MediaFormat.KEY_MIME)
        val sampleRate = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT)
        Log.d("TAG", "audio format map : $mediaFormat; buffer size : $minBufferSize")
        player = AudioTrack(AudioManager.STREAM_MUSIC,
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, minBufferSize,
                AudioTrack.MODE_STREAM)
        player!!.play()
        val codec = MediaCodec.createDecoderByType(mimeType)
        val format: MediaFormat?
        format = if (mimeType == "audio/mp4a-latm") {
            val channel = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            makeAACCodecSpecificData(MediaCodecInfo.CodecProfileLevel.AACObjectLC,
                    sampleRate, channel)
        } else {
            mediaFormat
        }

        codec.configure(format, null, null, 0)
        codec.start()
        Log.d("TAG", "audio play started...")
        decodeFrame(codec)
    }

    private fun decodeFrame(codec: MediaCodec) {
        var readNext = true
        if (resumePosition > 0) {
            extractor.seekTo(resumePosition, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            while (resumePosition > extractor.sampleTime) {
                extractor.advance()
            }
        }
        while (!playEnd) {
            if (pause) {
                continue
            }
            if (readNext) {
                var index = codec.dequeueInputBuffer(10000)
                if (index >= 0) {
                    var buffer = codec.getInputBuffer(index)
                    var simpleSize = extractor.readSampleData(buffer, 0)
                    if (simpleSize < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        readNext = false
                    } else {
                        codec.queueInputBuffer(index, 0, simpleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val info = BufferInfo()
            var index = codec.dequeueOutputBuffer(info, 10000)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // 当MediaFormat改变时，需要改变AudioTrack的sample rate
                val format = codec.outputFormat
                val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                Log.d("TAG", "audio changed format map is : $format")
                player!!.playbackRate = sampleRate
                continue
            }
            while (index >= 0) {
                var buffer = codec.getOutputBuffer(index)
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                var data = ByteArray(info.size)
                buffer.get(data)
                player!!.write(data, 0, info.size)
                buffer.clear()
                codec.releaseOutputBuffer(index, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    playEnd = true
                }
                resumePosition = info.presentationTimeUs
                index = codec.dequeueOutputBuffer(info, 10000)
            }
        }
        Log.d("TAG", "audio play finished!!!")
        codec.stop()
        codec.release()
        player!!.stop()
        player!!.release()
        extractor.release()
        playEnd = true
    }

    fun stop() {
        playEnd = true
    }

    private fun getTrackIndex(type: String): Int {
        extractor = MediaExtractor()
        extractor.setDataSource(filePath)
        val trackCount = extractor.trackCount
        for (trackIndex in 0 until trackCount) {
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime.startsWith(type)) {
                return trackIndex
            }
        }
        return -1
    }

    /**
     * The code profile, Sample rate, channel Count is used to
     * produce the AAC Codec SpecificData.
     * Android 4.4.2/frameworks/av/media/libstagefright/avc_utils.cpp refer
     * to the portion of the code written.
     *
     * MPEG-4 Audio refer : http://wiki.multimedia.cx/index.php?title=MPEG-4_Audio#Audio_Specific_Config
     *
     * @param audioProfile is MPEG-4 Audio Object Types
     * @param sampleRate
     * @param channelConfig
     * @return MediaFormat
     */
    private fun makeAACCodecSpecificData(audioProfile: Int, sampleRate: Int,
            channelConfig: Int): MediaFormat? {
        val format = MediaFormat()
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm")
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, sampleRate)
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, channelConfig)

        val samplingFreq = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000,
                12000, 11025, 8000)

        // Search the Sampling Frequencies
        var sampleIndex = -1
        for (i in samplingFreq.indices) {
            if (samplingFreq[i] == sampleRate) {
                Log.d("TAG", "kSamplingFreq " + samplingFreq[i] + " i : " + i)
                sampleIndex = i
            }
        }

        if (sampleIndex == -1) {
            return null
        }

        val csd = ByteBuffer.allocate(2)
        csd.put((audioProfile shl 3 or (sampleIndex shr 1)).toByte())

        csd.position(1)
        csd.put(((sampleIndex shl 7 and 0x80).toByte() or (channelConfig shl 3).toByte()))
        csd.flip()
        format.setByteBuffer("csd-0", csd) // add csd-0

        for (k in 0 until csd.capacity()) {
            Log.e("TAG", "csd : " + csd.array()[k])
        }

        return format
    }
}