package com.ymlion.mediasample.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.media.Image
import android.os.Environment
import android.text.format.DateFormat
import android.util.Log
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


/**
 * Created by YMlion on 2017/9/4.
 */
object ImageUtil {

    private val COLOR_FormatI420 = 1
    private val COLOR_FormatNV21 = 2

    val FILE_TypeI420 = 1
    val FILE_TypeNV21 = 2
    val FILE_TypeJPEG = 3

    fun getFile(type: Int): File {
        val fileName = DateFormat.format("yyyyMMddHHmmss", System.currentTimeMillis()).toString()
        val dirType = if (type == 0) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
        val fileType = if (type == 0) ".jpg" else ".mp4"
        return File(Environment.getExternalStoragePublicDirectory(dirType).absolutePath
                + "/"
                + fileName
                + fileType)
    }

    fun saveImage(image: Image?) {
        if (image == null) {
            return
        }
        val file = getFile(0)
        try {
            var outputStream = BufferedOutputStream(FileOutputStream(file))
            val rect = image.cropRect
            val yuvImage = YuvImage(getDataFromImage(image,
                    COLOR_FormatNV21), ImageFormat.NV21,
                    rect.width(), rect.height(), null)
            yuvImage.compressToJpeg(rect, 100, outputStream)
            Log.d("TAG", "frame saved in ${file.absolutePath}")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }

    fun getBitmap(image: Image?): Bitmap? {
        var s = System.currentTimeMillis()
        if (image == null) {
            return null
        }
        try {
            val data = getDataFromImage(image,
                    COLOR_FormatNV21)
            var outputStream = ByteArrayOutputStream()
            val rect = image.cropRect
            val yuvImage = YuvImage(data, ImageFormat.NV21,
                    rect.width(), rect.height(), null)
            yuvImage.compressToJpeg(rect, 100, outputStream)
            var bm = BitmapFactory.decodeByteArray(outputStream.toByteArray(), 0, outputStream.size())
            image.close()
            Log.d("ImageUtil", "parse image use ${System.currentTimeMillis() - s}ms")
            return bm
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
        return null
    }

    fun getDataFromImage(image: Image, colorFormat: Int): ByteArray {
        if (colorFormat != COLOR_FormatI420 && colorFormat != COLOR_FormatNV21) {
            throw IllegalArgumentException(
                    "only support COLOR_FormatI420 " + "and COLOR_FormatNV21")
        }
        if (!isImageFormatSupported(image)) {
            throw RuntimeException("can't convert Image to byte array, format " + image.format)
        }
        val crop = image.cropRect
        val format = image.format
        val width = crop.width()
        val height = crop.height()
        val planes = image.planes
        val data = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
        val rowData = ByteArray(planes[0].rowStride)
        Log.v("TAG", "get data from " + planes.size + " planes")
        var channelOffset = 0
        var outputStride = 1
        for (i in planes.indices) {
            when (i) {
                0 -> {
                    channelOffset = 0
                    outputStride = 1
                }
                1 -> if (colorFormat == COLOR_FormatI420) {
                    channelOffset = width * height
                    outputStride = 1
                } else if (colorFormat == COLOR_FormatNV21) {
                    channelOffset = width * height + 1
                    outputStride = 2
                }
                2 -> if (colorFormat == COLOR_FormatI420) {
                    channelOffset = (width.toDouble() * height.toDouble() * 1.25).toInt()
                    outputStride = 1
                } else if (colorFormat == COLOR_FormatNV21) {
                    channelOffset = width * height
                    outputStride = 2
                }
            }
            val buffer = planes[i].buffer
            val rowStride = planes[i].rowStride
            val pixelStride = planes[i].pixelStride
            Log.v("TAG", "pixelStride " + pixelStride)
            Log.v("TAG", "rowStride " + rowStride)
            Log.v("TAG", "width " + width)
            Log.v("TAG", "height " + height)
            Log.v("TAG", "buffer size " + buffer.remaining())
            val shift = if (i == 0) 0 else 1
            val w = width shr shift
            val h = height shr shift
            buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
            for (row in 0 until h) {
                val length: Int
                if (pixelStride == 1 && outputStride == 1) {
                    length = w
                    buffer.get(data, channelOffset, length)
                    channelOffset += length
                } else {
                    length = (w - 1) * pixelStride + 1
                    buffer.get(rowData, 0, length)
                    for (col in 0 until w) {
                        data[channelOffset] = rowData[col * pixelStride]
                        channelOffset += outputStride
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
            Log.d("TAG", "Finished reading data from plane " + i)
        }
        return data
    }

    private fun isImageFormatSupported(image: Image): Boolean {
        val format = image.format
        when (format) {
            ImageFormat.YUV_420_888, ImageFormat.NV21, ImageFormat.YV12 -> return true
        }
        return false
    }

}