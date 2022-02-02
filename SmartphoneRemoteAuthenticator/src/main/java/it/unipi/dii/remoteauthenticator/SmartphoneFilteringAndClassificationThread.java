package it.unipi.dii.remoteauthenticator;

import android.content.Context;
import android.util.Log;

import it.unipi.dii.Authentication.AnomalyDetectionSystem;
import it.unipi.dii.Authentication.PreprocessingResult;
import it.unipi.dii.Constants;
import it.unipi.dii.WalkingDetection.ACFilteringResult;
import it.unipi.dii.WalkingDetection.StepAccelerometerValues;
import it.unipi.dii.WalkingDetection.WalkDetectorFSM;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The SmartphoneFilteringAndClassificationThread is a class, which extends the Thread class, used when the
 * the architecture requires walking detection and classification to be performed locally on the smartwatch.
 * In this thread will be performed first the Filtering of walking segments using AutoCorrelation features
 * and then, if the filtering has positive result, will be executed the classification of the walking
 * segment in order to authenticate the user.
 */
public class SmartphoneFilteringAndClassificationThread extends Thread {

    private List<StepAccelerometerValues> stepList;
    private File storagePath;


    private DataClient dataClient;



    public SmartphoneFilteringAndClassificationThread(List<StepAccelerometerValues> list, File storagePath,Context ctx,DataClient dataclient){

        this.stepList = new ArrayList<>(list);
        this.storagePath = storagePath;
        this.dataClient = Wearable.getDataClient(ctx);

    }

    public void sendResponseToSmartwatch(String result){

        PutDataMapRequest putDMR = PutDataMapRequest.create(Constants.NOTIFICATION_RESULT_PATH);

        Long tsLong = System.currentTimeMillis() / 1000;
        String timestamp = tsLong.toString();

        putDMR.getDataMap().putString("Timestamp", timestamp);
        putDMR.getDataMap().putString("AuthenticationResult",result+System.currentTimeMillis());


        PutDataRequest putDR = putDMR.asPutDataRequest();
        putDR.setUrgent();
        Task<DataItem> putTask = dataClient.putDataItem(putDR);

        putTask.addOnSuccessListener(
                new OnSuccessListener<DataItem>() {
                    @Override
                    public void onSuccess(DataItem dataItem) {
                        Log.d("SmartphoneClassific", "Authentication response successfully sent: " + dataItem);
                    }
                });

    }

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
            sendResponseToSmartwatch("Walking segment filtered");
            return;
        }


        //If i'm here means that the walking segment can be used for classification and so we can start
        //the authentication procedure. (I can find the 4 features AC_C1 ecc inside the ACfiltering method result)
        //First is performed preprocessing over the 3 accelerometer vectors(acc_x,acc_y,acc_z)
        PreprocessingResult data = AnomalyDetectionSystem.preProcessing(concat_accX,concat_accY,concat_accZ,concat_timestamps);

        //When preprocessing is completed, feature extraction is performed.
        float[] featuresExtracted = AnomalyDetectionSystem.featureExtraction(data,r);

        //If we reach this point means that has to be executed the classic authentication
        //mechanism to evaluate single walking instances.

        boolean result = false;
        result = AnomalyDetectionSystem.anomalyDetection(featuresExtracted,storagePath);

        if(!result){
            //If result is false means that the classification has given negative result (user not recognized)
            //For this reason everything is resetted and then sensing is restarted (as when ACfiltering fails)
            Log.println(Log.INFO,"classifier thread","User NOT AUTHENTICATED");
            sendResponseToSmartwatch("Authentication failed");
            return;
        }else{
            //If result is true means the authentication procedure has been completed successfully, user is
            //recognized and the device is now unlocked. Then all the data structure are resetted in order
            //to restart the authentication procedure after a pause interval.
            Log.println(Log.INFO,"classifier thread","User AUTHENTICATED");
            sendResponseToSmartwatch("Authentication success");
        }


    }



}
