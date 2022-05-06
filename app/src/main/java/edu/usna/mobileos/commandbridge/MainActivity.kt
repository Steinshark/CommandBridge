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
import com.github.eltonvs.obd.command.*
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
import java.lang.NullPointerException
import java.util.*
import kotlin.collections.ArrayList


// Main activity has 4 distinct views.
//  1 - "activity_main": Home screen to connect to bluetooth device
//  2 - "static_display": Displays realtime values of vehicle sensors
//  3 - "recycler_view":  Single graph of a sensor displayed
//  4 - "recycler_view":  Multiple graphs displayed of sensors
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
    val displayModes    = arrayOf("RPM","Speed","Throttle","Airflow","IAT","Coolant Temp","IMP","TimingAdvance")
    val macAddress      = "8C:DE:00:01:8A:2C"                                                       //Hard code BTdev MAC
    var graphsInView    = ArrayList<Command?>()
    var continueUpdate  = true
    var serviceRunning  = false
    var noDataWarned    = false
    private val UpdateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            for(command in graphsInView){
                command?.runCommand()
            }
            if(displayMode == "Graph"){
                graphRecyclerView.adapter?.notifyDataSetChanged()
                //recyclerAdapter = RecyclerAdapter(graphsInView)
                //graphRecyclerView.adapter = recyclerAdapter
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
        if(serviceRunning){
            stopService(serviceIntent)
            serviceRunning = false
        }
    }
    override fun onOptionsItemSelected(item : MenuItem): Boolean{
        if(serviceRunning){
            stopService(serviceIntent)
            serviceRunning = false
        }
        return when(item.itemId) {
            R.id.homeView ->{

                setContentView(R.layout.activity_main)
                true

            }
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
        try{
            socket.close()
        }
        catch(u: UninitializedPropertyAccessException){

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
                    "RPM" to Command(RPMCommand() as ObdCommand, "RPM", obdCon,this),
                    "Speed" to Command(SpeedCommand() as ObdCommand, "Speed", obdCon,this),
                    "Throttle" to Command(ThrottlePositionCommand() as ObdCommand, "Throttle Position", obdCon,this),
                    "Airflow" to Command(MassAirFlowCommand() as ObdCommand, "MAF Rate", obdCon,this),
                    "IAT" to Command(AirIntakeTemperatureCommand() as ObdCommand, "Intake Air Temp", obdCon,this),
                    "Coolant Temp" to Command(EngineCoolantTemperatureCommand() as ObdCommand, "Engine Coolant Temp", obdCon,this),
                    "IMP" to Command(IntakeManifoldPressureCommand() as ObdCommand, "Intake Manifold Pressure", obdCon,this),
                    "Timing Advance" to Command(TimingAdvanceCommand() as ObdCommand, "Timing Advance", obdCon,this),
                    "Engine Uptime" to Command(RuntimeCommand() as ObdCommand, "Engine Run Time", obdCon,this),
                    "ELD" to Command(AbsoluteLoadCommand() as ObdCommand, "Engine Load", obdCon,this),
                    "VIN" to Command(VINCommand() as ObdCommand, "VIN Decode", obdCon,this),
                    "TRC" to Command(TroubleCodesCommand() as ObdCommand, "Trouble Codes", obdCon,this),
                    "RES" to Command(ResetTroubleCodesCommand() as ObdCommand, "Reset Trouble Codes", obdCon,this),
                    "DFC" to Command(DistanceSinceCodesClearedCommand() as ObdCommand, "Distance Since Cleared", obdCon,this),
                    "RES" to Command(FuelPressureCommand() as ObdCommand, "Fuel Pressure", obdCon,this)
                )
            }
        }
        t.start()
    }
    override fun setStaticDisplayMode(){
        setContentView(R.layout.static_display)
        displayMode = "Static"
        startCommandService(true,3.0)
    }
    override fun setGraphDisplayMode(items: ArrayList<String>){
        if (items.size <= 0){
            Toast.makeText(this,"No Items selected for display!",Toast.LENGTH_SHORT)
        }
        else {
            setContentView(R.layout.recycler_view)
            displayMode = "Graph"
            graphRecyclerView = findViewById(R.id.recyclerView)
            graphsInView = ArrayList()
            for (name in items) {
                Log.i("graphs","$name")
                try {
                    graphsInView.add(commands[name])
                }
                catch (e: UninitializedPropertyAccessException) {                                     //Make sure commands actually exist
                    Toast.makeText(this, "No commands have been initialized!", Toast.LENGTH_SHORT).show()
                }
            }
            Log.i("graphs","ended with $graphsInView")
            recyclerAdapter = RecyclerAdapter(graphsInView)
            graphRecyclerView.adapter = recyclerAdapter
            startCommandService(true,3.0)
            var title:TextView = findViewById(R.id.graphTitle)
            if(items.size == 1){
                try{
                    title.text = "Single Graph View ${graphsInView[0]?.name}"
                }
                catch(i: IndexOutOfBoundsException){
                    Toast.makeText(this,"Values cannot be updated",Toast.LENGTH_SHORT).show()
                }
            }
            else{
                title.text = "Multi Graph View"
            }//Updates graphs every .5 seconds
        }
    }
    override fun doNothing(item:String){
        Log.i("gerg","Hello Greg")
    }
    override fun cancel(){}
    override fun onItemClick(task: String) {
        TODO("Not yet implemented")
    }
    fun startCommandService(refreshMode:Boolean,refreshTime:Double){
        serviceRunning = true
        serviceIntent = Intent(baseContext,CommandService::class.java)
        serviceIntent.putExtra("CycleUpdate",refreshMode)                                     //Set to update
        serviceIntent.putExtra("RefreshTime",refreshTime)                                     //Update every .5 seconds
        Log.i("main","Starting service")
        startService(serviceIntent)
    }
    fun updateStaticView() {
        var updatedSuccess = false
        try {
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
            commands["RPM"]?.fetchValue()
            commands["Speed"]?.fetchValue()
            commands["Throttle"]?.fetchValue()
            commands["Airflow"]?.fetchValue()
            commands["IAT"]?.fetchValue()
            commands["Coolant Temp"]?.fetchValue()
            commands["IMP"]?.fetchValue()
            commands["Timing Advance"]?.fetchValue()
            commands["Engine Uptime"]?.fetchValue()
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
        } catch (u: UninitializedPropertyAccessException) {
            if (!noDataWarned) {
                Toast.makeText(this, "Values cannot be updated", Toast.LENGTH_SHORT).show()
                noDataWarned = true
            }
            updatedSuccess = false
        } catch (n: NullPointerException) {
            if (updatedSuccess && !serviceRunning) {
                startCommandService(true, 3.0)
            } else if (!updatedSuccess) {
                stopService(serviceIntent)
                serviceRunning = false
            }
        }
        catch (i: IndexOutOfBoundsException){
            if (!noDataWarned) {
                Toast.makeText(this, "Values cannot be updated", Toast.LENGTH_SHORT).show()
                noDataWarned = true
            }
        }
    }
}

