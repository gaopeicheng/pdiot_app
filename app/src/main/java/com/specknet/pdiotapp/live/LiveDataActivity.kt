package com.specknet.pdiotapp.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.live.Model.getPrediction
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.RespeckData
import com.specknet.pdiotapp.utils.ThingyLiveData
import java.io.*
import java.lang.Exception
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.timerTask
import kotlin.math.sqrt


class LiveDataActivity : AppCompatActivity() {

    // global graph variables
    lateinit var dataSet_res_accel_x: LineDataSet
    lateinit var dataSet_res_accel_y: LineDataSet
    lateinit var dataSet_res_accel_z: LineDataSet
    lateinit var dataSet_res_mag: LineDataSet

    lateinit var dataSet_thingy_accel_x: LineDataSet
    lateinit var dataSet_thingy_accel_y: LineDataSet
    lateinit var dataSet_thingy_accel_z: LineDataSet

    private lateinit var  RecordingButton: ImageButton

    var time = 0f
    lateinit var allRespeckData: LineData

    lateinit var allThingyData: LineData

    lateinit var respeckChart: LineChart
    lateinit var thingyChart: LineChart

    lateinit var predictedrespeckActivity: String
    lateinit var predictionrespeckConfidence: String

    // global broadcast receiver so we can unregister it
    lateinit var respeckLiveUpdateReceiver: BroadcastReceiver
    lateinit var thingyLiveUpdateReceiver: BroadcastReceiver
    lateinit var looperRespeck: Looper
    lateinit var looperThingy: Looper

    var sensorType = ""
    lateinit var sensorTypeSpinner: Spinner
    var thingyOn = false
    var respeckOn = false

    private var mIsRespeckRecording = false  //when respeck is recording, it's true
    private var mIsThingyRecording = false   //when thingy is recording, it's true
    private lateinit var respeckOutputData: StringBuilder
    private lateinit var thingyOutputData: StringBuilder

    var counttime = 0   //accumulate 50 data
    var respeck_data = Array(50){FloatArray(6)}  //respeck array to store 50*6 data
    var thingy_data = Array(50){FloatArray(9)}   //thingy array to store 50*9 data


    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    val filterTestThingy = IntentFilter(Constants.ACTION_THINGY_BROADCAST)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_live_data)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        var respeck_accel_x = findViewById<TextView>(R.id.breathing_rate_sec)
        var respeck_accel_y = findViewById<TextView>(R.id.breathing_rate_min)
        var respeck_accel_z = findViewById<TextView>(R.id.breathing_signal)
        var respeck_gyro_x = findViewById<TextView>(R.id.respeck_gyro_x)
        var respeck_gyro_y = findViewById<TextView>(R.id.respeck_gyro_y)
        var respeck_gyro_z = findViewById<TextView>(R.id.respeck_gyro_z)

        var thingy_accel = findViewById<TextView>(R.id.thingy_accel_data)
        var thingy_gyro = findViewById<TextView>(R.id.thingy_gyro_data)
        var thingy_mag = findViewById<TextView>(R.id.thingy_mag_data)
        thingyOutputData = StringBuilder()


        var Respeckprediction = findViewById<TextView>(R.id.prediction)
        var Respeckconfidence = findViewById<TextView>(R.id.confidence)

        setupSpinner()

        setupButton()

        setupCharts()

        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)



                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    val groy_x = liveData.gyro.x
                    val groy_y = liveData.gyro.y
                    val groy_z = liveData.gyro.z

//                    val mag = sqrt((x*x + y*y + z*z).toDouble())

//                    val data =
//                        RespeckData(
//                            timestamp = 0L,
//                            accel_x = x,
//                            accel_y = y,
//                            accel_z = z,
//                            accel_mag = mag.toFloat(),
//                            breathingSignal = 0f
//                        )

         //           val predictionWithConfidence = getPrediction(data) //come from the model

//                    val predictionWithConfidence = getPrediction()
                    predictedrespeckActivity = "Sitting/Standing"
