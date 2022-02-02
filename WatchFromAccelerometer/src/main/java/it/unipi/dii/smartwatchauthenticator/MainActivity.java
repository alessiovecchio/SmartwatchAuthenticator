package it.unipi.dii.smartwatchauthenticator;

import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.util.Log;
import android.view.View;

import androidx.core.content.ContextCompat;

public class MainActivity extends WearableActivity {



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        // Enables Always-on

        /*SensorManager mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        System.out.println(mSensorManager.getSensorList(Sensor.TYPE_ALL).toString());*/
        setAmbientEnabled();
    }

    public void modeHandler(View v){

        String[] viewID = getResources().getResourceName(v.getId()).split("/");
        Log.println(Log.INFO,"MainActivity",viewID[1]);

        Intent i = new Intent(this,SensorHandler.class);

        if(viewID[1].equals("LwdLa")){

            i.putExtra("mode","classify");
            i.putExtra("localWalkingDetection",true);
            i.putExtra("localAuthentication",true);

        }else if(viewID[1].equals("LwdRa")){

            i.putExtra("mode","classify");
            i.putExtra("localWalkingDetection",true);
            i.putExtra("localAuthentication",false);

        }else if(viewID[1].equals("RwdRa")){

            i.putExtra("mode","classify");
            i.putExtra("localWalkingDetection",false);
            i.putExtra("localAuthentication",false);

        }

        ContextCompat.startForegroundService(this,i);

    }

    @Override
    protected void onStop() {
        super.onStop();
    }

}