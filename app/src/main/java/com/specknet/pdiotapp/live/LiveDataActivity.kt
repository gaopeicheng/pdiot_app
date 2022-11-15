package com.specknet.pdiotapp.live

//import com.specknet.pdiotapp.live.Model.getPrediction

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.specknet.pdiotapp.R
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.ThingyLiveData
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*


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

    val test: Array<DoubleArray> = arrayOf(doubleArrayOf(0.125977,-1.205872,0.143982,16.125,6.1875,9.515625),
        doubleArrayOf(0.104004,-0.896057,0.059509,15.46875,6.8125,18.0625),
        doubleArrayOf(0.087402,-1.044495,0.060974,1.953125,-5.453125,15.625),
        doubleArrayOf(0.058594,-0.935364,0.096619,-4.015625,-5.15625,10.71875),
        doubleArrayOf(-0.037842,-0.858215,0.167419,-9.84375,-7.828125,9.671875),
        doubleArrayOf(0.006348,-0.807434,0.12738,-9.265625,-9.765625,11.359375),
        doubleArrayOf(0.013184,-0.736633,0.199158,-1.75,-7.796875,10.46875),
        doubleArrayOf(0.028076,-0.747864,0.213562,-1.9375,-10.53125,11.4375),
        doubleArrayOf(0.0271,-0.777405,0.240662,-0.125,-8.34375,10.859375),
        doubleArrayOf(-0.008057,-0.82843,0.262634,2.109375,-8.71875,10.328125),
        doubleArrayOf(-0.007812,-0.908752,0.271667,1.390625,-7.96875,10.0625),
        doubleArrayOf(0.084961,-0.996155,0.277039,-0.8125,-8.5,7.828125),
        doubleArrayOf(0.095215,-1.087219,0.388611,0.96875,-7.15625,1.4375),
        doubleArrayOf(0.079102,-1.175598,0.392761,-3.375,-8.078125,-5.90625),
        doubleArrayOf(0.143311,-1.538635,0.216492,-3.3125,-20.328125,-8.9375),
        doubleArrayOf(-0.118164,-1.346008,0.298767,6.65625,0.953125,-8.53125),
        doubleArrayOf(-0.118164,-0.934387,0.105652,3.140625,21.109375,-9.265625),
        doubleArrayOf(-0.188721,-0.824768,-0.030334,2.890625,21.328125,-11.171875),
        doubleArrayOf(0.058838,-0.960266,0.240417,9.75,7.46875,-12.015625),
        doubleArrayOf(-0.233154,-0.954651,0.112244,1.53125,15.625,-14.390625),
        doubleArrayOf(-0.045166,-0.745911,0.163757,0.890625,15.609375,-9.96875),
        doubleArrayOf(-0.092041,-0.736877,0.184265,6.453125,13.078125,-9.484375),
        doubleArrayOf(-0.106201,-0.727844,0.145447,6.109375,14.4375,-8.96875),
        doubleArrayOf(-0.12915,-0.759827,0.15448,4.484375,14.21875,-8.03125),
        doubleArrayOf(-0.131348,-0.804993,0.208191,5.25,12.390625,-6.953125),
        doubleArrayOf(-0.134277,-0.885803,0.236267,5.890625,9.4375,-4.765625),
        doubleArrayOf(-0.186523,-1.01886,0.2453,5.984375,10.78125,-3.25),
        doubleArrayOf(-0.200439,-1.087708,0.271423,3.953125,12.390625,-2.515625),
        doubleArrayOf(-0.223877,-1.227112,0.268982,3.359375,9.546875,-2.28125),
        doubleArrayOf(-0.213135,-1.325012,0.301941,-2.09375,7.78125,3.203125),
        doubleArrayOf(-0.238525,-1.344299,0.283386,-1.640625,5.84375,8.625),
        doubleArrayOf(-0.186035,-1.408264,0.060974,1.90625,0.84375,8.609375),
        doubleArrayOf(0.186279,-1.002014,-0.052795,12.046875,3.703125,16.25),
        doubleArrayOf(0.198486,-0.872375,0.020935,16.890625,-6.125,20.09375),
        doubleArrayOf(0.12207,-0.919006,0.009949,0.484375,-19.859375,19.09375),
        doubleArrayOf(-0.028076,-0.963684,0.26532,-5.0,-17.296875,9.828125),
        doubleArrayOf(-0.118408,-0.851624,0.14032,-16.15625,-18.796875,6.078125),
        doubleArrayOf(-0.200928,-0.698303,0.149109,-14.53125,-4.25,2.765625),
        doubleArrayOf(-0.020996,-0.70221,0.212097,-10.8125,-5.921875,0.59375),
        doubleArrayOf(-0.005371,-0.722229,0.237976,-9.265625,-8.96875,1.03125),
        doubleArrayOf(-0.078857,-0.742004,0.261414,-10.78125,-6.0,-1.828125),
        doubleArrayOf(-0.041748,-0.790588,0.273865,-5.078125,-3.515625,-2.046875),
        doubleArrayOf(-0.058105,-0.893616,0.396423,-3.8125,-2.359375,0.078125),
        doubleArrayOf(-0.113525,-0.947083,0.452332,0.875,2.96875,-2.53125),
        doubleArrayOf(-0.074707,-1.069885,0.481628,0.734375,4.125,-5.828125),
        doubleArrayOf(-0.15332,-1.061584,0.524109,6.4375,13.75,-12.09375),
        doubleArrayOf(-0.174072,-1.260803,0.297058,4.375,10.421875,-9.84375),
        doubleArrayOf(-0.088867,-1.449036,0.304138,6.140625,8.1875,-12.828125),
        doubleArrayOf(-0.287109,-1.46051,-0.020325,6.203125,29.921875,-15.8125),
        doubleArrayOf(-0.136719,-0.990295,-0.211243,7.484375,34.171875,-12.234375))


    private val nnApiDelegate by lazy  {
        NnApiDelegate()
    }

    private val REStflite by lazy {
        Interpreter(
            FileUtil.loadMappedFile(this, RES_MODEL_PATH),
            Interpreter.Options().addDelegate(nnApiDelegate))
    }

    private val THItflite by lazy {
        Interpreter(
            FileUtil.loadMappedFile(this, THI_MODEL_PATH),
            Interpreter.Options().addDelegate(nnApiDelegate))
    }

    private val ALLtflite by lazy {
        Interpreter(
            FileUtil.loadMappedFile(this, ALL_MODEL_PATH),
            Interpreter.Options().addDelegate(nnApiDelegate))
    }


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

    private fun printOutput(temp:Array<FloatArray> ): String {
        var s: String = ""
        for(i in 0 until 13){
            s = s+ temp[0][i] +" "

        }
        return s
    }

    private fun setupButton(){
        RecordingButton = findViewById(R.id.start_button)

        val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(50*6*4)
        byteBuffer.order(ByteOrder.nativeOrder())
        for (i in 0 until 50) {
            for (j in test[i].indices) {
                byteBuffer.putFloat(test[i][j].toFloat())
            }
        }

        val output = Array(1){FloatArray(13){0f}}
        Log.v("Init output and print", "init" + output[0][0])
//        val outputbuffer = ByteBuffer.allocateDirect(14*4)
        REStflite.run(byteBuffer,output)
        var s: String = ""
        for(i in 0 until 13){
            s = s+ output[0][i] +" "
        }
        Log.v("predict and the output changed", "prediction " + s)

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
            if(mIsThingyRecording==false&&mIsRespeckRecording==false){
                // Havn t test in the real sensor yet, but the test data shows the program s logic is correct.
                val RESbyteBuffer: ByteBuffer = ByteBuffer.allocateDirect(50*6*4)
                byteBuffer.order(ByteOrder.nativeOrder())
                for (i in 0 until 50) {
                    for (j in test[i].indices) {
                        RESbyteBuffer.putFloat(respeck_data[i][j].toFloat())
                    }
                }
                val RESoutput = Array(1){FloatArray(13){0f}}
                REStflite.run(RESbyteBuffer,RESoutput)

                val RESstring = printOutput(RESoutput)

                val THIbyteBuffer: ByteBuffer = ByteBuffer.allocateDirect(50*9*4)
                byteBuffer.order(ByteOrder.nativeOrder())
                for (i in 0 until 50) {
                    for (j in test[i].indices) {
                        THIbyteBuffer.putFloat(thingy_data[i][j].toFloat())
                    }
                }
                val THIoutput = Array(1){FloatArray(13){0f}}
                THItflite.run(THIbyteBuffer,THIoutput)

                val THIstring = printOutput(THIoutput)

                Log.i("RES",RESstring)
                Log.i("THI",THIstring)


            }
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

    companion object {
//        private val TAG = CameraActivity::class.java.simpleName

        private const val ACCURACY_THRESHOLD = 0.5f
        private const val RES_MODEL_PATH = "respeck_model.tflite"
        private const val THI_MODEL_PATH = "thingy_model.tflite"
        private const val ALL_MODEL_PATH = "all_model.tflite"
        private const val LABELS_PATH = "labels.txt"
    }
}
