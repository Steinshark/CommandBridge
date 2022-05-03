package edu.usna.mobileos.commandbridge

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.bluetooth.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.RecyclerView
import com.github.eltonvs.obd.command.ObdCommand
import com.github.eltonvs.obd.command.engine.*
import com.github.eltonvs.obd.command.control.*
import com.github.eltonvs.obd.command.temperature.*

import com.github.eltonvs.obd.command.pressure.*

import com.github.eltonvs.obd.connection.ObdDeviceConnection
import com.jjoe64.graphview.GraphView
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList

class MainActivity : AppCompatActivity(), DRInterface, RecyclerListener {
    lateinit var btManager: BluetoothManager
    lateinit var btAdapter: BluetoothAdapter
    lateinit var socket:    BluetoothSocket
    lateinit var obdCon:    ObdDeviceConnection
    lateinit var graphview: GraphView
    lateinit var graphRecyclerView: RecyclerView
    lateinit var recyclerAdapter: RecyclerAdapter
    lateinit var pendingIntent: PendingIntent
    var btCapable       = true
    val uuid            = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")             //Can be random (I think)
    val displayModes    = arrayOf("RPM","Lambda","Airflow","Speed","VANOS")
    val macAddress      = "8C:DE:00:01:8A:2C"                                                       //Hard code BTdev MAC
    var graphsInView    = ArrayList<Command?>()
    lateinit var commands: MutableMap<String,Command>
    var continueUpdate = true
    lateinit var alarmManager: AlarmManager

    private val UpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            for(command in graphsInView){
                command?.runCommand()
            }
            recyclerAdapter = RecyclerAdapter(graphsInView)
            if(continueUpdate) {
                startCommandService()
            }
        }
    }




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

        //Setup our intent filter
        val intentFilter = IntentFilter()
        intentFilter.addAction("Update")
        registerReceiver(UpdateReceiver,intentFilter)

        startCommandService()
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
                    Toast.makeText(this, "Socket initialized", Toast.LENGTH_LONG).show()
                }
                commands  = mutableMapOf<String,Command>(
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
            }
        }
        t.start()
    }
    override fun setStaticDisplayMode(){
        setContentView(R.layout.display_mode_graph)

    }
    override fun setGraphDisplayMode(items: ArrayList<String>){
        if (items.size <= 0){
            Toast.makeText(this,"No Items selected for display!",Toast.LENGTH_SHORT)
        }
        else{
            setContentView(R.layout.recycler_view)
            graphRecyclerView = findViewById(R.id.recyclerView)
            graphsInView = ArrayList()
            for(name in items){
                graphsInView.add(commands[name])
            }
            recyclerAdapter = RecyclerAdapter(graphsInView)
            graphRecyclerView.adapter = recyclerAdapter
        }

    }
    override fun doNothing(item:String){}
    override fun cancel(){}
    override fun onItemClick(task: String) {
        TODO("Not yet implemented")
    }
    fun startCommandService(){
        val serviceIntent = Intent(baseContext,CommandService::class.java)
        startService(serviceIntent)
    }
}
class Command(val ex:ObdCommand,val name:String, val obdCon:ObdDeviceConnection){
    var graphData       = LineGraphSeries<DataPoint>()
    var graphInitTime   = Calendar.getInstance().timeInMillis
    var graphLength     = 60

    fun runCommand() = runBlocking{
        Log.i("test","RUNNING COM for $name")
        val response = obdCon.run(ex)
        val x = ((graphInitTime - Calendar.getInstance().timeInMillis) / 1000) as Double
        val y = response.value as Double
        graphData.appendData(DataPoint(x,y),true,graphLength)
    }
}