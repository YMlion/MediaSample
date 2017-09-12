package com.ymlion.mediasample.capture

import android.Manifest.permission
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.os.Build.VERSION
import android.os.Bundle
import android.util.Log
import android.view.TextureView.SurfaceTextureListener
import android.view.View
import com.ymlion.mediasample.R.layout
import kotlinx.android.synthetic.main.activity_capture.textureView

class CaptureActivity : Activity() {

    private lateinit var rm: CaptureManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(layout.activity_capture)
        initView()
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
    }

    private fun initView() {
        textureView.surfaceTextureListener = object : SurfaceTextureListener {
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int,
                    height: Int) {
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
                return true
            }

            override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int,
                    height: Int) {
                if (VERSION.SDK_INT >= 23) {
                    if (checkSelfPermission(
                            permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        openCamera(width, height)
                    }
                } else {
                    openCamera(width, height)
                }
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
        rm = CaptureManager(this, textureView.surfaceTexture)
        rm.open(width, height)
    }

    override fun onStop() {
        super.onStop()
        rm.close()
    }

    fun changeCamera(view: View) {
        rm.changeCamera(textureView)
    }

    fun capture(view: View) {
        rm.capture()
    }

}