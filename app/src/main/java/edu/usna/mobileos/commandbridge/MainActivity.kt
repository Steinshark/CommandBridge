package edu.usna.mobileos.commandbridge

import android.Manifest
import android.bluetooth.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.engine.*
import com.github.eltonvs.obd.command.control.*
import com.github.eltonvs.obd.command.fuel.*
import com.github.eltonvs.obd.command.temperature.*

import com.github.eltonvs.obd.command.pressure.*

import com.github.eltonvs.obd.connection.ObdDeviceConnection
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.*
import java.time.LocalDateTime

class MainActivity : AppCompatActivity(), DRInterface {
    lateinit var btManager: BluetoothManager
    lateinit var btAdapter: BluetoothAdapter
    lateinit var socket:    BluetoothSocket
    lateinit var obdCon:    ObdDeviceConnection
    lateinit var graphview: GraphView
    var btCapable       = true
    val uuid            = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")                   //Can be random (I think)
    val displayModes    = arrayOf("RPM","Lambda","Airflow","Speed","VANOS")
    val macAddress      = "8C:DE:00:01:8A:2C"                                                       //Hard code BTdev MAC
    var commands       = mutableMapOf<String,Command>(
        "RPM" to Command(RPMCommand() as ObdCommand, "RPM", obdCon),
        "SPD" to Command(SpeedCommand() as ObdCommand, "Speed", obdCon),
        "THR" to Command(ThrottlePositionCommand() as ObdCommand, "Throttle Pos", obdCon),
        "MAF" to Command(MassAirFlowCommand() as ObdCommand, "MAF Rate", obdCon),
        "IAT" to Command(AirIntakeTemperatureCommand() as ObdCommand, "Intake Air Temp", obdCon),
        "ECT" to Command(EngineCoolantTemperatureCommand() as ObdCommand, "Engine Coolant Temp", obdCon),
        "IMP" to Command(IntakeManifoldPressureCommand() as ObdCommand, "Intake Manifold Pressure", obdCon),
        "TAD" to Command(TimingAdvanceCommand() as ObdCommand, "Timing Advance", obdCon),
        "ERT" to Command(RuntimeCommand() as ObdCommand, "Engine Run Time", obdCon),
        "ELD" to Command(AbsoluteLoadCommand() as ObdCommand, "Engine Load", obdCon),
        "VIN" to Command(VINCommand() as ObdCommand, "VIN Decode", obdCon),
        "TRC" to Command(TroubleCodesCommand() as ObdCommand, "Trouble Codes", obdCon),
        "RES" to Command(ResetTroubleCodesCommand() as ObdCommand, "Reset Trouble Codes", obdCon),
        "DFC" to Command(DistanceSinceCodesClearedCommand() as ObdCommand, "Distance Since Cleared", obdCon),
        "RES" to Command(FuelPressureCommand() as ObdCommand, "Fuel Pressure", obdCon)
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        btManager = getSystemService(BluetoothManager::class.java) as (BluetoothManager)
        if(btManager.adapter == null){
            Toast.makeText(baseContext,"No Bluetooth Adapter Found",Toast.LENGTH_LONG).show()
            btCapable = false
        }
        else{
            btAdapter = btManager.getAdapter()
        }
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.options_menu,menu)
        return super.onCreateOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item : MenuItem): Boolean{
        return when(item.itemId) {
            R.id.fullView -> {
                true
            }
            R.id.individualView -> {
                val dialog = RadioButtonDialog(displayModes,this as DRInterface)
                dialog.show(supportFragmentManager, "Set DisplayMode")
                true
            }
            R.id.multiView -> {
                val dialog = CheckBoxDialog(displayModes,this as DRInterface)
                dialog.show(supportFragmentManager, "Set DisplayMode")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    override fun onDestroy() {
        super.onDestroy()
        socket.close()
    }
    fun connectBluetooth(v: View?){
        val t = Thread {
            if (btCapable == false || btAdapter == null) {
                runOnUiThread{
                    Toast.makeText(this, "Bluetooth unavailable", Toast.LENGTH_LONG).show()
                }
                return@Thread
            }
            if (ActivityCompat.checkSelfPermission(this,Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED){
                ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.BLUETOOTH_CONNECT),2)
            }
            val btDevice = btAdapter.getRemoteDevice(macAddress)
            socket = btDevice.createInsecureRfcommSocketToServiceRecord(uuid)
            try {
                socket.connect()
            }
            catch (e: IOException) {
                runOnUiThread{
                    Toast.makeText(this,"Socket could not connect", Toast.LENGTH_SHORT).show()}
                return@Thread
            }
            //Ensure that the socket is actually connected
            if (socket.isConnected) {
                obdCon = ObdDeviceConnection(socket.inputStream,socket.outputStream)
                runOnUiThread{
                    Toast.makeText(this, "Socket initialized", Toast.LENGTH_LONG).show()}
            }
        }
        t.start()
    }

    override fun setDisplayMode(items:ArrayList<String>){
        if (items.size <= 0){
            Toast.makeText(this,"No Items selected for display!",Toast.LENGTH_SHORT)
        }
        else if(items.size == 1){
            setContentView(R.layout.display_mode_one)

        }
        else{
            setContentView(R.layout.display_mode_group)

        }
    }
    override fun doNothing(item:String){}
    override fun cancel(){}
}



class Command(val ex:ObdCommand,val name:String, val obdCon:ObdDeviceConnection){
    var graphData       = LineGraphSeries<DataPoint>()
    var graphInitTime   = Calendar.getInstance().timeInMillis
    var graphLength     = 60

    fun runCommand() = runBlocking{
        val response = obdCon.run(ex)
        val x = ((graphInitTime - Calendar.getInstance().timeInMillis) / 1000) as Double
        val y = response.value as Double
        graphData.appendData(DataPoint(x,y),true,graphLength)
    }



}