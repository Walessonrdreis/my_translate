package com.walesson.screentranslator

import android.app.Service
import android.content.Intent
import android.os.IBinder

class BubbleService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
