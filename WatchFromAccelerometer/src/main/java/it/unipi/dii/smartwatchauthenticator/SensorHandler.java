package it.unipi.dii.smartwatchauthenticator;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.RequiresApi;

import it.unipi.dii.Authentication.FeaturesCalculator;
import it.unipi.dii.WalkingDetection.StepAccelerometerValues;
import it.unipi.dii.WalkingDetection.WalkDetectorFSM;
import it.unipi.dii.Constants;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

public class SensorHandler extends WearableListenerService implements SensorEventListener{

    private boolean localWalkingDetection; // Boolean used to decide if the system should implement the walking detection remotely (to smartphone) or in locale
    private boolean localAuthentication; //Boolean used to decide if the system should implement the authentication procedure remotely (smartphone) or locally.
    private int sensedValues; // Simple integer used to check if at least 3 values are already sensed
    private WalkDetectorFSM wFSM; // Object that implements the FSM used to execute the waling detection on the values sensed by the accelerometer
    private int counted_steps;  //this variable counts the step detected by the FSM.
    private Object ResetLock; //object implemented to handle the mutual exclusion on step list that could be cleared by the classification thread.
    private String mode; //This string will contain the mode of operation (classify,train,normal_test, abnormal_test)
    private int training_instances_stored; //Counts the number of training instances saved during training phase
    private int testing_instances_stored; //Counts the number of testing instances saved during testing phase
    private Object Lock_counter_instances; //Object used to handle mutual exclusion on the counter of training/testing instances
    private long start_sensingTimestamp;

    //That array is used to keep trace of the 8 different steps by which is composed a walking segment
    //Each element of this list will contain all the accelerometer values (for the 3 axis) related to the corresponding step
    private ArrayList<StepAccelerometerValues> stepList;

    //Those Variables are instead used when the architecture requires remote authentication (on a smartphone) to
    //store accelerometer values for a certain amount of time. The size of that window will be tuned in order to
    //contain at least 8 walking steps.

    private File accFile; //this variable contains the file descriptor of the .csv in which will be stored raw accelerometer data.
    private FileWriter accWriter; //FileWriter used to write accelerometer values on .csv file.
    private StringBuffer AccelerometerData;
    private ByteBuffer BinaryAccData;

    private File storagePath; //Contains the storage path in which will be stored the csv file contain accelerometer data.
    private DataClient dataClient;
    private long activity; //variable used to keep trace of the amount of time elapsed from the starting of acceleromter sensing.
    private long last_timestamp; //timestamp of the last accelerometer sensing.
    private long sample_timestamp; //timestamp of saved accelerometer samples


    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private PowerManager mPowerManager;
    private PowerManager.WakeLock wl;
    private Sensor mStepDetector;
    private Notification notif;

    private File rawFile;
    private BufferedReader rawReader;
    private File instanceFile;
    private FileWriter instanceWriter;
    private SensorEvent previousSample;
    private StepAccelerometerValues temp;
    private String[] previousFields;
    private int counter = 1;
    private long simulation_duration;
    private int user_index = 1;
    private long startClassificationTimestamp;
    private Sensor mLinear;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        super.onStartCommand(intent, flags, startId);

