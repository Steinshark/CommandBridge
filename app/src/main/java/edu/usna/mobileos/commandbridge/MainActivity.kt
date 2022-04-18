package edu.usna.mobileos.commandbridge

import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btManager = getSystemService(BluetoothManager::class.java) as (BluetoothManager)
        btAdapter = btManager.getAdapter()
    }


    fun connectBluetooth(v: View?){
        if(btAdapter == null){
            Toast.makeText(this,"Bluetooth unavailable",Toast.LENGTH_LONG).show()
        }
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 2);
        }
        btDevice    = btAdapter.getRemoteDevice("8C:DE:00:01:8A:2C")
        socket      = btDevice.createRfcommSocketToServiceRecord(uuid)

        if(socket == null){
            Toast.makeText(this,"Socket Null",Toast.LENGTH_LONG).show()
        }
        else{
            Toast.makeText(this,"Socket Connected",Toast.LENGTH_LONG).show()
        }
    }
}
