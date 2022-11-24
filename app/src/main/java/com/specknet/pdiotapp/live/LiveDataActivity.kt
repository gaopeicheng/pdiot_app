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

    var lockThingy = 0
    var lockRespeck = 1
    var lockThingy30s = 0
    var lockRespeck30s = 1

    var countTimeRepseck30s = 0
    var countTimeThingy30s = 0
    var countTimeAllRespeck30s = 0
    var countTimeAllThingy30s = 0

    var respeck_data = Array(50){FloatArray(6)}  //respeck array to store 50*6 data
    var thingy_data = Array(50){FloatArray(9)}   //thingy array to store 50*9 data
    var all_data = Array(50){FloatArray(15)}      //all array to store 50*15 data

    var respeck_data_30s = Array(50){FloatArray(6)}
    var thingy_data_30s = Array(50){FloatArray(9)}
    var all_data_30s = Array(50){FloatArray(15)}

    var thingyMaxIdx30s = Array(1){Array(14){0}}
    var thingyConfidence30s = Array(1){FloatArray(14){0f}}
    var respeckMaxIdx30s = Array(1){Array(14){0}}
    var respeckConfidence30s = Array(1){FloatArray(14){0f}}
    var allMaxIdx30s = Array(1){Array(14){0}}
    var allConfidence30s = Array(1){FloatArray(14){0f}}

    var roundThingy = 0
    var roundRespeck = 0
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

    val labelsMap = mapOf<Int,String>(0 to "12 Climbing stairs"  ,
        1 to "13 Descending stairs",
        2 to "31 Desk work",
        3 to "7 Lying down left"   ,
        4 to "2 Lying down on back"   ,
        5 to "8 Lying down on stomach",
        6 to "6 Lying down right",
        7 to "9 Movement",
        8 to "11 Running",
        9 to "0 Sitting",
        10 to "5 Sitting bent backward",
        11 to "4 Sitting bent forward",
        12 to "100 Standing",
        13 to "1 Walking at normal speed")

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

                roundThingy = 0
                thingyMaxIdx30s = Array(1){Array(14){0}}
                thingyConfidence30s = Array(1){FloatArray(14){0f}}

                roundRespeck = 0
                respeckMaxIdx30s = Array(1){Array(14){0}}
                respeckConfidence30s = Array(1){FloatArray(14){0f}}
            }
        }

        // set up the broadcast receiver
        respeckLiveUpdateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {

                Log.i("thread", "I am running on thread = " + Thread.currentThread().name)

                val action = intent.action

                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {

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
                    }
                    else {
                        countTimeRepseck = 0
                    }

                    if (countTimeRepseck % 10 == 0) {
                        var RESbyteBuffer: ByteBuffer = ByteBuffer.allocateDirect(50 * 6 * 4)
                        RESbyteBuffer.order(ByteOrder.nativeOrder())
                        for (i in 0 until 50) {
                            for (j in 0 until 6) {
                                RESbyteBuffer.putFloat(respeck_data[i][j].toFloat())
                            }
                        }
                        var RESoutput = Array(1) { FloatArray(14) { 0f } }
                        REStflite.run(RESbyteBuffer, RESoutput)
                        var maxIdxRespeck = getMaxIdx(RESoutput)

                        RES_pred_act = labelsMap.getValue(maxIdxRespeck)
                        RES_pred_con = RESoutput[0][maxIdxRespeck].toString()
                    }

                    if (startflag) {
                        if(countTimeRepseck30s < 50){   //store 50*9 data into the thingy array

                            respeck_data_30s[countTimeRepseck30s][0] = respeckLiveData.accelX
                            respeck_data_30s[countTimeRepseck30s][1] = respeckLiveData.accelY
                            respeck_data_30s[countTimeRepseck30s][2] = respeckLiveData.accelZ
                            respeck_data_30s[countTimeRepseck30s][3] = respeckLiveData.gyro.x
                            respeck_data_30s[countTimeRepseck30s][4] = respeckLiveData.gyro.y
                            respeck_data_30s[countTimeRepseck30s][5] = respeckLiveData.gyro.z
                            countTimeRepseck30s++
                        }
                        else {
                            countTimeRepseck30s = 0
                            var respeckByteBuffer30s: ByteBuffer = ByteBuffer.allocateDirect(50 * 6 * 4)
                            respeckByteBuffer30s.order(ByteOrder.nativeOrder())
                            for (i in 0 until 50) {
                                for (j in 0 until 6) {
                                    respeckByteBuffer30s.putFloat(respeck_data_30s[i][j])
                                }
                            }
                            var respeck30sOutput = Array(1) { FloatArray(14) { 0f } }
                            REStflite.run(respeckByteBuffer30s, respeck30sOutput)

                            var maxIdxrespeck30s = getMaxIdx(respeck30sOutput)
                            respeckMaxIdx30s[0][maxIdxrespeck30s] += 1
                            respeckConfidence30s[0][maxIdxrespeck30s] += respeck30sOutput[0][maxIdxrespeck30s]

                            roundRespeck++
                            if (roundRespeck == 8) {
                                var respeckFinalIdx = getMaxIdx(respeckConfidence30s)
                                var respeckSecondFinalIdx = getSecondMaxIdx(respeckConfidence30s, respeckFinalIdx)

                                STAT_res_1_act = labelsMap.getValue(respeckFinalIdx)
                                STAT_res_1_con = (respeckConfidence30s[0][respeckFinalIdx] / 8).toString()

                                STAT_res_2_act = labelsMap.getValue(respeckSecondFinalIdx)
                                STAT_res_2_con = (respeckConfidence30s[0][respeckSecondFinalIdx] / 8).toString()

                                roundRespeck = 0
                                respeckMaxIdx30s = Array(1){Array(14){0}}
                                respeckConfidence30s = Array(1){FloatArray(14){0f}}
                            }
                        }

                        while (lockRespeck30s <= 0) {
                            if (lockRespeck30s > 0) break
                        }
                        lockRespeck30s--

                        if (countTimeAllRespeck30s < 50) {
                            all_data_30s[countTimeAllRespeck30s][9] = respeckLiveData.accelX
                            all_data_30s[countTimeAllRespeck30s][10] = respeckLiveData.accelY
                            all_data_30s[countTimeAllRespeck30s][11] = respeckLiveData.accelZ
                            all_data_30s[countTimeAllRespeck30s][12] = respeckLiveData.gyro.x
                            all_data_30s[countTimeAllRespeck30s][13] = respeckLiveData.gyro.y
                            all_data_30s[countTimeAllRespeck30s][14] = respeckLiveData.gyro.z
                            countTimeAllRespeck30s++
                        }
                        else {
                            countTimeAllRespeck30s = 0
                        }

                        lockThingy30s++
                    }

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
                    }
                    else {
                        countTimeAllRespeck = 0
                    }

                    lockThingy++

                    runOnUiThread {    //real-time data show on the ui
                        respeck_accel_x.text = "accel_x = " + xRespeck.toString()
                        respeck_accel_y.text = "accel_y = " + yRespeck.toString()
                        respeck_accel_z.text = "accel_z = " + zRespeck.toString()
                        respeck_gyro_x.text = "gyro_x = " + groyXRespeck.toString()
                        respeck_gyro_y.text = "gyro_y = " + groyYRespeck.toString()
                        respeck_gyro_z.text = "gyro_z = " + groyZRespeck.toString()

                        RES_Act.text = "Activity: " + RES_pred_act
                        RES_Con.text =  RES_pred_con

                        STAT_res_fir_act.text = STAT_res_1_act
                        STAT_res_fir_con.text = STAT_res_1_con

                        STAT_res_sec_act.text = STAT_res_2_act
                        STAT_res_sec_con.text = STAT_res_2_con

                    }

