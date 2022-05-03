package edu.usna.mobileos.commandbridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class CommandService: Service(){
    override fun onBind(p0: Intent?): IBinder? {
        TODO("Not yet implemented")
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        start_graphing()
        return START_STICKY
    }

    fun start_graphing(){
        Thread.sleep(1000)
        val broadcastIntent = Intent()
        broadcastIntent.action = "Update"
        baseContext.sendBroadcast(broadcastIntent)
    }


}