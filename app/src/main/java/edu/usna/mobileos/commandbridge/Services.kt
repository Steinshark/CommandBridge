package edu.usna.mobileos.commandbridge

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log

class CommandService: Service() {


    var sendUpdateMessage: Boolean = true
    var cycleDuration: Double = 0.5

    private val ToggleGraphFetchReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            sendUpdateMessage = intent!!.getBooleanExtra("Refresh",true)
            cycleDuration = intent!!.getDoubleExtra("RefreshTime",0.5)
            Log.i("service","\tupdated to $sendUpdateMessage, every $cycleDuration")
        }
    }

    override fun onBind(p0: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i("service","Launched ")
        sendUpdateMessage = intent!!.getBooleanExtra("CycleUpdate",true)
        cycleDuration = intent!!.getDoubleExtra("RefreshTime",.5)
        start_graphing()

        //Create the intent filter to listen for updates from main
        val intentFilter = IntentFilter()
        intentFilter.addAction("Refresh")
        registerReceiver(ToggleGraphFetchReceiver,intentFilter)

        return START_STICKY
    }

    fun start_graphing() {

        val refreshThread = Thread{
            Log.i("service","\tstarting thread")
            val broadcastIntent = Intent()
            broadcastIntent.action = "Update"

            while(true){
                Thread.sleep(500)
                if (sendUpdateMessage){
                    baseContext.sendBroadcast(broadcastIntent)
                }

            }
        }
        refreshThread.start()
    }
}