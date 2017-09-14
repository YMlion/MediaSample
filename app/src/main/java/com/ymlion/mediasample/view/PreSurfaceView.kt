package com.ymlion.mediasample.view

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLSurfaceView.Renderer
import android.util.AttributeSet
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


/**
 * Created by YMlion on 2017/9/14.
 */
class PreSurfaceView(context: Context?, attrs: AttributeSet?) : GLSurfaceView(context,
        attrs), SurfaceTexture.OnFrameAvailableListener, Renderer {

    private var mSurface: SurfaceTexture? = null
    private var mSurfaceListener: SurfaceListener? = null
    private var mDrawer: DirectDrawer? = null

    init {
        setRenderer(this)
        setEGLContextClientVersion(3)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClearColor(1f, 1f, 1f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        mSurface!!.updateTexImage()
        var mtx = FloatArray(16)
        mSurface!!.getTransformMatrix(mtx)
        mDrawer?.draw(mtx)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
        mSurfaceListener?.onSurfaceChanged()
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val textureID = createTextureID()
        mSurface = SurfaceTexture(textureID)
        mSurface!!.setOnFrameAvailableListener(this)
        mDrawer = DirectDrawer(textureID)
        mSurfaceListener?.onSurfaceCreated(mSurface!!)
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture?) {
        requestRender()
    }

    private fun createTextureID(): Int {
        val texture = IntArray(1)

        GLES30.glGenTextures(1, texture, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0])
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR.toFloat())
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR.toFloat())
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE)

        return texture[0]
    }

    fun getSurface(): SurfaceTexture? {
        return mSurface
    }

    fun setSurfaceListener(listener: SurfaceListener) {
        mSurfaceListener = listener
    }

    interface SurfaceListener {
        fun onSurfaceCreated(surfaceTexture: SurfaceTexture)
        fun onSurfaceChanged()
    }
}