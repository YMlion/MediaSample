package com.ymlion.mediasample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_menu.btn_playback
import kotlinx.android.synthetic.main.activity_menu.btn_record

class MenuActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        btn_playback.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        btn_record.setOnClickListener {
            startActivity(Intent(this, RecordActivity::class.java))
        }
    }
}
