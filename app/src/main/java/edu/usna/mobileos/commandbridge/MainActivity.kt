package edu.usna.mobileos.commandbridge

import android.Manifest
import android.bluetooth.*
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.lang.reflect.Method
import java.util.*

class MainActivity : AppCompatActivity() {
    // Class inits
    var btCapable = true

    //Bluetooth environment
    lateinit var btManager: BluetoothManager
    lateinit var btAdapter: BluetoothAdapter
    lateinit var socket: BluetoothSocket

    //Bluetooth variables
    val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    val macAddress  = "8C:DE:00:01:8A:2C" //Make selectable for other devices


    // OVERRIDE THE ON CREATE FUNCTION
    // SETUP THE BLUETOOTH ENVIRONMENT
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //Check and init the BT manager and adapter
        btManager = getSystemService(BluetoothManager::class.java) as (BluetoothManager)
        if(btManager.adapter == null){
            Toast.makeText(baseContext,"No Bluetooth Adapter Found",Toast.LENGTH_LONG).show()
            btCapable = false}
        else{
            btAdapter = btManager.getAdapter()}
    }

    
    override fun onDestroy() {
        super.onDestroy()
        // ensure the Bluetooth socket is closed
        socket.close()
    }

    // Function to connect bluetooth device.
    // Attached to a button in the GUI.
    fun connectBluetooth(v: View?){
        val t = Thread {
            //Check that device is btCapable and that adapter exists
            if (btCapable == false || btAdapter == null) {
                runOnUiThread{
                    Toast.makeText(this, "Bluetooth unavailable", Toast.LENGTH_LONG).show()}
                return@Thread}

            //Check that BT permissions were allowed by user, and if not, ask them for permission
            if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.BLUETOOTH_CONNECT),2)}


            //Get Device -> in this case it is an OBD 2 scanner
            //Attempt to create a socket with the device
            val btDevice = btAdapter.getRemoteDevice(macAddress)
            socket = btDevice.createInsecureRfcommSocketToServiceRecord(uuid)
            try {
                socket.connect()
            }
            catch (e: IOException) {
                runOnUiThread{
                    Toast.makeText(this, "${e.message} - EXPECTED", Toast.LENGTH_SHORT).show()}
                return@Thread}

            //Ensure that the socket is actually connected
            if (socket.isConnected) {
                runOnUiThread{
                    Toast.makeText(this, "Socket initialized", Toast.LENGTH_LONG).show()}}
        }
        t.start()
    }


    fun sendCommandRequest(v:View?,command:String){
        socket.outputStream.write(command.toByteArray())
    }
}