//Holds everything related to a distinct engine command. Contains methods to ask the car for data, and the
// data structures for holding this data. This object is passed into the recycler view for building graphs,
// and is queryed for displaying the static values as well.
class Command(val ex:ObdCommand,val name:String, val obdCon:ObdDeviceConnection,val context: Context){
    var graphData       = LineGraphSeries<DataPoint>()
    var graphInitTime   = Calendar.getInstance().timeInMillis
    var graphLength     = 60
    var liveData        = ""                                                                        //Twice's BAC
    var warned          = false

    //Method used for grabbing data and converting it to graphable format
    fun runCommand() = runBlocking{
        Log.i("test","RUNNING COM for $name")
        val x = ((Calendar.getInstance().timeInMillis - graphInitTime) / 1000.0)
        Log.i("graph","$x")
        var y = 1.0

        // Try to query
        try {
            val response = obdCon.run(ex)
            y = response.value.toDouble()
        }
        // Many proprietary exceptions from the OBD library
        catch(n : NonNumericResponseException){
            Log.i("graph","non numeric response\n$n")
        }
        catch(n : NoDataException){
            Log.i("graph","no Data:\n$n")
        }
        catch(s: StoppedException){
            Log.i("graph","Stopped Exec\n$s")
        }
        catch(i: IOException){
            Log.i("graph","Pipe broke\n$i")
        }
        catch(m: MisunderstoodCommandException){
            Log.i("graph","Misunderstood Command\n$m")
        }

        // Put the new datapoint on the collection to build the graph in
        // the layout
        graphData.appendData(DataPoint(x,y),true,graphLength)
    }

    // Grabs the values for "static_display" view to query later.
    fun fetchValue() = runBlocking {

        // Try to query
        try{
            val response = obdCon.run(ex)
            liveData = response.value
            Log.i("data","Recieved ${liveData}")
        }
        //Catch everything
        catch(c: ClassCastException){
            Log.i("data","Recieved CLassCast${liveData}\n ${c}")

        }
        catch(n: NumberFormatException){
            Log.i("data","Recieved NumberFormat ${liveData}\n ${n}")
        }
        catch(n: NonNumericResponseException) {
            Log.i("test","Non NumericResp \n${n}")
            if (!warned) {
                warned = true
                Toast.makeText(context,"Data collection failed",Toast.LENGTH_SHORT).show()
            }
        }
        catch(n: NoDataException) {
            Log.i("test","No Data Returned${n}")
            if (!warned) {
                warned = true
                Toast.makeText(context,"Data collection failed",Toast.LENGTH_SHORT).show()

            }
        }
        catch(m: MisunderstoodCommandException){
            Log.i("test","Misunderstood $liveData\n${m}")
        }
        catch(s: StoppedException){
            Log.i("test","No Data Returned")
            if (!warned) {
                warned = true
                Toast.makeText(context,"Data collection failed",Toast.LENGTH_SHORT).show()
            }

        }
        catch(i: IOException){
            Log.i("test","Pipe broke")
        }
    }
}