//                    predictionrespeckConfidence = (30..40).shuffled().last().toString()

                    if (mIsRespeckRecording) {
                        val output = liveData.phoneTimestamp.toString() + "," +
                                liveData.accelX + "," + liveData.accelY + "," + liveData.accelZ + "," +
                                liveData.gyro.x + "," + liveData.gyro.y + "," + liveData.gyro.z + "\n"

                        if(counttime<50){   //store 50*6 data into the respeck array
                            respeck_data[counttime][0] = liveData.accelX
                            respeck_data[counttime][1] = liveData.accelY
                            respeck_data[counttime][2] = liveData.accelZ
                            respeck_data[counttime][3] = liveData.gyro.x
                            respeck_data[counttime][4] = liveData.gyro.y
                            respeck_data[counttime][5] = liveData.gyro.z
                            counttime++
                            count()
                        }
//                        respeckOutputData.append(output)
                    }

                    runOnUiThread {    //real-time data show on the ui
                        respeck_accel_x.text = "accel_x = " + x.toString()
                        respeck_accel_y.text = "accel_y = " + y.toString()
                        respeck_accel_z.text = "accel_z = " + z.toString()
                        respeck_gyro_x.text = "gyro_x = " + groy_x.toString()
                        respeck_gyro_y.text = "gyro_y = " + groy_y.toString()
                        respeck_gyro_z.text = "gyro_z = " + groy_z.toString()
                        Respeckprediction.text = "Activity: " + predictedrespeckActivity
                    }

                    time += 1
                    updateGraph("respeck", x, y, z)

                    respeckOn = true    //the respeck bluetooth is on

                }
            }
        }

        // register receiver on another thread
        val handlerThreadRespeck = HandlerThread("bgThreadRespeckLive")
        handlerThreadRespeck.start()
        looperRespeck = handlerThreadRespeck.looper
        val handlerRespeck = Handler(looperRespeck)
        this.registerReceiver(respeckLiveUpdateReceiver, filterTestRespeck, null, handlerRespeck)

        // set up the broadcast receiver
        thingyLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_THINGY_BROADCAST) {

                    val liveData =
                        intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    Log.d("Live", "onReceive: liveData = " + liveData)

                    // get all relevant intent contents
                    val x = liveData.accelX
                    val y = liveData.accelY
                    val z = liveData.accelZ

                    if (mIsThingyRecording) {
                        val output = liveData.phoneTimestamp.toString() + "," +
                                liveData.accelX + "," + liveData.accelY + "," + liveData.accelZ + "," +
                                liveData.gyro.x + "," + liveData.gyro.y + "," + liveData.gyro.z + "," +
                                liveData.mag.x + "," + liveData.mag.y + "," + liveData.mag.z + "\n"

                        if(counttime<50){   //store 50*9 into thing data array
                            thingy_data[counttime][0] = liveData.accelX
                            thingy_data[counttime][1] = liveData.accelY
                            thingy_data[counttime][2] = liveData.accelZ
                            thingy_data[counttime][3] = liveData.gyro.x
                            thingy_data[counttime][4] = liveData.gyro.y
                            thingy_data[counttime][5] = liveData.gyro.z
                            thingy_data[counttime][6] = liveData.mag.x
                            thingy_data[counttime][7] = liveData.mag.y
                            thingy_data[counttime][8] = liveData.mag.z
                            counttime++
                            count()
                        }

                        thingyOutputData.append(output)
                    }

                    runOnUiThread {
                        thingy_accel.text = "accel =("+ liveData.accelX+ liveData.accelY+ liveData.accelZ+")"
                        thingy_gyro.text = "gyro =("+ liveData.gyro.x+ liveData.gyro.y+ liveData.gyro.z+")"
                        thingy_mag.text = "mag =("+ liveData.mag.x+ liveData.mag.y+ liveData.mag.z+")"
                    }

                    time += 1
                    updateGraph("thingy", x, y, z)

                    thingyOn = true

                }
            }
        }

        // register receiver on another thread
        val handlerThreadThingy = HandlerThread("bgThreadThingyLive")
        handlerThreadThingy.start()
        looperThingy = handlerThreadThingy.looper
        val handlerThingy = Handler(looperThingy)
        this.registerReceiver(thingyLiveUpdateReceiver, filterTestThingy, null, handlerThingy)

    }


    fun setupCharts() {
        respeckChart = findViewById(R.id.respeck_chart)
        thingyChart = findViewById(R.id.thingy_chart)

        // Respeck

        time = 0f
        val entries_res_accel_x = ArrayList<Entry>()
        val entries_res_accel_y = ArrayList<Entry>()
        val entries_res_accel_z = ArrayList<Entry>()

        dataSet_res_accel_x = LineDataSet(entries_res_accel_x, "Accel X")
        dataSet_res_accel_y = LineDataSet(entries_res_accel_y, "Accel Y")
        dataSet_res_accel_z = LineDataSet(entries_res_accel_z, "Accel Z")

        dataSet_res_accel_x.setDrawCircles(false)
        dataSet_res_accel_y.setDrawCircles(false)
        dataSet_res_accel_z.setDrawCircles(false)

        dataSet_res_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_res_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_res_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsRes = ArrayList<ILineDataSet>()
        dataSetsRes.add(dataSet_res_accel_x)
        dataSetsRes.add(dataSet_res_accel_y)
        dataSetsRes.add(dataSet_res_accel_z)

        allRespeckData = LineData(dataSetsRes)
        respeckChart.data = allRespeckData
        respeckChart.invalidate()

        // Thingy

        time = 0f
        val entries_thingy_accel_x = ArrayList<Entry>()
        val entries_thingy_accel_y = ArrayList<Entry>()
        val entries_thingy_accel_z = ArrayList<Entry>()

        dataSet_thingy_accel_x = LineDataSet(entries_thingy_accel_x, "Accel X")
        dataSet_thingy_accel_y = LineDataSet(entries_thingy_accel_y, "Accel Y")
        dataSet_thingy_accel_z = LineDataSet(entries_thingy_accel_z, "Accel Z")

        dataSet_thingy_accel_x.setDrawCircles(false)
        dataSet_thingy_accel_y.setDrawCircles(false)
        dataSet_thingy_accel_z.setDrawCircles(false)

        dataSet_thingy_accel_x.setColor(
            ContextCompat.getColor(
                this,
                R.color.red
            )
        )
        dataSet_thingy_accel_y.setColor(
            ContextCompat.getColor(
                this,
                R.color.green
            )
        )
        dataSet_thingy_accel_z.setColor(
            ContextCompat.getColor(
                this,
                R.color.blue
            )
        )

        val dataSetsThingy = ArrayList<ILineDataSet>()
        dataSetsThingy.add(dataSet_thingy_accel_x)
        dataSetsThingy.add(dataSet_thingy_accel_y)
        dataSetsThingy.add(dataSet_thingy_accel_z)

        allThingyData = LineData(dataSetsThingy)
        thingyChart.data = allThingyData
        thingyChart.invalidate()
    }

    fun updateGraph(graph: String, x: Float, y: Float, z: Float) {
        // take the first element from the queue
        // and update the graph with it
        if (graph == "respeck") {
            dataSet_res_accel_x.addEntry(Entry(time, x))
            dataSet_res_accel_y.addEntry(Entry(time, y))
            dataSet_res_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allRespeckData.notifyDataChanged()
                respeckChart.notifyDataSetChanged()
                respeckChart.invalidate()
                respeckChart.setVisibleXRangeMaximum(150f)
                respeckChart.moveViewToX(respeckChart.lowestVisibleX + 40)
            }
        } else if (graph == "thingy") {
            dataSet_thingy_accel_x.addEntry(Entry(time, x))
            dataSet_thingy_accel_y.addEntry(Entry(time, y))
            dataSet_thingy_accel_z.addEntry(Entry(time, z))

            runOnUiThread {
                allThingyData.notifyDataChanged()
                thingyChart.notifyDataSetChanged()
                thingyChart.invalidate()
                thingyChart.setVisibleXRangeMaximum(150f)
                thingyChart.moveViewToX(thingyChart.lowestVisibleX + 40)
            }
        }

    }

    private fun disableView(view: View) {
        view.isClickable = false
        view.isEnabled = false
    }

    private fun enableView(view: View) {
        view.isClickable = true
        view.isEnabled = true
    }

    private fun setupSpinner(){
        sensorTypeSpinner = findViewById(R.id.sensor_type_spinner)

        ArrayAdapter.createFromResource(
            this,
            R.array.sensor_type_array,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            sensorTypeSpinner.adapter = adapter
        }

        sensorTypeSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, viwq: View, position: Int, id: Long) {
                val selectedItem = parent.getItemAtPosition(position).toString()
                sensorType = selectedItem
            }

            override fun onNothingSelected(p0: AdapterView<*>?) {
                sensorType = "Respeck"
            }
        }

    }

    private fun setupButton(){
        RecordingButton = findViewById(R.id.start_button)


        RecordingButton.setOnClickListener {

            getInputs()

            if (sensorType == "Respeck" && !respeckOn) {
                Toast.makeText(this, "Respeck is not on! Check connection.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            if (sensorType == "Thingy" && !thingyOn) {
                Toast.makeText(this, "Thingy is not on! Check connection.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(this, "Starting recording", Toast.LENGTH_SHORT).show()

//            disableView(RecordingButton)    //
//
//            disableView(sensorTypeSpinner)

            startRecording()
        }

    }

    private fun startRecording() {

        if (sensorType.equals("Thingy")) {
            mIsThingyRecording = true    
            mIsRespeckRecording = false
        }
        else {
            mIsRespeckRecording = true
            mIsThingyRecording = false
        }
    }

    private fun count(){   //once the thingy or respeck data array is full ,then stop recording
        if(counttime == 49){
            Toast.makeText(this, "Stop recording", Toast.LENGTH_SHORT).show()
            mIsThingyRecording = false   //stop the recording
            mIsRespeckRecording = false  //stop the recording
//            enableView(RecordingButton)
//            enableView(sensorTypeSpinner)
            counttime = 0;
            for(i in 0 until 50)
                for(j in 0 until 6) {
                    Log.v("datarecord:", respeck_data[i][j].toString())
                }
        }
    }

    private fun getInputs(){
        sensorType = sensorTypeSpinner.selectedItem.toString()
    }

    private fun saveRecording() {    //to produce a csv file (potentially retain in case using in the future)
        val currentTime = System.currentTimeMillis()
        var formattedDate = ""
        try {
            formattedDate = SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.UK).format(Date())
        } catch (e: Exception) {
            formattedDate = currentTime.toString()
        }
        val filename = "${sensorType}_${formattedDate}.csv" // TODO format this to human readable

        val file = File(getExternalFilesDir(null), filename)

        val dataWriter: BufferedWriter

        // Create file for current day and append header, if it doesn't exist yet
        try {
            val exists = file.exists()
            dataWriter = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true)))

            if (!exists) {

                // the header columns in here
                dataWriter.append("# Sensor type: $sensorType").append("\n")

                if (sensorType.equals("Thingy")) {
                    dataWriter.write(Constants.RECORDING_CSV_HEADER_THINGY)
                }
                else {
                    dataWriter.write(Constants.RECORDING_CSV_HEADER_RESPECK)
                }
                dataWriter.newLine()
                dataWriter.flush()
            }

            if (sensorType.equals("Thingy")) {
                if (thingyOutputData.isNotEmpty()) {
                    dataWriter.write(thingyOutputData.toString())
                    dataWriter.flush()

                }
            }
            else {
                if (respeckOutputData.isNotEmpty()) {
                    dataWriter.write(respeckOutputData.toString())
                    dataWriter.flush()
                }
            }

            dataWriter.close()

            respeckOutputData = StringBuilder()
            thingyOutputData = StringBuilder()

            Toast.makeText(this, "Recording saved!", Toast.LENGTH_SHORT).show()
        }
        catch (e: IOException) {
            Toast.makeText(this, "Error while saving recording!", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(respeckLiveUpdateReceiver)
        unregisterReceiver(thingyLiveUpdateReceiver)
        looperRespeck.quit()
        looperThingy.quit()
        if (mIsThingyRecording || mIsRespeckRecording) {
            saveRecording()
        }
    }
}
