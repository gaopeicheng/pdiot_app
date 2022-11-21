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
    private lateinit var start_stop_Button: Button
    private var startflag = false

    var time = 0f
    lateinit var allRespeckData: LineData

    lateinit var allThingyData: LineData

    lateinit var respeckChart: LineChart
    lateinit var thingyChart: LineChart

    var RES_pred_act: String =""
    var RES_pred_con: String =""

    var THI_pred_act: String =""
    var THI_pred_con: String =""

    var ALL_pred_act: String =""
    var ALL_pred_con: String =""

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

    var countTimeRepseck = 0   //accumulate 50 data
    var countTimeThingy = 0

    var countTimeAllThingy = 0
    var countTimeAllRespeck = 0
    var countTimeAll = 0
    var lockThingy = 0
    var lockRespeck = 1

    var respeck_data = Array(50){FloatArray(6)}  //respeck array to store 50*6 data
    var thingy_data = Array(50){FloatArray(9)}   //thingy array to store 50*9 data
    var all_data = Array(50){FloatArray(15)}      //all array to store 50*15 data

    var averageRespeckIndex = Array(1){FloatArray(13){0f}}
    var averageRespeckConfidence = Array(1){FloatArray(13){0f}}
    var roundRespeck = 0
    var averageThingyIndex = Array(1){FloatArray(13){0f}}
    var averageThingyConfidence = Array(1){FloatArray(13){0f}}
    var roundThingy = 0
    var averageAllIndex = Array(1){FloatArray(13){0f}}
    var averageAllConfidence = Array(1){FloatArray(13){0f}}
    var roundAll = 0

    val filterTestRespeck = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    val filterTestThingy = IntentFilter(Constants.ACTION_THINGY_BROADCAST)

    var STAT_res_1_act =""
    var STAT_res_1_con =""

    var STAT_res_2_act =""
    var STAT_res_2_con =""

    var STAT_thi_1_act =""
    var STAT_thi_1_con =""

    var STAT_thi_2_act =""
    var STAT_thi_2_con =""

    var STAT_all_1_act =""
    var STAT_all_1_con =""

    var STAT_all_2_act =""
    var STAT_all_2_con =""

    val labelsMap = mapOf<Int,String>(0 to "0 Sitting",   // need to match the network output
        1 to "1 Walking at normal speed",
        2 to "2 Lying down on back",
        3 to "4 Sitting bent forward",
        4 to "5 Sitting bent backward",
        5 to "6 Lying down right",
        6 to "7 Lying down left",
        7 to "8 Lying down on stomach",
        8 to "11 Running",
        9 to "12 Climbing stairs",
        10 to "13 Descending stairs",
        11 to "31 Desk work",
        12 to "100 Standing")

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

        var RES_Act = findViewById<TextView>(R.id.RES_activity)
        var RES_Con = findViewById<TextView>(R.id.RES_confidence)

        var THI_Act = findViewById<TextView>(R.id.THI_activity)
        var THI_Con = findViewById<TextView>(R.id.THI_confidence)

        var ALL_Act = findViewById<TextView>(R.id.ALL_activity)
        var ALL_Con = findViewById<TextView>(R.id.ALL_confidence)

        var STAT_res_fir_act = findViewById<TextView>(R.id.STAT_res_first_content)
        var STAT_res_fir_con = findViewById<TextView>(R.id.STAT_res_con_first_content)

        var STAT_res_sec_act = findViewById<TextView>(R.id.STAT_res_second_content)
        var STAT_res_sec_con = findViewById<TextView>(R.id.STAT_res_con_second_content)

        var STAT_thi_fir_act = findViewById<TextView>(R.id.STAT_thi_first_content)
        var STAT_thi_fir_con = findViewById<TextView>(R.id.STAT_thi_con_first_content)

        var STAT_thi_sec_act = findViewById<TextView>(R.id.STAT_thi_second_content)
        var STAT_thi_sec_con = findViewById<TextView>(R.id.STAT_thi_con_second_content)

        var STAT_all_fir_act = findViewById<TextView>(R.id.STAT_all_first_content)
        var STAT_all_fir_con = findViewById<TextView>(R.id.STAT_all_con_first_content)

        var STAT_all_sec_act = findViewById<TextView>(R.id.STAT_all_second_content)
        var STAT_all_sec_con = findViewById<TextView>(R.id.STAT_all_con_second_content)

        start_stop_Button = findViewById(R.id.startstopButton)

        start_stop_Button.setOnClickListener {
            if(startflag == false){
                start_stop_Button.text = "Stop"
                startflag = true
            }else{
                start_stop_Button.text = "Start"
                startflag = false
            }
        }