//                    time += 1
//                    updateGraph("respeck", x, y, z)

                    respeckOn = true    //the respeck bluetooth is on

//                    time += 1
//                    updateGraph("thingy", x, y, z)
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
                    }
                    else {
                        countTimeThingy = 0
                    }

                    if (countTimeThingy % 10 == 0) {
                        var thingyByteBuffer: ByteBuffer = ByteBuffer.allocateDirect(50 * 9 * 4)
                        thingyByteBuffer.order(ByteOrder.nativeOrder())
                        for (i in 0 until 50) {
                            for (j in 0 until 9) {
                                thingyByteBuffer.putFloat(thingy_data[i][j].toFloat())
                            }
                        }
                        var thingyOutput = Array(1) { FloatArray(14) { 0f } }
                        THItflite.run(thingyByteBuffer, thingyOutput)
                        var maxIdxThingy = getMaxIdx(thingyOutput)

                        THI_pred_act = labelsMap.getValue(maxIdxThingy)
                        THI_pred_con = thingyOutput[0][maxIdxThingy].toString()

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

                    if (startflag) {
                        if(countTimeThingy30s < 50){   //store 50*9 data into the thingy array

                            thingy_data_30s[countTimeThingy30s][0] = thingyLiveData.accelX
                            thingy_data_30s[countTimeThingy30s][1] = thingyLiveData.accelY
                            thingy_data_30s[countTimeThingy30s][2] = thingyLiveData.accelZ
                            thingy_data_30s[countTimeThingy30s][3] = thingyLiveData.gyro.x
                            thingy_data_30s[countTimeThingy30s][4] = thingyLiveData.gyro.y
                            thingy_data_30s[countTimeThingy30s][5] = thingyLiveData.gyro.z
                            thingy_data_30s[countTimeThingy30s][6] = thingyLiveData.mag.x
                            thingy_data_30s[countTimeThingy30s][7] = thingyLiveData.mag.y
                            thingy_data_30s[countTimeThingy30s][8] = thingyLiveData.mag.z
                            countTimeThingy30s++
                        }
                        else {
                            countTimeThingy30s = 0
                            var thingyByteBuffer30s: ByteBuffer = ByteBuffer.allocateDirect(50 * 9 * 4)
                            thingyByteBuffer30s.order(ByteOrder.nativeOrder())
                            for (i in 0 until 50) {
                                for (j in 0 until 9) {
                                    thingyByteBuffer30s.putFloat(thingy_data_30s[i][j])
                                }
                            }
                            var thingy30sOutput = Array(1) { FloatArray(14) { 0f } }
                            THItflite.run(thingyByteBuffer30s, thingy30sOutput)

                            var maxIdxThingy30s = getMaxIdx(thingy30sOutput)
                            thingyMaxIdx30s[0][maxIdxThingy30s] += 1

                            thingyConfidence30s[0][maxIdxThingy30s] += thingy30sOutput[0][maxIdxThingy30s]

                            roundThingy++
                            if (roundThingy == 8) {
                                var thingyFinalIdx = getMaxIdx(thingyConfidence30s)
                                var thingySecondFinalIdx = getSecondMaxIdx(thingyConfidence30s, thingyFinalIdx)
                                STAT_thi_1_act = labelsMap.getValue(thingyFinalIdx)
                                STAT_thi_1_con = (thingyConfidence30s[0][thingyFinalIdx] / 8).toString()

                                STAT_thi_2_act = labelsMap.getValue(thingySecondFinalIdx)
                                STAT_thi_2_con = (thingyConfidence30s[0][thingySecondFinalIdx] / 8).toString()

                                roundThingy = 0
                                thingyMaxIdx30s = Array(1){Array(14){0}}
                                thingyConfidence30s = Array(1){FloatArray(14){0f}}
                            }
                        }

                        while (lockThingy30s <= 0) {
                            if (lockThingy30s > 0) break
                        }
                        lockThingy30s--

                        if (countTimeAllThingy30s < 50) {
                            all_data_30s[countTimeAllThingy30s][0] = thingyLiveData.accelX
                            all_data_30s[countTimeAllThingy30s][1] = thingyLiveData.accelY
                            all_data_30s[countTimeAllThingy30s][2] = thingyLiveData.accelZ
                            all_data_30s[countTimeAllThingy30s][3] = thingyLiveData.gyro.x
                            all_data_30s[countTimeAllThingy30s][4] = thingyLiveData.gyro.y
                            all_data_30s[countTimeAllThingy30s][5] = thingyLiveData.gyro.z
                            all_data_30s[countTimeAllThingy30s][6] = thingyLiveData.mag.x
                            all_data_30s[countTimeAllThingy30s][7] = thingyLiveData.mag.y
                            all_data_30s[countTimeAllThingy30s][8] = thingyLiveData.mag.z
                            countTimeAllThingy30s++
                        }
                        else {
                            countTimeAllThingy30s = 0
                            var allByteBuffer30s: ByteBuffer = ByteBuffer.allocateDirect(50 * 15 * 4)
                            allByteBuffer30s.order(ByteOrder.nativeOrder())
                            for (i in 0 until 50) {
                                for (j in 0 until 15) {
                                    allByteBuffer30s.putFloat(all_data_30s[i][j])
                                }
                            }
                            var all30sOutput = Array(1) { FloatArray(14) { 0f } }
                            ALLtflite.run(allByteBuffer30s, all30sOutput)

                            var maxIdxAll30s = getMaxIdx(all30sOutput)
                            allMaxIdx30s[0][maxIdxAll30s] += 1

                            allConfidence30s[0][maxIdxAll30s] += all30sOutput[0][maxIdxAll30s]

                            roundAll++
                            if (roundAll == 8) {
                                var allFinalIdx = getMaxIdx(allConfidence30s)
                                var allSecondFinalIdx = getSecondMaxIdx(allConfidence30s, allFinalIdx)
                                STAT_all_1_act = labelsMap.getValue(allFinalIdx)
                                STAT_all_1_con = (allConfidence30s[0][allFinalIdx] / 8).toString()

                                STAT_all_2_act = labelsMap.getValue(allSecondFinalIdx)
                                STAT_all_2_con = (thingyConfidence30s[0][allSecondFinalIdx] / 8).toString()

                                roundAll = 0
                                allMaxIdx30s = Array(1){Array(14){0}}
                                allConfidence30s = Array(1){FloatArray(14){0f}}
                            }
                        }

                        lockRespeck30s++
                    }

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
                    }
                    else {
                        countTimeAllThingy = 0
                    }
                    if (countTimeAllThingy % 10 == 0) {
                        var allByteBuffer: ByteBuffer = ByteBuffer.allocateDirect(50 * 15 * 4)
                        allByteBuffer.order(ByteOrder.nativeOrder())
                        for (i in 0 until 50) {
                            for (j in all_data[i].indices) {
                                allByteBuffer.putFloat(all_data[i][j].toFloat())
                            }
                        }
                        var allOutput = Array(1) { FloatArray(14) { 0f } }
                        ALLtflite.run(allByteBuffer, allOutput)
                        var maxIdxAll = getMaxIdx(allOutput)

                        ALL_pred_act = labelsMap.getValue(maxIdxAll)
                        ALL_pred_con = allOutput[0][maxIdxAll].toString()
                    }
                    lockRespeck++

                    runOnUiThread {
                        ALL_Act.text = "Activity: " + ALL_pred_act
                        ALL_Con.text =  ALL_pred_con

                        STAT_thi_fir_act.text = STAT_thi_1_act
                        STAT_thi_fir_con.text = STAT_thi_1_con

                        STAT_thi_sec_act.text = STAT_thi_2_act
                        STAT_thi_sec_con.text = STAT_thi_2_con


                        STAT_all_fir_act.text = STAT_all_1_act
                        STAT_all_fir_con.text = STAT_all_1_con

                        STAT_all_sec_act.text = STAT_all_2_act
                        STAT_all_sec_con.text = STAT_all_2_con
                    }
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

    private fun getMaxIdx(temp:Array<FloatArray> ): Int {

        var max: Float = 0f
        var maxIdx: Int = 0
        for(i in 0 until 14){
            if (max<temp[0][i]) {
                max = temp[0][i]
                maxIdx = i
            }
        }
        return maxIdx
    }

    private fun getSecondMaxIdx(temp:Array<FloatArray>, max:Int ): Int {

        var temp2 = 0f
        var secondMaxIdx: Int = 0
        for(i in 0 until 14){
            if (temp2 < temp[0][i] && max != i) {
                temp2 = temp[0][i]
                secondMaxIdx = i
            }
        }
        return secondMaxIdx
    }

    private fun printOutput(temp:Array<FloatArray> ): String {

        var s: String = ""
        for(i in 0 until 14){
            s = s+temp[0][i]
        }
        return s
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