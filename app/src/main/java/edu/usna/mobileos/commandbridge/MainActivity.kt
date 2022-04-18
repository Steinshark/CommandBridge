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
    var btCapable = true

    //BLUETOOTH INITS 
    lateinit var btManager: BluetoothManager
    lateinit var btAdapter: BluetoothAdapter
    lateinit var btDevice: BluetoothDevice
    
    //BLUETOOTH  
    val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    lateinit var socket: BluetoothSocket

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Check and init the BT manager and adapter
        btManager = getSystemService(BluetoothManager::class.java) as (BluetoothManager)
        if(btManager.adapter == null){
            Toast.makeText(baseContext,"No Bluetooth Adapter Found",Toast.LENGTH_LONG).show()
            btCapable = false
        }
        else{
            btAdapter = btManager.getAdapter()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        socket.close()
    }

    // Function to connect bluetooth device.
    // Attatched to a button in the GUI.
    fun connectBluetooth(v: View?){

        //Check that device is btCapable and that adapter exists
        if(btCapable == false || btAdapter == null){
            Toast.makeText(this,"Bluetooth unavailable",Toast.LENGTH_LONG).show()
            return
        }

        //Check that BT permissions were allowed by user, and if not, ask them for permission
        if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 2);
        }

        //Attempt The bluetooth pairing and create a socket to the device
        btDevice    = btAdapter.getRemoteDevice("8C:DE:00:01:8A:2C")
        socket      = btDevice.createRfcommSocketToServiceRecord(uuid)

        //if socket null, then we're out
        if(socket == null){
            Toast.makeText(this,"Socket Null",Toast.LENGTH_LONG).show()
        }
        else{
            Toast.makeText(this,"Socket initialized",Toast.LENGTH_LONG).show()
        }
    }
}
