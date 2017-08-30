package com.ymlion.mediasample

import android.app.Activity
import android.content.Intent
import android.graphics.SurfaceTexture
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
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import android.view.View.OnClickListener
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import kotlinx.android.synthetic.main.activity_main.btn_file
import kotlinx.android.synthetic.main.activity_main.btn_play
import kotlinx.android.synthetic.main.activity_main.frame_layout
import kotlinx.android.synthetic.main.activity_main.textureView
import kotlinx.android.synthetic.main.activity_main.tv_path

class MainActivity : Activity(), OnClickListener {
    var playEnd = true
    var filePath = ""
    var sleepTime: Long = 0
    var audioPlayer: AudioPlayer = AudioPlayer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textureView.isOpaque = false
        textureView.surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
                return false
            }

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int,
                    height: Int) {
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int,
                    height: Int) {
            }
        }
        filePath = Environment.getExternalStorageDirectory().absolutePath + "/download/video10s.mp4"
        tv_path.text = filePath
        btn_file.setOnClickListener(this)
        btn_play.setOnClickListener(this)
    }

    override fun onClick(v: View?) {
        when (v!!.id) {
            R.id.btn_play -> {
                if (!playEnd) {
                    playEnd = true
                    audioPlayer.stop()
                } else {
                    Thread({
                        initCodec(Surface(textureView.surfaceTexture))
                    }).start()
                    Thread({
                        audioPlayer.start(filePath)
                    }).start()
                }
            }
            R.id.btn_file -> {
                val intent = Intent()
                intent.type = "video/*"
                intent.action = Intent.ACTION_GET_CONTENT
                startActivityForResult(intent, 1)
            }
        }
    }

    private lateinit var extractor: MediaExtractor

    private fun initCodec(surface: Surface) {
        playEnd = false
        checkCoder("video/avc", false)
        var trackIndex = getTrackIndex("video")
        if (trackIndex < 0) {
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
        while (!playEnd) {
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
            if (index >= 0) {
                if (sleepTime == 0L) {
                    sleepTime = System.currentTimeMillis()
                }
                var sleepTime1 = (info.presentationTimeUs / 1000) - (System.currentTimeMillis() - sleepTime)
                if (sleepTime1 > 0) {
                    SystemClock.sleep(sleepTime1)
                }
                codec.releaseOutputBuffer(index, true)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    playEnd = true
                }
            }
        }
        Log.d("TAG", "play finished!!!")
        codec.stop()
        codec.release()
        playEnd = true
        sleepTime = 0
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
        var duration = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION).toLong() / 1000.toFloat()
        var rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toInt()
        Log.d("TAG", "video duration : $duration ; orientation : $rotation")
        return rotation
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
