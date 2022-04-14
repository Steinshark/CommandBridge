package edu.usna.mobileos.commandbridge

import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.util.*

class MainActivity : AppCompatActivity() {
    // Class inits

    //BLUETOOTH
    lateinit var btManager: BluetoothManager
    lateinit var btAdapter: BluetoothAdapter
    lateinit var btDevice: BluetoothDevice

    // NETWORK
    val uuid        = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    lateinit var socket: BluetoothSocket




    lateinit var displayText: TextView
    lateinit var jsonOBJ: JSONObject
    val intentReceiver = object: BroadcastReceiver(){
        override fun onReceive(p0: Context?, p1: Intent?) {

            //Grab the message from the thing
            val feedString = p1?.getStringExtra("message")
            displayText.text = feedString
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btManager = getSystemService(BluetoothManager::class.java) as (BluetoothManager)
        btAdapter = btManager.getAdapter()



        val apiKey = "646463100831aeea0ccec00b18447d24"
        val location = "38.9784,-76.4922"
        val urlString = "https://api.forecast.io/forecast/$apiKey/$location"








        displayText = findViewById(R.id.displayText)

        val intentFilter = IntentFilter()
        intentFilter.addAction("JSON_RETRIEVED") //note the same action as broadcast by the Service
        registerReceiver(intentReceiver, intentFilter)

        startService(
            Intent(baseContext, ReadJSONService::class.java).putExtra(
                "urlString",
                urlString
            )
        )
    }


    fun connectBluetooth(){
        if(btAdapter == null){
            Toast.makeText(this,"Bluetooth unavailable",Toast.LENGTH_LONG).show()
        }
        else{
            btDevice    = btAdapter.getRemoteDevice("8C:DE::00:01:8A:2C")
            socket      = btDevice.createRfcommSocketToServiceRecord(uuid)

        }
    }


    private fun retrieveSiteContent(urlString: String): String {

        var returnString = ""
        try {
            val url = URL(urlString)
            val urlConnection = url.openConnection() as HttpURLConnection
            val reader = urlConnection.inputStream.bufferedReader()
            returnString = reader.readText()
            reader.close()
            urlConnection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return returnString
    }
}

class ReadJSONService : Service() {

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        doWork(intent)
        return START_STICKY
    }

    private fun doWork(intent: Intent?) {
        val urlString = intent?.getStringExtra("urlString") ?: ""
        val myThread = Thread {
            Log.i("IT472", "retrieving JSON from $urlString")
            val feedString = retrieveSiteContent(urlString)
            Log.i("IT472", "received $feedString")

            //broadcast intent
            val broadcastIntent = Intent()
            broadcastIntent.putExtra("message", feedString)
            broadcastIntent.action = "JSON_RETRIEVED"
            baseContext.sendBroadcast(broadcastIntent)

            //stop the service
            stopSelf()
        }
        myThread.start()
    }


    private fun retrieveSiteContent(urlString: String): String {

        var returnString = ""
        try {
            val url = URL(urlString)
            val urlConnection = url.openConnection() as HttpURLConnection
            val reader = urlConnection.inputStream.bufferedReader()
            returnString = reader.readText()
            reader.close()
            urlConnection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return returnString
    }
}