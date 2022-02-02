package it.unipi.dii.remoteauthenticator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import it.unipi.dii.Authentication.FeaturesCalculator;
import it.unipi.dii.remoteauthenticator.R;

import it.unipi.dii.Constants;
import it.unipi.dii.WalkingDetection.StepAccelerometerValues;
import it.unipi.dii.WalkingDetection.WalkDetectorFSM;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class SmartwatchDataReceiver extends WearableListenerService{

    private int sensedValues; // Simple integer used to check if at least 3 values are already sensed
    private WalkDetectorFSM wFSM; // Object that implements the FSM used to execute the waling detection on the values sensed by the accelerometer
    private int counted_steps;  //this variable counts the step detected by the FSM.
    private ArrayList<StepAccelerometerValues> stepList;
    private String Accelerometer_data;
    private List<StepAccelerometerValues> receivedStepList;
    private String line;
    private String[] previousFields;
    private StepAccelerometerValues temp;
    private long sample_timestamp;

    private PowerManager mPowerManager;
    private PowerManager.WakeLock wl;
    private DataClient dataClient;
    private Context ctx;
    private File storagePath;
    private Notification notif;
    private double randomNumber;
    private Object ResetLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId){

        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wl = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "RemoteAuthenticator::authWakeLock");

        storagePath = new File("/storage/emulated/0/Download");//getApplicationContext().getExternalFilesDir(null);
        ctx = this;
        dataClient = Wearable.getDataClient(this);

        randomNumber = Math.random();

        createNotificationChannel();
        notif = updateNotification("Started remote authenticator application",1,true);
        startForeground(1,notif);

        wFSM = new WalkDetectorFSM();
        stepList = new ArrayList<>();
        ResetLock = new Object();
        counted_steps = 0;
        sensedValues = 0;
        sample_timestamp = 0;


        wl.acquire();
        return Service.START_STICKY;
    }

    @Override
    /**
     * This method is used to handle the reception of a message from the smartphone through Bluetooth.
     * In particular that message will contain the result of the classification (authentication) procedure.
     *
     *
     */
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().compareTo(Constants.COMMUNICATION_PATH) == 0) {
                DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                if (dataMap.isEmpty()) {
                    Log.d("SmartwDataRec", "DataMap vuoto");
                } else {
                    Log.d("SmartwDataRec", "Command Received");
                    String result = dataMap.getString("Command");
                    //This command is received when the smartwatch requests only the remote
                    //classification, sending the already found walking segment. Otherwise when the
                    //TotalRemote command is received, the smartwatch has to execute the entire "pipeline"
                    //of processing. Firstly, it will execute the walking detection and then will execute
                    //the authentication procedure
                    if(/*result.contains("ClassificationRemote")*/dataMap.getAsset("step0") != null){
                        ArrayList<Asset> asset_list = new ArrayList<>();
                        receivedStepList = new ArrayList<>();

                        for(int i=0;i<Constants.MIN_NUM_STEPS+1;i++){
                            asset_list.add(dataMap.getAsset("step"+i));
                        }
                        new LoadFileTask().execute(asset_list.get(0),asset_list.get(1),asset_list.get(2),asset_list.get(3),
                                asset_list.get(4),asset_list.get(5),asset_list.get(6),asset_list.get(7));

                    }else if(/*result.contains("TotalRemote")*/dataMap.getAsset("Accelerometer") != null){

                        Asset a = dataMap.getAsset("Accelerometer");
                        new LoadFileTask().execute(a);

                    }

                }
            }
        }
    }

    List<StepAccelerometerValues> readStepValuesFromCSV(){

        try {
            ArrayList<StepAccelerometerValues> ret = new ArrayList<>();
            for (int i = 0; i < Constants.MIN_NUM_STEPS + 1; i++) {

                File f = new File(storagePath, "step" + i+".csv");
                BufferedReader stepReader = new BufferedReader(new FileReader(f));

                StepAccelerometerValues step = new StepAccelerometerValues();
                String line;
                String[] fields;

                while( (line = stepReader.readLine()) != null){

                    fields = line.split(",");
                    step.addTriplet(Float.parseFloat(fields[0]),Float.parseFloat(fields[1]),Float.parseFloat(fields[2]),
                            Long.parseLong(fields[3]),Float.parseFloat(fields[4]));

                }
                ret.add(step);
                stepReader.close();
                f.delete();
            }

            return ret;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }


    //Task involved in saving the data received from the paired smartwatch. At the end of this operation sends an Intent
    //to the ClassificationService to start features extraction and classification operations.
    private class LoadFileTask extends AsyncTask<Asset, Void, Boolean> {
        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        protected Boolean doInBackground(Asset... params) {
            if (params.length == Constants.MIN_NUM_STEPS+1) {

                Asset[] assets = params;

                String file_name;


                Log.d("LoadFileTask", "Loading the file");

                // Converts asset into a file descriptor and block until it's ready
                for(int i=0; i<assets.length; i++) {
                    file_name = "step"+i+".csv";
                    Task<DataClient.GetFdForAssetResponse> getFdForAssetResponseTask =
                            Wearable.getDataClient(getApplicationContext()).getFdForAsset(assets[i]);
                    InputStream assetInputStream = null;
                    try {
                        DataClient.GetFdForAssetResponse getFdForAssetResponse =
                                Tasks.await(getFdForAssetResponseTask);

                        assetInputStream = getFdForAssetResponse.getInputStream();
                        if (assetInputStream == null) {
                            Log.w("LoadFileTask", "Requested an unknown Asset.");
                            return false;
                        }
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                        return false;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return false;
                    }
                    // decode the stream into a file
                    byte[] buffer = new byte[0];
                    try {
                        buffer = new byte[assetInputStream.available()];
                        assetInputStream.read(buffer);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }

                    int num_lines = buffer.length/Constants.BLUETOOTH_LINE_SIZE;
                    ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
                    byte[] floatbuffer = new byte[4];
                    byte[] longbuffer = new byte[8];
                    StepAccelerometerValues step = new StepAccelerometerValues();

                    for(int j=0;j<num_lines;j++){

                        bis.read(floatbuffer,0,4);
                        float x = ByteBuffer.wrap(floatbuffer).getFloat();
                        bis.read(floatbuffer,0,4);
                        float y = ByteBuffer.wrap(floatbuffer).getFloat();
                        bis.read(floatbuffer,0,4);
                        float z = ByteBuffer.wrap(floatbuffer).getFloat();
                        bis.read(longbuffer,0,8);
                        long timestamp = ByteBuffer.wrap(longbuffer).getLong();
                        bis.read(floatbuffer,0,4);
                        float module = ByteBuffer.wrap(floatbuffer).getFloat();

                        step.addTriplet(x,y,z,timestamp,module);

                    }

                    receivedStepList.add(step);

                }
                return true;

            } else if (params.length == 1) {

                Asset as = params[0];
                Task<DataClient.GetFdForAssetResponse> getFdForAssetResponseTask =
                        Wearable.getDataClient(getApplicationContext()).getFdForAsset(as);
                InputStream assetInputStream = null;
                try {
                    DataClient.GetFdForAssetResponse getFdForAssetResponse =
                            Tasks.await(getFdForAssetResponseTask);

                    assetInputStream = getFdForAssetResponse.getInputStream();
                    if (assetInputStream == null) {
                        Log.w("LoadFileTask", "Requested an unknown Asset.");
                        return false;
                    }
                } catch (ExecutionException e) {
                    e.printStackTrace();
                    return false;
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return false;
                }

                byte[] buffer = new byte[0];
                try {
                    buffer = new byte[assetInputStream.available()];
                    assetInputStream.read(buffer);
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }

                int num_lines = buffer.length/Constants.BLUETOOTH_LINE_SIZE;
                ByteArrayInputStream bis = new ByteArrayInputStream(buffer);
                byte[] floatbuffer = new byte[4];
                byte[] longbuffer = new byte[8];

                StringBuffer rcvData = new StringBuffer();

                for(int j=0;j<num_lines;j++) {

                    bis.read(floatbuffer, 0, 4);
                    float x = ByteBuffer.wrap(floatbuffer).getFloat();
                    bis.read(floatbuffer, 0, 4);
                    float y = ByteBuffer.wrap(floatbuffer).getFloat();
                    bis.read(floatbuffer, 0, 4);
                    float z = ByteBuffer.wrap(floatbuffer).getFloat();
                    bis.read(longbuffer, 0, 8);
                    long timestamp = ByteBuffer.wrap(longbuffer).getLong();
                    bis.read(floatbuffer, 0, 4);
                    float module = ByteBuffer.wrap(floatbuffer).getFloat();

                    String line = timestamp+","+x+","+y+","+z+","+module+"\n";
                    rcvData.append(line);

                }

                //Accelerometer_data = new String(buffer, StandardCharsets.UTF_8);
                Accelerometer_data = rcvData.toString();
                return false;
            }

        return false;
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        protected void onPostExecute(Boolean res) {

            if (res) {
                //List<StepAccelerometerValues> stepList = readStepValuesFromCSV();
                SmartphoneFilteringAndClassificationThread thread = new SmartphoneFilteringAndClassificationThread(receivedStepList,storagePath,ctx,dataClient);
                Log.d("LoadFileTask","AVVIATA PROCEDURA DI AUTENTICAZIONE!");
                thread.start();
            }
            else {

                //Log.d("LoadFileTask",line);
                String[] single_lines = Accelerometer_data.split("\n");
                for(int i=0;i< single_lines.length;i++) {
                    line = single_lines[i];
                    executeLocalWalkingDetection();
                }


            }

        }
    }


    /**
     * This method is executed when the walking Detection procedure has to be executed locally on the smartwatch.
     * In particular each new accelerometer sample (and its module) is analyzed using the Finite State Machine
     * (walkingDetection method) and when a walking segment is found classification mechanism is started.
     * When walking segment is found, depending on the value of the localAuthentication boolean parameter,
     * the method could:
     * - start a new thread to perform classification locally
     * - send walking detection result (step list) to smartphone to execute classification remotely.
     *
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void executeLocalWalkingDetection(){

        //The finite state machine will not be executed until at least 3 values are available in the ring buffer.
        //This check is done through the sensedValues variable.
        if (sensedValues < 2) {


            String[] fields = line.split(",");
            previousFields = fields;
            float mod = FeaturesCalculator.Module(Float.parseFloat(fields[1]),Float.parseFloat(fields[2]),Float.parseFloat(fields[3]));
            wFSM.addToBuffer(mod, (sample_timestamp));
            sample_timestamp += Constants.SAMPLING_PERIOD;
            sensedValues++;

        } else {

            // We have sensed at least 3 values. Now the FSM can start checking each single
            // module value trying to find peaks and group of peaks.


            float mod = 0.0f;
            String[] fields = null;

            fields = line.split(",");
            mod = FeaturesCalculator.Module(Float.parseFloat(previousFields[1]),Float.parseFloat(previousFields[2]),Float.parseFloat(previousFields[3]));
            wFSM.addToBuffer(mod, (sample_timestamp));

            int result = wFSM.walkingDetection();

            switch (result){

                //the FSM that is in GROUP_START_SEARCH has not found a Group start and for this
                //reason there is no need to store the related accelerometer values.
                //We use an approach in which all the accelerometer values between the end of the precedent
                //step and the beginning of the next one are not stored.
                case Constants.NO_STEP_FOUND:  {
                    //Log.d("Test walking detection","Case NO STEP FOUND");

                    if(counted_steps > 0){

                        if(temp == null){
                            temp = new StepAccelerometerValues();
                            sample_timestamp += Constants.SAMPLING_PERIOD;
                            temp.addTriplet(Float.parseFloat(previousFields[1]),Float.parseFloat(previousFields[2]), Float.parseFloat(previousFields[3]),(sample_timestamp),Float.parseFloat(previousFields[4]));

                        }else{
                            sample_timestamp += Constants.SAMPLING_PERIOD;
                            temp.addTriplet(Float.parseFloat(previousFields[1]),Float.parseFloat(previousFields[2]), Float.parseFloat(previousFields[3]),(sample_timestamp),Float.parseFloat(previousFields[4]));

                        }

                    }

                    previousFields = fields;
                    return;
                }

                //the FSM has found a new peak that represents the peaks group start (step). For this
                //reason counted_steps is incremented and a new element will be inserted in the stepList.
                //That element will contain all the accelerometer values related to that step.
                case Constants.NEW_STEP_FOUND: {
                    counted_steps++;
                    if(temp == null) {
                        stepList.add(counted_steps - 1, new StepAccelerometerValues());
                    }else{
                        stepList.add(counted_steps - 1, temp);
                        temp = null;
                    }

                    if(counted_steps == 1){
                        sample_timestamp = 0;


                    }else{
                        sample_timestamp += Constants.SAMPLING_PERIOD;


                    }


                    stepList.get(counted_steps - 1).addTriplet(Float.parseFloat(previousFields[1]),Float.parseFloat(previousFields[2]), Float.parseFloat(previousFields[3]),(sample_timestamp),Float.parseFloat(previousFields[4]));

                    previousFields = fields;
                    return;
                }

                //Too much time is passed from the ending of the last group of peaks without finding a new step start.
                case Constants.INVALID_WALK_SEGMENT: {
                    stepList.clear();
                    counted_steps = 0;
                    sample_timestamp = 0;
                    temp = null;

                    previousFields = fields;
                    return;
                }

                //In this case the FSM after analyzing the sample is still waiting for the group end.
                //For this reason all the accelerometer values between a peaks group start and a peaks group end
                //are stored in the correspondent element of the stepList
                case Constants.SEARCHING_GROUP_END: {
                    sample_timestamp += Constants.SAMPLING_PERIOD;

                    stepList.get(counted_steps - 1).addTriplet(Float.parseFloat(previousFields[1]),Float.parseFloat(previousFields[2]), Float.parseFloat(previousFields[3]),(sample_timestamp),Float.parseFloat(previousFields[4]));
                    previousFields = fields;
                    return;
                }

                case Constants.FIRST_GROUP_FOUND: {
                    sample_timestamp += Constants.SAMPLING_PERIOD;
                    stepList.get(counted_steps - 1).addTriplet(Float.parseFloat(previousFields[1]),Float.parseFloat(previousFields[2]), Float.parseFloat(previousFields[3]),(sample_timestamp),Float.parseFloat(previousFields[4]));
                    previousFields = fields;
                    return;
                }

                //The FSM after performing the step length test has verified that the last group of peaks
                //cannot be considered as a step. For this reason all the sensed values until that moment
                //are flushed and the variable counting the consecutive steps until that moment is resetted.
                case Constants.STEP_LENGTH_TEST_FALSE: {
                    stepList.clear();
                    counted_steps = 0;
                    sample_timestamp = 0;
                    temp = null;
                    previousFields = fields;
                    return;
                }

                //In this case the FSM has found a group end for a group of peaks, the step length test
                //has been passed but the segment duration test has expressed that the walking segment
                //it is not yet long enough. The sensed accelerometer values are stored and then the system
                //will search for new group of peaks.
                case Constants.SEGMENT_DURATION_TEST_FALSE: {
                    sample_timestamp += Constants.SAMPLING_PERIOD;
                    stepList.get(counted_steps - 1).addTriplet(Float.parseFloat(previousFields[1]),Float.parseFloat(previousFields[2]), Float.parseFloat(previousFields[3]),(sample_timestamp),Float.parseFloat(previousFields[4]));
                    previousFields = fields;
                    return;
                }

                //In this case the step regularity test has not been passed and then the first group of
                //peaks(step) of the walking segment has to be removed searching for a new one.
                case Constants.STEP_REGULARITY_TEST_FALSE: {
                    sample_timestamp += Constants.SAMPLING_PERIOD;
                    stepList.get(counted_steps - 1).addTriplet(Float.parseFloat(previousFields[1]),Float.parseFloat(previousFields[2]), Float.parseFloat(previousFields[3]),(sample_timestamp),Float.parseFloat(previousFields[4]));
                    ArrayList<StepAccelerometerValues> tmp = new ArrayList<>();
                    for(int i=1; i<stepList.size();i++){
                        tmp.add(stepList.get(i));
                    }
                    stepList = tmp;

                    previousFields = fields;
                    return;
                }

                //In this case the FSM tells that a new walking segment has been found.
                case Constants.STEP_REGULARITY_TEST_TRUE: {

                    stepList.get(counted_steps - 1).addTriplet(Float.parseFloat(previousFields[1]),Float.parseFloat(previousFields[2]), Float.parseFloat(previousFields[3]),(sample_timestamp),Float.parseFloat(previousFields[4]));


                    //A new thread is started to remove intensive computations from the main thread
                    //of the application. Before starting filtering and classification thread, sensing
                    //is temporarily stopped.
                    Log.d("Test walking detection","WALKING SEGMENT FOUND!!!");


                    SmartphoneFilteringAndClassificationThread thread = new SmartphoneFilteringAndClassificationThread(stepList,storagePath,ctx,dataClient);
                    thread.start();
                    restartSensing();
                    }

                }


            }

    }


    /**
     * This method is called by the thread demanded to the execution of filtering and classification.
     * In particular that method is called when:
     * - AutoCorrelation filtering gives negative results (irregular segment);
     * - Authentication fails;
     * - Authentication success.
     * The method cleans the old stepList, resets the counted_steps and sensedValues variables and
     * creates a new FSM(same effect of resetting the parameters of the old one) in order to start
     * the searching of a new walking segment.
     */
    public void restartSensing(){

        synchronized(ResetLock){
            sample_timestamp = 0;
            stepList.clear();
            counted_steps = 0;
            sensedValues = 0;
            //new FSM created instead resetting the old one. The old object will be destroyed
            //by the garbage collector.
            wFSM = new WalkDetectorFSM();
        }

    }









    public void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    Constants.CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    public Notification updateNotification(String text,int id, boolean start){
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        Notification not = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            not = new Notification.Builder(this,"ForegroundServiceChannel")
                    .setContentTitle("Title")
                    .setContentText(text)
                    .setSmallIcon(R.drawable.icon)
                    .setContentIntent(pendingIntent)
                    .setTicker("abc")
                    .build();
        }

        if(!start) {
            NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(id, not);
        }

        return not;
    }




}