package com.ymlion.mediasample.record

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.TextureView
import android.view.View
import com.ymlion.mediasample.R.layout
import com.ymlion.mediasample.record.RecordManager.RecordListener
import com.ymlion.rtmp.Rtmp
import kotlinx.android.synthetic.main.activity_record.record_seconds_tv
import kotlinx.android.synthetic.main.activity_record.textureView
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.concurrent.thread

class RecordActivity : Activity() {

    private lateinit var rm: RecordManager
    private lateinit var timer: CountDownTimer
    private var out: OutputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_record)
        initView()
        thread {
            rtmpConnect()
            while (!close) {
            }
            rtmp.close()
        }
    }

    private lateinit var rtmp: Rtmp

    private fun rtmpConnect() { //        rtmp = Rtmp("10.32.10.219", "test", "live")
        rtmp = Rtmp("23.106.136.168", "test", "live")
        try {
            rtmp.connect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var close: Boolean = false

    override fun onDestroy() {
        close = true
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        Log.d("MAIN", "onResume: ")
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(
                    Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA), 0)
            }
        }
    }

    private fun initView() {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int,
                    height: Int) {
                Log.d("MAIN", "onSurfaceTextureAvailable: $width; $height")
                if (Build.VERSION.SDK_INT >= 23) {
                    if (checkSelfPermission(
                            Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        openCamera(width, height)
                    }
                } else {
                    openCamera(width, height)
                }
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int,
                    height: Int) {
                Log.e("MAIN", "onSurfaceTextureSizeChanged: $width; $height")
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(
                    surface: SurfaceTexture) { //                Log.v("TAG", "onSurfaceTextureUpdated")
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>,
            grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 0) {
            for (grantResult in grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    finish()
                    return
                }
            }
            openCamera(textureView.width, textureView.height)
        }
    }

    private fun openCamera(width: Int, height: Int) {
        out = BufferedOutputStream(FileOutputStream(
                externalCacheDir.absolutePath + "/" + System.currentTimeMillis() + ".h264"))
        rm = RecordManager(this, textureView.surfaceTexture)
        Log.d("MAIN", "openCamera: texture view size : "
                + textureView.width
                + " ; "
                + textureView.height)
        rm.open(width, height)
        rm.setRecordListener(object : RecordListener {
            override fun onVideoFormatChanged(format: MediaFormat?) {
                val sps = format?.getByteBuffer("csd-0")
                val pps = format?.getByteBuffer("csd-1")
                val spsBytes = ByteArray(sps!!.limit())
                sps.get(spsBytes)
                val ppsBytes = ByteArray(pps!!.limit())
                pps!!.get(ppsBytes)
                Log.d("TAG", "onOutputFormatChanged: sps : "
                        + spsBytes.size
                        + "; pps : "
                        + ppsBytes.size)
                var builder = StringBuilder()
                for (spsByte in spsBytes) {
                    builder.append(spsByte.toInt()).append(' ')
                }
                Log.e("TAG", builder.toString())
                builder = StringBuilder()
                for (ppsByte in ppsBytes) {
                    builder.append(ppsByte.toInt()).append(' ')
                }
                Log.e("TAG", builder.toString())
                out?.write(spsBytes)
                out?.write(ppsBytes)
            }

            override fun onAudioFormatChanged(format: MediaFormat?) {
            }

            override fun onVideoFrame(frame: ByteArray?, time: Long) {
                Log.d("TAG", "onVideoFrame size is ${frame?.size} + $time")/*try {
                    rtmp.sendVideo(frame, (time / 1000).toInt())
                } catch (e: Exception) {
                    e.printStackTrace()
                }*/
                try {
                    out?.write(frame)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun onAudioFrame(frame: ByteArray?, time: Long) {
            }

        })
    }

    override fun onStop() {
        super.onStop()
        if (rm != null) {
            rm.close()
        }
    }

    fun stopRecord(view: View) {
        rm.stopRecord()
        timer.cancel()
        record_seconds_tv.visibility = View.GONE
        out?.flush()
        out?.close()
    }

    fun recordVideo(view: View) {
        record_seconds_tv.visibility = View.VISIBLE
        timer = object : CountDownTimer(60000, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val time = (60000 - millisUntilFinished) / 1000f
                record_seconds_tv.text = String.format("%.1fs", time)
            }

            override fun onFinish() {

            }
        }
        timer.start()
        rm.startRecord()
    }


    fun changeCamera(view: View) {
        if (rm != null) {
            rm.changeCamera(textureView)
        }
    }

}
