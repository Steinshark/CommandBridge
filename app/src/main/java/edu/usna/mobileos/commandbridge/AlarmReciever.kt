package edu.usna.mobileos.commandbridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class AlarmReceiver(): BroadcastReceiver() {
    override fun onReceive(p0: Context?, p1: Intent?) {
        Log.i("IT472", "Alarm receiver triggered")
        //start service
        val sIntent = Intent(p0, CommandService::class.java)
        var looping = p1!!.getBooleanExtra("loopRefresh",false)
        Log.i("test","fetched looping: ${looping}")
        p0?.startService(sIntent)
    }


}