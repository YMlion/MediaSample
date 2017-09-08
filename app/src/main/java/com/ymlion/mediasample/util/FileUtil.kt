package com.ymlion.mediasample.util

import java.io.File

/**
 * Created by YMlion on 2017/9/8.
 */
object FileUtil {
    public fun isFile(path: String): Boolean {
        val file = File(path)
        return file.exists() && file.length() > 0
    }
}