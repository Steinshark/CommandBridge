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
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
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
    lateinit var serviceIntent: Intent
    lateinit var commands: MutableMap<String,Command>
    lateinit var alarmManager: AlarmManager
    var displayMode: String = "Static"
    var btCapable       = true
    val uuid            = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")             //Can be random (I think)
    val displayModes    = arrayOf("RPM","Lambda","Airflow","Speed","VANOS")
    val macAddress      = "8C:DE:00:01:8A:2C"                                                       //Hard code BTdev MAC
    var graphsInView    = ArrayList<Command?>()
    var continueUpdate  = true
    var serviceRunning  = false
    private val UpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            for(command in graphsInView){
                command?.runCommand()
            }
            if(displayMode == "Graph"){
                recyclerAdapter = RecyclerAdapter(graphsInView)
            }
            else if(displayMode == "Static"){
                updateStaticView()
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
    }
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.options_menu,menu)
        return super.onCreateOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item : MenuItem): Boolean{
        return when(item.itemId) {
            R.id.valuesView -> {
                setStaticDisplayMode()
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
        if(socket != null){
            socket.close()
        }
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
        setContentView(R.layout.static_display)
        displayMode = "Static"
        startCommandService(true,.05)

    }
    override fun setGraphDisplayMode(items: ArrayList<String>){
        if (items.size <= 0){
            Toast.makeText(this,"No Items selected for display!",Toast.LENGTH_SHORT)
        }
        else {
            try {
                setContentView(R.layout.recycler_view)
                displayMode = "Graph"
                graphRecyclerView = findViewById(R.id.recyclerView)
                graphsInView = ArrayList()
                for (name in items) {
                    graphsInView.add(commands[name])
                }
                recyclerAdapter = RecyclerAdapter(graphsInView)
                graphRecyclerView.adapter = recyclerAdapter
                startCommandService(true,0.5)                                                       //Updates graphs every .5 seconds
            } catch (e: UninitializedPropertyAccessException) {                                     //Make sure commands actually exist
                Toast.makeText(this, "No commands have been initialized!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun doNothing(item:String){}
    override fun cancel(){}
    override fun onItemClick(task: String) {
        TODO("Not yet implemented")
    }
    fun startCommandService(refreshMode:Boolean,refreshTime:Double){
        serviceIntent = Intent(baseContext,CommandService::class.java)
        serviceIntent.putExtra("CycleUpdate",refreshMode)                                     //Set to update
        serviceIntent.putExtra("RefreshTime",refreshTime)                                     //Update every .5 seconds
        Log.i("main","Starting service")
        startService(serviceIntent)
    }
    fun updateServiceRefresh(){
        val broadcastIntent = Intent()
        broadcastIntent.action = "Refresh"
        broadcastIntent.putExtra("CycleUpdate",true)
        broadcastIntent.putExtra("RefreshTime",.5)
        baseContext.sendBroadcast(broadcastIntent)
    }
    fun updateStaticView(){
        var updatedSuccess = false

        val rpm: TextView = findViewById(R.id.rpm)
        val speed: TextView = findViewById(R.id.speed)
        val throttleposition: TextView = findViewById(R.id.throttleposition)
        val massairflow: TextView = findViewById(R.id.massairflow)
        val intakeairtemp: TextView = findViewById(R.id.intakeairtemp)
        val enginecoolanttemp: TextView = findViewById(R.id.enginecoolanttemp)
        val intakemanifoldpsi: TextView = findViewById(R.id.intakemanifoldpsi)
        val timingadvance: TextView = findViewById(R.id.timingadvance)
        val engineruntime: TextView = findViewById(R.id.engineruntime)
        val engineload: TextView = findViewById(R.id.engineload)
        val vin: TextView = findViewById(R.id.vin)
        val fuelpressure: TextView = findViewById(R.id.fuelpressure)

        try{
            commands["RPM"]?.fetchValue()
            commands["SPD"]?.fetchValue()
            commands["THR"]?.fetchValue()
            commands["MAF"]?.fetchValue()
            commands["IAT"]?.fetchValue()
            commands["ECT"]?.fetchValue()
            commands["IMP"]?.fetchValue()
            commands["TAD"]?.fetchValue()
            commands["ERT"]?.fetchValue()
            commands["ELD"]?.fetchValue()
            commands["VIN"]?.fetchValue()
            commands["RES"]?.fetchValue()

            rpm.text = commands["RPM"]?.liveData.toString()
            speed.text = commands["SPD"]?.liveData.toString()
            throttleposition.text = commands["THR"]?.liveData.toString()
            massairflow.text = commands["MAF"]?.liveData.toString()
            intakeairtemp.text = commands["IAT"]?.liveData.toString()
            enginecoolanttemp.text = commands["ECT"]?.liveData.toString()
            intakemanifoldpsi.text = commands["IMP"]?.liveData.toString()
            timingadvance.text = commands["TAD"]?.liveData.toString()
            engineruntime.text = commands["ERT"]?.liveData.toString()
            engineload.text = commands["ELD"]?.liveData.toString()
            vin.text = commands["VIN"]?.liveData.toString()
            fuelpressure.text = commands["RES"]?.liveData.toString()

            updatedSuccess = true
        }
        catch(u: UninitializedPropertyAccessException){
            Toast.makeText(this,"Values cannot be updated",Toast.LENGTH_LONG).show()
            updatedSuccess = false
        }
        if(updatedSuccess && !serviceRunning){
            startCommandService(true,0.5)
        }
        else if(!updatedSuccess){
            stopService(serviceIntent)
            serviceRunning = false
        }
    }
}
class Command(val ex:ObdCommand,val name:String, val obdCon:ObdDeviceConnection){
    var graphData       = LineGraphSeries<DataPoint>()
    var graphInitTime   = Calendar.getInstance().timeInMillis
    var graphLength     = 60
    var liveData        = 0.0                                                                       //Twice's BAC
    fun runCommand() = runBlocking{
        Log.i("test","RUNNING COM for $name")
        val response = obdCon.run(ex)
        val x = ((graphInitTime - Calendar.getInstance().timeInMillis) / 1000) as Double
        val y = response.value as Double
        graphData.appendData(DataPoint(x,y),true,graphLength)
    }

    fun fetchValue() = runBlocking {
        val response = obdCon.run(ex)
        liveData = response.value as Double
    }
}