        localWalkingDetection = intent.getBooleanExtra("localWalkingDetection",true);
        localAuthentication = intent.getBooleanExtra("localAuthentication",true);
        mode = intent.getStringExtra("mode");


        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wl = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "SmartawatchAuthenticator::authWakeLock");
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensedValues = 0;
        counted_steps = 0;

        //If localWalkingDetection == true means that the walking detection will be performed locally
        //and for this reason are allocated different data structures w.r.t the else case, in which
        //are instantiated variables in order to collect and send info to the smartphone.
        if(localWalkingDetection) {
            stepList = new ArrayList<>();
            wFSM = new WalkDetectorFSM();
            ResetLock = new Object();
            storagePath = getApplicationContext().getExternalFilesDir(null);
            dataClient = Wearable.getDataClient(this);


        }else{
            //Initialized data structures needed to send raw accelerometer data to smartphone.
            storagePath = getApplicationContext().getExternalFilesDir(null);
            dataClient = Wearable.getDataClient(this);
            activity = 0;
            AccelerometerData = null;
            last_timestamp = System.currentTimeMillis();
            ResetLock = new Object();

        }

        //Created the notification which characterize a Foreground service
        createNotificationChannel();
        notif = updateNotification("Started authenticator application",1,true);
        startForeground(1,notif);

        //Registered listener to start sensing from accelerometer
        if (mAccelerometer != null) {
            mSensorManager.registerListener((SensorEventListener) this, mAccelerometer,Constants.SAMPLING_PERIOD*1000);
        }

        wl.acquire();
        return Service.START_STICKY;
    }


    @RequiresApi(api = Build.VERSION_CODES.O)
    public void onSensorChanged(SensorEvent event) {


        if(localWalkingDetection) {
            executeLocalWalkingDetection(event);
        }else{
            remoteWalkingDetectionClassification(event);
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
     * @param event SensorEvent object from which are extracted raw accelerometer data used for walking detection
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void executeLocalWalkingDetection(SensorEvent event){

        //The finite state machine will not be executed until at least 3 values are available in the ring buffer.
        //This check is done through the sensedValues variable.

        float mod = FeaturesCalculator.Module(event.values[0]/9.81f,event.values[1]/9.81f,event.values[2]/9.81f);
        String line_from_sensors = event.timestamp+","+(event.values[0]/9.81f)+","+(event.values[1]/9.81f)+","+(event.values[2]/9.81f)+","+mod+",";
        if (sensedValues < 2) {


                String line = line_from_sensors;
                String[] fields = line.split(",");
                previousFields = fields;
                wFSM.addToBuffer(mod, (event.timestamp));


                sensedValues++;

        } else {

            // We have sensed at least 3 values. Now the FSM can start checking each single
            // module value trying to find peaks and group of peaks.



            String[] fields = null;

            String line = line_from_sensors;

            fields = line.split(",");
            mod = FeaturesCalculator.Module(Float.parseFloat(previousFields[1]),Float.parseFloat(previousFields[2]),Float.parseFloat(previousFields[3]));

            wFSM.addToBuffer(mod, (event.timestamp));

            int result = wFSM.walkingDetection();

            switch (result){

                //the FSM that is in GROUP_START_SEARCH has not found a Group start and for this
                //reason there is no need to store the related accelerometer values.
                //We use an approach in which all the accelerometer values between the end of the precedent
                //step and the beginning of the next one are not stored.
                case Constants.NO_STEP_FOUND:  {


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

                    if(localAuthentication) {
                        //A new thread is started to remove intensive computations from the main thread
                        //of the application. Before starting filtering and classification thread, sensing
                        //is temporarily stopped.
                        Log.d("Test walking detection","WALKING SEGMENT FOUND!!!");

                        mSensorManager.unregisterListener((SensorEventListener) this, mAccelerometer);
                        FilteringAndClassificationThread thread =
                                new FilteringAndClassificationThread(stepList, this,storagePath,mode,training_instances_stored,testing_instances_stored,this);
                        thread.start();
                        //restartSensing();
                    }else{
                        //If localAuthentication is false means that the walking segment found has to be
                        //sent to the smartphone on which will be performed the classification.
                        Log.d("Test walking detection","WALKING SEGMENT FOUND!!!");
                        mSensorManager.unregisterListener((SensorEventListener) this, mAccelerometer);
                        startClassificationTimestamp = System.currentTimeMillis();
                        sendDataToSmartphone(null);
                    }

                }


            }

        }


    }

    /**
     * That method is executed when walking detection and classification procedures need to be executed remotely
     * on the smartphone. In particular the method is responsible of saving raw accelerometer data on a .csv file
     * that will be sent through Bluetooth to the smartphone when an enough number of samples is obtained.
     * @param event : SensorEvent object from which are extracted raw accelerometer data.
     */
    private void remoteWalkingDetectionClassification(SensorEvent event){

        //if i'm here means that the walking detection and classification must be performed remotely.
        //For this reason each accelerometer sample is sent through bluetooth to the smartphone.

        //String line = null;


       if(BinaryAccData == null){
           BinaryAccData = ByteBuffer.allocate(Constants.BLUETOOTH_LINE_SIZE*(Constants.MAX_WALK_SEGMENT_DURATION/Constants.SAMPLING_PERIOD));
       }

       /*String[] campi = line.split(",");
       float x = Float.parseFloat(campi[1]);
       float y = Float.parseFloat(campi[2]);
       float z = Float.parseFloat(campi[3]);
       long timestamp = (long) Float.parseFloat(campi[0]);
       float module = Float.parseFloat(campi[4]);*/

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[3];
        long timestamp = event.timestamp;
        float module = FeaturesCalculator.Module(x,y,z);

        BinaryAccData.putFloat(x);
        BinaryAccData.putFloat(y);
        BinaryAccData.putFloat(z);
        BinaryAccData.putLong(timestamp);
        BinaryAccData.putFloat(module);



        activity += Constants.SAMPLING_PERIOD;

        //When this condition is verified means that i've collected accelerometer values for an amount of time
        //that could contain an entire walking segment. So data can be sent to the smartwatch using sendDataToSmartphone()
        if(activity >= Constants.MAX_WALK_SEGMENT_DURATION){
            //mSensorManager.unregisterListener((SensorEventListener) this, mAccelerometer);
            //byte[] bytes = AccelerometerData.toString().getBytes();
            byte[] bytes = BinaryAccData.array();
            Asset as = Asset.createFromBytes(bytes);
            sendDataToSmartphone(as);
            activity = 0;
            AccelerometerData = null;
            BinaryAccData = null;

        }


    }

    private Asset toAsset(String file_name){
        File f = new File(storagePath,file_name);
        int dim = (int)f.length();
        byte[] bytes = new byte[dim];
        try{
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(f));
            buf.read(bytes, 0, bytes.length);
            buf.close();
        }catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        Asset as = Asset.createFromBytes(bytes);
        f.delete();
        return as;

    }

    private void writeStepValuesToCSV(){
        for(int i=0; i<stepList.size();i++){
            File stepFile = new File(storagePath,"step"+i+".csv");
            FileWriter stepWriter;
            try {
                stepWriter = new FileWriter(stepFile);
                int step_size = stepList.get(i).getAx().size();
                StepAccelerometerValues step = stepList.get(i);
                for(int j=0; j<step_size;j++){
                    String data = step.getAx().get(j)+","+step.getAy().get(j)+","+step.getAz().get(j)+","+step.getTimestamps().get(j)+","+step.getModules().get(j)+"\n";
                    stepWriter.append(data);
                }
                stepWriter.close();
            }catch(IOException io){
                io.printStackTrace();
                Log.d("SensorHandler","Error during writing of step files");
            }

        }

    }

    /**
     * This method is executed when there is the needing to send information to the smartphone.
     * There are 2 possible cases, handled by the method, in which we have to send data to smartphone:
     * - When localWalkingDetection and localAuthentication are both false -> we have to send .csv files
     *   containing raw accelerometer data;
     * - When localWalkingDetection is true and localAuthentication is false means that on the smartphone
     *   will be performed only the classification, so will be sent to smartphone the result of the walking
     *   detection algorithm.
     */
    public void sendDataToSmartphone(Asset accAsset){

        //This is the case in which we have to send raw accelerometer data to smartphone to perform
        //walking detection and classification remotely.
        if(!localWalkingDetection && !localAuthentication) {
            if (accAsset != null) {
                dataClient.deleteDataItems(Uri.parse(Constants.COMMUNICATION_PATH));
                PutDataMapRequest putDMR = PutDataMapRequest.create(Constants.COMMUNICATION_PATH);
                //putDMR.getDataMap().putString("Command","TotalRemote"+System.currentTimeMillis());
                Long tsLong = System.currentTimeMillis() / 1000;
                String timestamp = tsLong.toString();
                putDMR.getDataMap().putString("Timestamp", timestamp);
                putDMR.getDataMap().putAsset("Accelerometer", accAsset);
                PutDataRequest putDR = putDMR.asPutDataRequest();
                putDR.setUrgent();
                Task<DataItem> putTask = dataClient.putDataItem(putDR);

                putTask.addOnSuccessListener(
                        new OnSuccessListener<DataItem>() {
                            @Override
                            public void onSuccess(DataItem dataItem) {
                                Log.d("SensorHandler", "Accelerometer sample successfully sent: " + dataItem);
                            }
                        });

            } else {
                Log.d("SensorHandler", "Error during the sending of the .csv file");
            }
         //In this case only the classification procedure must be performed remotely.
        }else if(localWalkingDetection && !localAuthentication){

            PutDataMapRequest putDMR = PutDataMapRequest.create(Constants.COMMUNICATION_PATH);
            //putDMR.getDataMap().putString("Command","ClassificationRemote"+System.currentTimeMillis());
            Long tsLong = System.currentTimeMillis() / 1000;
            String timestamp = tsLong.toString();
            putDMR.getDataMap().putString("Timestamp", timestamp);
            for(int i=0;i<stepList.size();i++){
                Asset stepAsset = null;

                int step_size = stepList.get(i).getAx().size();
                int buffer_dim = step_size * Constants.BLUETOOTH_LINE_SIZE;
                StepAccelerometerValues step = stepList.get(i);

                ByteBuffer bb = ByteBuffer.allocate(buffer_dim);

                for(int j=0; j<step_size;j++){

                    bb.putFloat(step.getAx().get(j));
                    bb.putFloat(step.getAy().get(j));
                    bb.putFloat(step.getAz().get(j));
                    bb.putLong(step.getTimestamps().get(j));
                    bb.putFloat(step.getModules().get(j));

                }

                byte[] buffer = bb.array();

                stepAsset = Asset.createFromBytes(buffer);


                if (stepAsset != null) {
                    putDMR.getDataMap().putAsset("step"+i, stepAsset);
                }
            }
            PutDataRequest putDR = putDMR.asPutDataRequest();
            putDR.setUrgent();
            dataClient = Wearable.getDataClient(this);
            Task<DataItem> putTask = dataClient.putDataItem(putDR);

            putTask.addOnSuccessListener(
                    new OnSuccessListener<DataItem>() {
                        @Override
                        public void onSuccess(DataItem dataItem) {
                            Log.d("SensorHandler", "Step data successfully sent: " + dataItem);


                        }
                    });

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
            last_timestamp = System.currentTimeMillis();
            activity = 0;
            //new FSM created instead resetting the old one. The old object will be destroyed
            //by the garbage collector.
            wFSM = new WalkDetectorFSM();
            mSensorManager.registerListener((SensorEventListener) this, mAccelerometer,Constants.SAMPLING_PERIOD*1000);
        }

    }



    @Override
    /**
     * This method is used to handle the reception of a message from the smartphone through Bluetooth.
     * In particular that message will contain the result of the classification (authentication) procedure.
     *
     *
     */
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        Log.d("SensorHandler", "Message Received");
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().compareTo(Constants.NOTIFICATION_RESULT_PATH) == 0) {
                DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                if (dataMap.isEmpty()) {
                    Log.d("SensorHandler", "DataMap vuoto");
                } else {
                    String result = dataMap.getString("AuthenticationResult");
                    if(result.contains("Walking segment filtered")){
                        Log.d("debug","SEGMENTO DI CAMMINATA FILTRATO");
                        updateNotification("Segmento filtrato!",4,false);
                        if(localWalkingDetection && !localAuthentication) {
                            restartSensing();
                        }
                    }
                    else if(result.contains("Authentication success")){
                        Log.d("debug","AUTENTICAZIONE REMOTA RIUSCITA CON SUCCESSO");
                        updateNotification("Autenticazione remota riuscita!",4,false);
                        if(localWalkingDetection && !localAuthentication) {
                            restartSensing();
                        }
                    }else if(result.contains("Authentication failed")){
                        Log.d("debug","AUTENTICAZIONE REMOTA NON RIUSCITA");
                        updateNotification("Autenticazione remota NON riuscita!",4,false);
                        if(localWalkingDetection && !localAuthentication) {
                            restartSensing();
                        }
                    }

                }


            }
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }




}