package edu.usna.mobileos.commandbridge

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast

class CommandService: Service(){
    override fun onBind(p0: Intent?): IBinder? {
        TODO("Not yet implemented")
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("service","sent ObdRequest for ${intent?.getSerializableExtra("command")}")
        //sendCommand(intent.getSerializableExtra("command"))
        return super.onStartCommand(intent, flags, startId)
    }



}