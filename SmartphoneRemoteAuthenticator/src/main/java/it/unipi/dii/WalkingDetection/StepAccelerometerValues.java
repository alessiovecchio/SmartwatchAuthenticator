package it.unipi.dii.WalkingDetection;

import java.util.ArrayList;

public class StepAccelerometerValues {

    private ArrayList<Float> Ax;
    private ArrayList<Float> Ay;
    private ArrayList<Float> Az;
    private ArrayList<Float> modules;
    private ArrayList<Long> timestamps;

    public StepAccelerometerValues(){

        Ax = new ArrayList<>();
        Ay = new ArrayList<>();
        Az = new ArrayList<>();
        modules = new ArrayList<>();
        timestamps = new ArrayList<>();

    }

    public void addTriplet(float x,float y,float z,long timestamp,float module){

        Ax.add(x);
        Ay.add(y);
        Az.add(z);
        modules.add(module);
        timestamps.add(timestamp);

    }

    public ArrayList<Float> getModules() {
        return modules;
    }

    public ArrayList<Float> getAx() {
        return Ax;
    }

    public ArrayList<Float> getAy() {
        return Ay;
    }

    public ArrayList<Float> getAz() {
        return Az;
    }

    public ArrayList<Long> getTimestamps() {
        return timestamps;
    }
}
