package com.specknet.pdiotapp.live;

import android.util.Log;
import com.specknet.pdiotapp.utils.RespeckData;
import org.tensorflow.lite.Interpreter;
import java.util.Arrays;

import static com.specknet.pdiotapp.MainActivityKt.associatedAxisLabels;
import static com.specknet.pdiotapp.MainActivityKt.modelFile;

public class Model {
    private static int inputLength = 50;
    private static int outputLength = 13;
    private static Interpreter interpreter = new Interpreter(modelFile);
    private static float[][][] input = new float[1][inputLength][3];
    private static float[][] output = new float[1][outputLength];

//    public static String[] getPrediction(RespeckData respeckData) {
//        float accel_x = respeckData.getAccel_x();
//        float accel_y = respeckData.getAccel_y();
//        float accel_z = respeckData.getAccel_z();
//
//        interpreter.run(input, output);
//
//        for (int i = 0; i < inputLength - 1; i++) {
//            input[0][i] = input[0][i+1];
//        }
//
//        input[0][inputLength - 1] = new float[] {accel_x, accel_y, accel_z};
//
//
//        String[] result = getPredictedClassWithConfidence();
//        Log.d("Prediction", result[0]);
//        return result;
//    }

    public static String[] getPrediction() {
//        float accel_x = respeckData.getAccel_x();
//        float accel_y = respeckData.getAccel_y();
//        float accel_z = respeckData.getAccel_z();
//
//
//        for (int i = 0; i < inputLength - 1; i++) {
//            input[0][i] = input[0][i+1];
//        }
//
//        input[0][inputLength - 1] = new float[] {accel_x, accel_y, accel_z};


        String[] result = getPredictedClassWithConfidence();
        Log.d("Prediction", result[0]);
        return result;
    }

    private static String[] getPredictedClassWithConfidence() {
        float max = output[0][0];
        int maxIdx = 0;

        for (int i = 1; i < outputLength; i++) {
            if (output[0][i] > max) {
                max = output[0][i];
                maxIdx = i;
            }
        }

        Log.d("Values", Arrays.toString(output[0]));

//        String label = associatedAxisLabels.get(maxIdx);
        String label = "Sitting/Standing";
        int confidence = Math.round(max * 100);
        String[] result = new String[] {label, String.valueOf(confidence)};

        return result;
    }

//    private static String[] getPredictedClassWithConfidence() {
//        float max = output[0][0];
//        int maxIdx = 0;
//
//        for (int i = 1; i < outputLength; i++) {
//            if (output[0][i] > max) {
//                max = output[0][i];
//                maxIdx = i;
//            }
//        }
//
//        Log.d("Values", Arrays.toString(output[0]));
//
//        String label = associatedAxisLabels.get(maxIdx);
//        int confidence = Math.round(max * 100);
//        String[] result = new String[] {label, String.valueOf(confidence)};
//
//        return result;
//    }
}