//        setupSpinner()

//        setupButton()
//
//        setupCharts()

//        val byteBuffer: ByteBuffer = ByteBuffer.allocateDirect(50*6*4)
//        byteBuffer.order(ByteOrder.nativeOrder())
//        for (i in 0 until 50) {
//            for (j in test[i].indices) {
//                byteBuffer.putFloat(test[i][j].toFloat())
//            }
//        }
//        val output = Array(1){FloatArray(13){0f}}
//        Log.v("Init output and print", "init" + output[0][0])
//        val outputbuffer = ByteBuffer.allocateDirect(14*4)
//        REStflite.run(byteBuffer,output)
//        var s: String = printOutput(output)
//        Log.v("predict and the output changed", "prediction " + s)
//        var maxIdx: Int = getMaxIdx(output)
//        var label: String = labelsMap.getValue(maxIdx)
//        Log.v("label", "label " + label)
//        Log.v("Confidence", "confi " + output[0][maxIdx])
//
//
//        Respeckprediction.text  =  "Activity: " + label
//        Respeckconfidence.text = output[0][maxIdx].toString()

        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST && startflag) {

                    val respeckLiveData =
                        intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    Log.d("Live", "onReceive: liveData = " + respeckLiveData)

                    // get all relevant intent contents
                    val xRespeck = respeckLiveData.accelX
                    val yRespeck = respeckLiveData.accelY
                    val zRespeck = respeckLiveData.accelZ

                    val groyXRespeck = respeckLiveData.gyro.x
                    val groyYRespeck = respeckLiveData.gyro.y
                    val groyZRespeck = respeckLiveData.gyro.z

                    if(countTimeRepseck<50){   //store 50*6 data into the respeck array
                        respeck_data[countTimeRepseck][0] = respeckLiveData.accelX
                        respeck_data[countTimeRepseck][1] = respeckLiveData.accelY
                        respeck_data[countTimeRepseck][2] = respeckLiveData.accelZ
                        respeck_data[countTimeRepseck][3] = respeckLiveData.gyro.x
                        respeck_data[countTimeRepseck][4] = respeckLiveData.gyro.y
                        respeck_data[countTimeRepseck][5] = respeckLiveData.gyro.z

                        countTimeRepseck++
                        if (countTimeRepseck == 50) {
                            countTimeRepseck = 0
                        }
                    }

                    var RESbyteBuffer: ByteBuffer = ByteBuffer.allocateDirect(50 * 6 * 4)
                    RESbyteBuffer.order(ByteOrder.nativeOrder())
                    for (i in 0 until 50) {
                        for (j in 0 until 6) {
                            RESbyteBuffer.putFloat(respeck_data[i][j].toFloat())
                        }
                    }
                    var RESoutput = Array(1) { FloatArray(13) { 0f } }
                    REStflite.run(RESbyteBuffer, RESoutput)
                    var maxIdxRespeck = getMaxIdx(RESoutput)

                    averageRespeckIndex[0][maxIdxRespeck] =
                        averageRespeckIndex[0][maxIdxRespeck] + 1
                    averageRespeckConfidence[0][maxIdxRespeck] =
                        averageRespeckConfidence[0][maxIdxRespeck] + RESoutput[0][maxIdxRespeck]
                    roundRespeck++

                    if (roundRespeck == 10) {
                        roundRespeck = 0

                        var maxAverageIdxRespeck = getMaxIdx(averageRespeckIndex)
                        RES_pred_act = labelsMap.getValue(maxAverageIdxRespeck)

                        var averageRespeckCount = averageRespeckIndex[0][maxAverageIdxRespeck]
                        var respeckConfidence =
                            averageRespeckConfidence[0][maxAverageIdxRespeck] / averageRespeckCount
                        RES_pred_con = respeckConfidence.toString()

                        averageRespeckIndex = Array(1) { FloatArray(13) { 0f } }
                        averageRespeckConfidence = Array(1) { FloatArray(13) { 0f } }

                    }

                    runOnUiThread {    //real-time data show on the ui
                        respeck_accel_x.text = "accel_x = " + xRespeck.toString()
                        respeck_accel_y.text = "accel_y = " + yRespeck.toString()
                        respeck_accel_z.text = "accel_z = " + zRespeck.toString()
                        respeck_gyro_x.text = "gyro_x = " + groyXRespeck.toString()
                        respeck_gyro_y.text = "gyro_y = " + groyYRespeck.toString()
                        respeck_gyro_z.text = "gyro_z = " + groyZRespeck.toString()

                        RES_Act.text = "Activity: " + RES_pred_act
                        RES_Con.text =  RES_pred_con

                    }

//                    time += 1
//                    updateGraph("respeck", x, y, z)

                    respeckOn = true    //the respeck bluetooth is on

//                    time += 1
//                    updateGraph("thingy", x, y, z)

                    while (lockRespeck <= 0) {
                        if (lockRespeck > 0) break
                    }
                    lockRespeck--

                    if (countTimeAllRespeck < 50) {
                        all_data[countTimeAllRespeck][9] = respeckLiveData.accelX
                        all_data[countTimeAllRespeck][10] = respeckLiveData.accelY
                        all_data[countTimeAllRespeck][11] = respeckLiveData.accelZ
                        all_data[countTimeAllRespeck][12] = respeckLiveData.gyro.x
                        all_data[countTimeAllRespeck][13] = respeckLiveData.gyro.y
                        all_data[countTimeAllRespeck][14] = respeckLiveData.gyro.z

                        countTimeAllRespeck++
                        if (countTimeAllRespeck == 50) {
                            countTimeAllRespeck = 0
                        }
                    }

                    //countTimeAll++
                    var allByteBuffer: ByteBuffer = ByteBuffer.allocateDirect(50 * 15 * 4)
                    allByteBuffer.order(ByteOrder.nativeOrder())
                    for (i in 0 until 50) {
                        for (j in all_data[i].indices) {
                            allByteBuffer.putFloat(all_data[i][j].toFloat())
                        }
                    }
                    var allOutput = Array(1) { FloatArray(13) { 0f } }
                    ALLtflite.run(allByteBuffer, allOutput)
                    var maxIdxAll = getMaxIdx(allOutput)

                    averageAllIndex[0][maxIdxAll] = averageAllIndex[0][maxIdxAll] + 1
                    averageAllConfidence[0][maxIdxAll] =
                        averageAllConfidence[0][maxIdxAll] + allOutput[0][maxIdxAll]
                    roundAll++

                    if (roundAll == 10) {
                        roundAll = 0

                        var maxAverageIdxAll = getMaxIdx(averageAllIndex)
                        ALL_pred_act = labelsMap.getValue(maxAverageIdxAll)

                        var averageAllCount = averageAllIndex[0][maxAverageIdxAll]
                        var allConfidence =
                            averageAllConfidence[0][maxAverageIdxAll] / averageAllCount
                        ALL_pred_con = allConfidence.toString()

                        averageAllIndex = Array(1) { FloatArray(13) { 0f } }
                        averageAllConfidence = Array(1) { FloatArray(13) { 0f } }

                    }
                    runOnUiThread {
                        ALL_Act.text = "Activity: " + ALL_pred_act
                        ALL_Con.text =  ALL_pred_con

                        // Statistc? maybe

                    }
                    lockThingy++
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

                if (action == Constants.ACTION_THINGY_BROADCAST && startflag) {

                    val thingyLiveData =
                        intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    Log.d("Live", "onReceive: liveData = $thingyLiveData")

                    // get all relevant intent contents
                    val xThingy = thingyLiveData.accelX
                    val yThingy = thingyLiveData.accelY
                    val zThingy = thingyLiveData.accelZ

                    if(countTimeThingy<50){   //store 50*9 data into the thingy array

                        thingy_data[countTimeThingy][0] = thingyLiveData.accelX
                        thingy_data[countTimeThingy][1] = thingyLiveData.accelY
                        thingy_data[countTimeThingy][2] = thingyLiveData.accelZ
                        thingy_data[countTimeThingy][3] = thingyLiveData.gyro.x
                        thingy_data[countTimeThingy][4] = thingyLiveData.gyro.y
                        thingy_data[countTimeThingy][5] = thingyLiveData.gyro.z
                        thingy_data[countTimeThingy][6] = thingyLiveData.mag.x
                        thingy_data[countTimeThingy][7] = thingyLiveData.mag.y
                        thingy_data[countTimeThingy][8] = thingyLiveData.mag.z

                        countTimeThingy++
                        if (countTimeThingy == 50) {
                            countTimeThingy = 0
                        }
                    }
                    var thingyByteBuffer: ByteBuffer = ByteBuffer.allocateDirect(50 * 9 * 4)
                    thingyByteBuffer.order(ByteOrder.nativeOrder())
                    for (i in 0 until 50) {
                        for (j in 0 until 9) {
                            thingyByteBuffer.putFloat(thingy_data[i][j].toFloat())
                        }
                    }
                    var thingyOutput = Array(1) { FloatArray(13) { 0f } }
                    THItflite.run(thingyByteBuffer, thingyOutput)
                    var maxIdxThingy = getMaxIdx(thingyOutput)

                    averageThingyIndex[0][maxIdxThingy] =
                        averageThingyIndex[0][maxIdxThingy] + 1
                    averageThingyConfidence[0][maxIdxThingy] =
                        averageThingyConfidence[0][maxIdxThingy] + thingyOutput[0][maxIdxThingy]
                    roundThingy++

                    if (roundThingy == 10) {
                        roundThingy = 0

                        var maxAverageIdxThingy = getMaxIdx(averageThingyIndex)
                        THI_pred_act = labelsMap.getValue(maxAverageIdxThingy)

                        var averageThingyCount = averageThingyIndex[0][maxAverageIdxThingy]
                        var thingyconfidence =
                            averageThingyConfidence[0][maxAverageIdxThingy] / averageThingyCount
                        THI_pred_con = thingyconfidence.toString()

                        averageThingyIndex = Array(1) { FloatArray(13) { 0f } }
                        averageThingyConfidence = Array(1) { FloatArray(13) { 0f } }

                    }

                    runOnUiThread {
                        thingy_accel.text =
                            "accel =(" + xThingy.toString() + yThingy.toString() + zThingy.toString() + ")"
                        thingy_gyro.text =
                            "gyro =(" + thingyLiveData.gyro.x + thingyLiveData.gyro.y + thingyLiveData.gyro.z + ")"
                        thingy_mag.text =
                            "mag =(" + thingyLiveData.mag.x + thingyLiveData.mag.y + thingyLiveData.mag.z + ")"

                        THI_Act.text = "Activity: " + THI_pred_act
                        THI_Con.text = THI_pred_con
                    }
                    thingyOn = true

                    while (lockThingy <= 0) {
                        if (lockThingy > 0) break
                    }
                    lockThingy--

                    if (countTimeAllThingy < 50) {
                        all_data[countTimeAllThingy][0] = thingyLiveData.accelX
                        all_data[countTimeAllThingy][1] = thingyLiveData.accelY
                        all_data[countTimeAllThingy][2] = thingyLiveData.accelZ
                        all_data[countTimeAllThingy][3] = thingyLiveData.gyro.x
                        all_data[countTimeAllThingy][4] = thingyLiveData.gyro.y
                        all_data[countTimeAllThingy][5] = thingyLiveData.gyro.z
                        all_data[countTimeAllThingy][6] = thingyLiveData.mag.x
                        all_data[countTimeAllThingy][7] = thingyLiveData.mag.y
                        all_data[countTimeAllThingy][8] = thingyLiveData.mag.z

                        countTimeAllThingy++
                        if (countTimeAllThingy == 50) {
                            countTimeAllThingy = 0
                        }

                    }

                    var allByteBuffer: ByteBuffer = ByteBuffer.allocateDirect(50 * 15 * 4)
                    allByteBuffer.order(ByteOrder.nativeOrder())
                    for (i in 0 until 50) {
                        for (j in all_data[i].indices) {
                            allByteBuffer.putFloat(all_data[i][j].toFloat())
                        }
                    }
                    var allOutput = Array(1) { FloatArray(13) { 0f } }
                    ALLtflite.run(allByteBuffer, allOutput)
                    var maxIdxAll = getMaxIdx(allOutput)

                    averageAllIndex[0][maxIdxAll] = averageAllIndex[0][maxIdxAll] + 1
                    averageAllConfidence[0][maxIdxAll] =
                        averageAllConfidence[0][maxIdxAll] + allOutput[0][maxIdxAll]
                    roundAll++

                    if (roundAll == 10) {
                        roundAll = 0

                        var maxAverageIdxAll = getMaxIdx(averageAllIndex)
                        ALL_pred_act = labelsMap.getValue(maxAverageIdxAll)

                        var averageAllCount = averageAllIndex[0][maxAverageIdxAll]
                        var allConfidence =
                            averageAllConfidence[0][maxAverageIdxAll] / averageAllCount
                        ALL_pred_con = allConfidence.toString()

                        averageAllIndex = Array(1) { FloatArray(13) { 0f } }
                        averageAllConfidence = Array(1) { FloatArray(13) { 0f } }

                    }
                    runOnUiThread {
                        ALL_Act.text = "Activity: " + ALL_pred_act
                        ALL_Con.text =  ALL_pred_con

                        // Statistc? maybe

                    }
                    lockRespeck++
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
//        respeckChart = findViewById(R.id.respeck_chart)
//        thingyChart = findViewById(R.id.thingy_chart)

        // Respeck

//        time = 0f
//        val entries_res_accel_x = ArrayList<Entry>()
//        val entries_res_accel_y = ArrayList<Entry>()
//        val entries_res_accel_z = ArrayList<Entry>()
//
//        dataSet_res_accel_x = LineDataSet(entries_res_accel_x, "Accel X")
//        dataSet_res_accel_y = LineDataSet(entries_res_accel_y, "Accel Y")
//        dataSet_res_accel_z = LineDataSet(entries_res_accel_z, "Accel Z")
//
//        dataSet_res_accel_x.setDrawCircles(false)
//        dataSet_res_accel_y.setDrawCircles(false)
//        dataSet_res_accel_z.setDrawCircles(false)
//
//        dataSet_res_accel_x.setColor(
//            ContextCompat.getColor(
//                this,
//                R.color.red
//            )
//        )
//        dataSet_res_accel_y.setColor(
//            ContextCompat.getColor(
//                this,
//                R.color.green
//            )
//        )
//        dataSet_res_accel_z.setColor(
//            ContextCompat.getColor(
//                this,
//                R.color.blue
//            )
//        )
//
//        val dataSetsRes = ArrayList<ILineDataSet>()
//        dataSetsRes.add(dataSet_res_accel_x)
//        dataSetsRes.add(dataSet_res_accel_y)
//        dataSetsRes.add(dataSet_res_accel_z)
//
//        allRespeckData = LineData(dataSetsRes)
//        respeckChart.data = allRespeckData
//        respeckChart.invalidate()

        // Thingy

//        time = 0f
//        val entries_thingy_accel_x = ArrayList<Entry>()
//        val entries_thingy_accel_y = ArrayList<Entry>()
//        val entries_thingy_accel_z = ArrayList<Entry>()
//
//        dataSet_thingy_accel_x = LineDataSet(entries_thingy_accel_x, "Accel X")
//        dataSet_thingy_accel_y = LineDataSet(entries_thingy_accel_y, "Accel Y")
//        dataSet_thingy_accel_z = LineDataSet(entries_thingy_accel_z, "Accel Z")
//
//        dataSet_thingy_accel_x.setDrawCircles(false)
//        dataSet_thingy_accel_y.setDrawCircles(false)
//        dataSet_thingy_accel_z.setDrawCircles(false)
//
//        dataSet_thingy_accel_x.setColor(
//            ContextCompat.getColor(
//                this,
//                R.color.red
//            )
//        )
//        dataSet_thingy_accel_y.setColor(
//            ContextCompat.getColor(
//                this,
//                R.color.green
//            )
//        )
//        dataSet_thingy_accel_z.setColor(
//            ContextCompat.getColor(
//                this,
//                R.color.blue
//            )
//        )
//
//        val dataSetsThingy = ArrayList<ILineDataSet>()
//        dataSetsThingy.add(dataSet_thingy_accel_x)
//        dataSetsThingy.add(dataSet_thingy_accel_y)
//        dataSetsThingy.add(dataSet_thingy_accel_z)
//
//        allThingyData = LineData(dataSetsThingy)
//        thingyChart.data = allThingyData
//        thingyChart.invalidate()
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

    private fun getMaxIdx(temp:Array<FloatArray> ): Int {

        var max: Float = 0f
        var maxIdx: Int = 0
        for(i in 0 until 13){
            if (max<temp[0][i]) {
                max = temp[0][i]
                maxIdx = i
            }
        }
//        var label: String = labelsMap.getValue(maxIdx)
        return maxIdx
    }

    private fun printOutput(temp:Array<FloatArray> ): String {

        var s: String = ""
        for(i in 0 until 13){
            s = s+temp[0][i]
        }
        return s
    }

    private fun setupButton(){
//        RecordingButton = findViewById(R.id.start_button)
//
//
//
//        RecordingButton.setOnClickListener {
//
//            getInputs()
//
//            if (sensorType == "Respeck" && !respeckOn) {
//                Toast.makeText(this, "Respeck is not on! Check connection.", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//
//
//            if (sensorType == "Thingy" && !thingyOn) {
//                Toast.makeText(this, "Thingy is not on! Check connection.", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }
//
//            Toast.makeText(this, "Starting recording", Toast.LENGTH_SHORT).show()
//
////            disableView(RecordingButton)    //
////
////            disableView(sensorTypeSpinner)
//
//            startRecording()
//        }

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

//    private fun count(){   //once the thingy or respeck data array is full ,then stop recording
//        if(counttime == 49){
//            Toast.makeText(this, "Stop recording", Toast.LENGTH_SHORT).show()
//            mIsThingyRecording = false   //stop the recording
//            mIsRespeckRecording = false  //stop the recording
////            enableView(RecordingButton)
////            enableView(sensorTypeSpinner)
//            counttime = 0;
//            for(i in 0 until 50)
//                for(j in 0 until 6) {
//                    Log.v("datarecord:", respeck_data[i][j].toString())
//                }
//        }
//    }

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