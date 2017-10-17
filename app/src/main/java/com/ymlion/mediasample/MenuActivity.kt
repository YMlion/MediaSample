package com.ymlion.mediasample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.ymlion.mediasample.capture.Camera1Activity
import com.ymlion.mediasample.capture.CaptureActivity
import com.ymlion.mediasample.player.VideoPlayActivity
import com.ymlion.mediasample.record.RecordActivity
import kotlinx.android.synthetic.main.activity_menu.btn_capture
import kotlinx.android.synthetic.main.activity_menu.btn_capture1
import kotlinx.android.synthetic.main.activity_menu.btn_playback
import kotlinx.android.synthetic.main.activity_menu.btn_record

class MenuActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        btn_playback.setOnClickListener {
            startActivity(Intent(this, VideoPlayActivity::class.java))
        }
        btn_record.setOnClickListener {
            startActivity(Intent(this, RecordActivity::class.java))
        }
        btn_capture.setOnClickListener {
            startActivity(Intent(this, CaptureActivity::class.java))
        }
        btn_capture1.setOnClickListener {
            startActivity(Intent(this, Camera1Activity::class.java))
        }

        /*thread {
            rtmpConnect()
            while (!close) {
            }
            rtmp.close()
        }*/
    }

    /*private lateinit var rtmp: Rtmp

    private fun rtmpConnect() {
        rtmp = Rtmp("10.32.10.219", "test", "live")
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
    }*/
}
