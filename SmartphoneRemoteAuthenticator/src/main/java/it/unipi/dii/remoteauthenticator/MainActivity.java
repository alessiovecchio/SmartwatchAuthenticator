package it.unipi.dii.remoteauthenticator;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.os.Bundle;

import it.unipi.dii.remoteauthenticator.R;

import it.unipi.dii.remoteauthenticator.SmartwatchDataReceiver;

public class MainActivity extends AppCompatActivity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Intent i = new Intent(this, SmartwatchDataReceiver.class);
        ContextCompat.startForegroundService(this,i);

    }





}