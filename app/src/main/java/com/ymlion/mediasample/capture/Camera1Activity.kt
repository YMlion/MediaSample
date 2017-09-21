package com.ymlion.mediasample.capture

import android.Manifest.permission
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Build.VERSION
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceHolder.Callback
import android.view.View
import com.ymlion.mediasample.R.layout
import com.ymlion.mediasample.util.ImageUtil
import kotlinx.android.synthetic.main.activity_camera1.faceView
import kotlinx.android.synthetic.main.activity_camera1.surfaceView
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class Camera1Activity : Activity(), ICameraCallback {

    private lateinit var camera: CameraCapture
    private var available = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_camera1)
        initView()
        camera = CameraCapture(this)
        camera.setCameraCallback(this)
    }

    override fun onResume() {
        super.onResume()
        Log.d("MAIN", "onResume: ")
        if (VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(
                    permission.CAMERA) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(arrayOf(permission.WRITE_EXTERNAL_STORAGE,
                        permission.RECORD_AUDIO, permission.CAMERA), 0)
            }
        }
        if (available) {
            openCamera(surfaceView.holder, surfaceView.width)
        }
        available = true
    }

    private fun initView() {
        surfaceView.holder.addCallback(object : Callback {
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int,
                    height: Int) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                if (VERSION.SDK_INT >= 23) {
                    if (checkSelfPermission(
                            permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        openCamera(holder, surfaceView.width)
                    }
                } else {
                    openCamera(holder, surfaceView.width)
                }
            }
        })
        faceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceDestroyed(holder: SurfaceHolder?) {
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
            }

            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int,
                    height: Int) {
                Log.d("TAG", "surface size is $width-$height")
            }
        })
        faceView.holder.setFormat(PixelFormat.TRANSPARENT)
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
            openCamera(surfaceView.holder, surfaceView.width)
        }
    }

    private fun openCamera(holder: SurfaceHolder?, width: Int) {
        Log.d("TAG", "open camera")
        camera.setFaceHolder(faceView.holder)
        camera.open(holder, width)
    }

    override fun onPause() {
        super.onPause()
        camera.close()
    }

    fun changeCamera(view: View) {
        camera.changeFacing()
        openCamera(surfaceView.holder, surfaceView.width)
    }

    fun capture(view: View) {
        camera.takePicture()
    }

    override fun onInitFinished(previewWidth: Int, previewHeight: Int) {
        val width = surfaceView.width
        val height = width * previewWidth / previewHeight
        val params = surfaceView.layoutParams
        params.height = height
        surfaceView.layoutParams = params
    }

    override fun onImageAvailable(data: ByteArray?) {
        val file = ImageUtil.getFile(0)
        var outputStream: OutputStream? = null
        try {
            outputStream = BufferedOutputStream(FileOutputStream(file))
            outputStream.write(data)
            outputStream.flush()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    companion object {
        init {
            System.loadLibrary("yuvtool")
//            System.loadLibrary("yuv")
        }
    }

}