package com.example.smartwatchauthenticator;

import static android.content.Context.VIBRATOR_SERVICE;

import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.content.Context;

import androidx.annotation.RequiresApi;

import com.example.Authentication.AnomalyDetectionSystem;
import com.example.Authentication.PreprocessingResult;
import com.example.Constants;
import com.example.WalkingDetection.ACFilteringResult;
import com.example.WalkingDetection.StepAccelerometerValues;
import com.example.WalkingDetection.WalkDetectorFSM;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * The FilteringAndClassificationThread is a class, which extends the Thread class, used when the
 * the architecture requires walking detection and classification to be performed locally on the smartwatch.
 * In this thread will be performed first the Filtering of walking segments using AutoCorrelation features
 * and then, if the filtering has positive result, will be executed the classification of the walking
 * segment in order to authenticate the user.
 */
public class FilteringAndClassificationThread extends Thread {

    private List<StepAccelerometerValues> stepList;
    private File storagePath;
    private SensorHandler sensorHandler;
    private String mode;
    private int trainInstanceCounter;
    private int testInstanceCounter;
    private Context ctx;


    public FilteringAndClassificationThread(List<StepAccelerometerValues> list, SensorHandler sensorHandler,File storagePath,String mode,int tr_c,int test_c,Context ctx){

        this.stepList = new ArrayList<>(list);
        this.sensorHandler = sensorHandler;
        this.storagePath = storagePath;
        this.mode = mode;
        this.trainInstanceCounter = tr_c;
        this.testInstanceCounter = test_c;
        this.ctx = ctx;

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void run(){

        ArrayList<Float> concat_accX = new ArrayList<>();
        ArrayList<Float> concat_accY = new ArrayList<>();
        ArrayList<Float> concat_accZ = new ArrayList<>();
        ArrayList<Long> concat_timestamps = new ArrayList<>();

        for (int i = 0; i < stepList.size(); i++) {

            concat_accX.addAll(stepList.get(i).getAx());
            concat_accY.addAll(stepList.get(i).getAy());
            concat_accZ.addAll(stepList.get(i).getAz());
            concat_timestamps.addAll(stepList.get(i).getTimestamps());
        }


        ACFilteringResult r;
        r = WalkDetectorFSM.autoCorrelationFiltering(concat_accX,concat_accY,concat_accZ,concat_timestamps);
        if(!r.isResult()){
            //If i'm here means that the walking segment is irregular/not acceptable.
            //The walking segment will be discarded and the thread interrupted before starting classification.
            //Before its interruption the thread has to flush the ring buffer of the FSM and all the sensed
            //accelerometer values collected until that moment. Then the thread restarts the sensing that
            //was previously stopped. (All the operations are encapsulated within the restartSensing method)
            Log.d("debug","CURRENT INSTANCE FILTERED, WAIT FOR A NEW ONE!");
            File f = new File(storagePath, "practicalTest_LOG.txt");
            try {
                if (!f.exists()) {
                    f.createNewFile();
                }
                FileWriter LogWriter = new FileWriter(f,true);
                Date date = Calendar.getInstance().getTime();
                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String strDate = dateFormat.format(date);
                String out = strDate+"; GAIT SEGMENT FILTERED \n\n";
                LogWriter.write(out);
                LogWriter.close();


            } catch (IOException e) {
                e.printStackTrace();
            }
            sensorHandler.restartSensing();
            return;
        }


        //If i'm here means that the walking segment can be used for classification and so we can start
        //the authentication procedure. (I can find the 4 features AC_C1 ecc inside the ACfiltering method result)
        //First is performed preprocessing over the 3 accelerometer vectors(acc_x,acc_y,acc_z)
        PreprocessingResult data = AnomalyDetectionSystem.preProcessing(concat_accX,concat_accY,concat_accZ,concat_timestamps);

        //When preprocessing is completed, feature extraction is performed.
        float[] featuresExtracted = AnomalyDetectionSystem.featureExtraction(data,r);

        //if we reach this point means that has to be executed the classic authentication
        //mechanism to evaluate single walking instances.

        boolean result = false;
        float[] features_prenorm = new float[featuresExtracted.length];
        System.arraycopy(featuresExtracted,0,features_prenorm,0,featuresExtracted.length);

        result = AnomalyDetectionSystem.anomalyDetection(featuresExtracted,storagePath);
        if(!result){
            //If result is false means that the classification has given negative result (user not recognized)
            //For this reason everything is resetted and then sensing is restarted (as when ACfiltering fails)
            Log.println(Log.INFO,"classifier thread","User NOT AUTHENTICATED");
            File f = new File(storagePath, "practicalTest_LOG.txt");
            try {
                if (!f.exists()) {
                    f.createNewFile();
                }
                FileWriter LogWriter = new FileWriter(f,true);
                Date date = Calendar.getInstance().getTime();
                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String strDate = dateFormat.format(date);
                String out = strDate+"; NOT AUTHENTICATED ;"+Arrays.toString(features_prenorm)+"\n\n";
                LogWriter.write(out);
                LogWriter.close();


            } catch (IOException e) {
                e.printStackTrace();
            }

            sensorHandler.restartSensing();
            return;
        }else{
            //If result is true means the authentication procedure has been completed successfully, user is
            //recognized and the device is now unlocked. Then all the data structure are resetted in order
            //to restart the authentication procedure after a pause interval.
            Log.d("debug","User AUTHENTICATED");
            File f = new File(storagePath, "practicalTest_LOG.txt");
            try {
                if (!f.exists()) {
                    f.createNewFile();
                }
                FileWriter LogWriter = new FileWriter(f,true);
                Date date = Calendar.getInstance().getTime();
                DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String strDate = dateFormat.format(date);
                String out = strDate+"; AUTHENTICATED ;"+Arrays.toString(features_prenorm)+"\n\n";
                LogWriter.write(out);
                LogWriter.close();


            } catch (IOException e) {
                e.printStackTrace();
            }
            sensorHandler.restartSensing();
        }


    }



}
