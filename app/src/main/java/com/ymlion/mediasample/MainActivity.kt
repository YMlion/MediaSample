package com.ymlion.mediasample

import android.app.Activity
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.view.View.OnClickListener
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import kotlinx.android.synthetic.main.activity_main.btn_file
import kotlinx.android.synthetic.main.activity_main.btn_play
import kotlinx.android.synthetic.main.activity_main.btn_stop
import kotlinx.android.synthetic.main.activity_main.frame_layout
import kotlinx.android.synthetic.main.activity_main.sb_time
import kotlinx.android.synthetic.main.activity_main.textureView
import kotlinx.android.synthetic.main.activity_main.tv_path
import kotlinx.android.synthetic.main.activity_main.tv_play_time
import kotlinx.android.synthetic.main.activity_main.tv_total_time

class MainActivity : Activity(), OnClickListener {
    var playEnd = true
    var filePath = ""
    var audioPlayer: AudioPlayer = AudioPlayer()
    var playPause = false
    var resumePosition = 0L
    var totalTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textureView.isOpaque = false
        filePath = Environment.getExternalStorageDirectory().absolutePath + "/download/video10s.mp4"
        tv_path.text = filePath
        btn_file.setOnClickListener(this)
        btn_play.setOnClickListener(this)
        btn_stop.setOnClickListener(this)
        sb_time.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                btn_stop.performClick()
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                resumePosition = seekBar!!.progress * 1000L
                btn_stop.performClick()
            }
        })
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.btn_play -> {
                if (playEnd) {
                    resumePosition = 0L
                    Thread({
                        initCodec(Surface(textureView.surfaceTexture))
                    }).start()
                    Thread({
                        audioPlayer.start(filePath)
                    }).start()
                    btn_play.text = "Pause"
                } else {
                    playPause = !playPause
                    btn_play.text = if (playPause) {
                        "Play"
                    } else {
                        "Pause"
                    }
                    audioPlayer.pause()
                }
            }
            R.id.btn_file -> {
                val intent = Intent()
                intent.type = "video/*"
                intent.action = Intent.ACTION_GET_CONTENT
                startActivityForResult(intent, 1)
            }
            R.id.btn_stop -> {
                if (playEnd) {
                    if (btn_stop.text.toString() == "Stop") {
                        return
                    }
                    Thread({
                        initCodec(Surface(textureView.surfaceTexture))
                    }).start()
                    Thread({
                        audioPlayer.resume(filePath)
                    }).start()
                    btn_play.text = "Pause"
                    btn_stop.text = "Stop"
                    Log.d("TAG", " resume position is $resumePosition")
                } else {
                    playEnd = true
                    audioPlayer.stop()
                    btn_play.text = "Play"
                    btn_stop.text = "Resume"
                }
            }
        }
    }

    private lateinit var extractor: MediaExtractor

    private fun initCodec(surface: Surface) {
        playEnd = false
        checkCoder("video/avc", false)
        var trackIndex = getTrackIndex("video")
        if (trackIndex < 0) {
            Log.w("TAG", "no video found!!!")
            return
        }
        extractor.selectTrack(trackIndex)
        var mediaFormat: MediaFormat = extractor.getTrackFormat(trackIndex)
        var mimeType = mediaFormat.getString(MediaFormat.KEY_MIME)
        val codec = MediaCodec.createDecoderByType(mimeType)

        runOnUiThread({
            var rotation = checkVideoInfo()
            calculateSize(mediaFormat, rotation)
        })

        codec.configure(mediaFormat, surface, null, 0)
        codec.start()
        Log.d("TAG", "play started...")
        decodeFrame(codec)
    }

    private fun decodeFrame(codec: MediaCodec) {
        var readNext = true
        var startTime: Long = 0
        var paused = false
        var pauseTime = 0L
        if (resumePosition > 0) {
            Log.e("TAG", "video pos $resumePosition")
            extractor.seekTo(resumePosition, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            while (resumePosition > extractor.sampleTime) {
                extractor.advance()
            }
            startTime = System.currentTimeMillis() - extractor.sampleTime / 1000
        }
        while (!playEnd) {
            if (playPause) {
                if (pauseTime == 0L) {
                    paused = true
                    pauseTime = System.currentTimeMillis()
                }
                continue
            }
            if (paused) {
                paused = false
                startTime += System.currentTimeMillis() - pauseTime
                pauseTime = 0L
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
                        val sampleTime = extractor.sampleTime
                        codec.queueInputBuffer(index, 0, simpleSize, sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val info = BufferInfo()
            var index = codec.dequeueOutputBuffer(info, 10000)
            if (index >= 0) {
                if (startTime == 0L) {
                    startTime = System.currentTimeMillis()
                }
                val ms = info.presentationTimeUs / 1000
                var sleepTime = ms - (System.currentTimeMillis() - startTime)
                if (sleepTime > 0) {
                    SystemClock.sleep(sleepTime)
                }
                resumePosition = info.presentationTimeUs
                codec.releaseOutputBuffer(index, true)
                runOnUiThread {
                    var second = (resumePosition / 1000000).toInt()
                    var minutes = second / 60
                    second %= 60
                    tv_play_time.text = "${addZero(minutes, 2)}:${addZero(second, 2)}"
                    sb_time.progress = (resumePosition / 1000).toInt()
                }
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    playEnd = true
                }
            }
        }
        Log.d("TAG", "play finished!!!")
        codec.stop()
        codec.release()
        extractor.release()
        playEnd = true
        runOnUiThread {
            btn_play.text = "Play"
        }
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

    private fun checkVideoInfo(): Int {
        var retriever = MediaMetadataRetriever()
        retriever.setDataSource(filePath)
        totalTime = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION).toLong()
        var rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt()
        Log.d("TAG", "video duration : $totalTime ; orientation : $rotation")
        var second = totalTime.toInt() / 1000
        var minutes = second / 60
        second %= 60
        tv_total_time.text = "${addZero(minutes, 2)}:${addZero(second, 2)}"
        sb_time.max = totalTime.toInt()
        return rotation
    }

    private fun addZero(num: Int, count: Int): String {
        return String.format("%0${count}d", num)
    }

    private fun calculateSize(mediaFormat: MediaFormat?, rotation: Int) {
        if (mediaFormat == null) {
            return
        }
        var vw = mediaFormat.getInteger(MediaFormat.KEY_WIDTH)
        var vh = mediaFormat.getInteger(MediaFormat.KEY_HEIGHT)
        if (rotation == 90 || rotation == 270) {
            var tmp = vw
            vw = vh
            vh = tmp
        }
        Log.d("TAG",
                "video width : $vw; height : $vh; texture width : ${textureView.width}; height : ${textureView.height} ")

        val sw = frame_layout.width
        val sh = frame_layout.height
        var dw = sw
        var dh = sh

        if (vw != sw || vh != sh) {
            if (sw >= vw && sh >= vh) {
                dw = vw
                dh = vh
            } else {
                if (vw > vh) {
                    var r1 = vw / 1.0f / sw
                    var r2 = vh / 1.0f / sh
                    if (r1 >= r2) {
                        dw = sw
                        dh = (vh / r1).toInt()
                    } else {
                        dw = (vw / r2).toInt()
                        dh = vh
                    }
                } else if (vw == vh) {
                    dw = vh
                    dh = vh
                } else {
                    dh = sh
                    dw = (vw / 1.0f / vh * dh).toInt()
                }
            }
        }
        Log.d("TAG", "display width : $dw; height : $dh")
        var lp: FrameLayout.LayoutParams = textureView.layoutParams as LayoutParams
        lp.width = dw
        lp.height = dh
        lp.gravity = Gravity.CENTER
        textureView.layoutParams = lp
    }

    private fun checkCoder(mime: String, encoder: Boolean) {
        val codecCount = MediaCodecList.getCodecCount()
        var mediaInfo: MediaCodecInfo? = null
        for (i in 0 until codecCount) {
            val info = MediaCodecList.getCodecInfoAt(i)
            if (encoder && !info.isEncoder) {
                continue
            } else if (!encoder && info.isEncoder) {
                continue
            }
            var found = false
            val types = info.supportedTypes
            for (type in types) {
                if (type == mime) {
                    found = true
                    mediaInfo = info
                    break
                }
            }
            if (found) {
                break
            }
        }
        val formats = mediaInfo?.getCapabilitiesForType(mime)?.colorFormats
        formats?.forEach {
            Log.d("Encoder", "support " + it)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 1 && resultCode == RESULT_OK) {
            val uri = data!!.data
            Log.d("TAG", uri.toString())
            val cursor = contentResolver.query(uri, null, null, null, null)
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
                if (index > -1) {
                    filePath = cursor.getString(index)
                    tv_path.text = filePath
                }
                playEnd = true
                audioPlayer.stop()
                btn_play.text = "Play"
                btn_stop.text = "Stop"
            }
            if (!cursor.isClosed) {
                cursor.close()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        playEnd = true
        audioPlayer.stop()
    }
}
