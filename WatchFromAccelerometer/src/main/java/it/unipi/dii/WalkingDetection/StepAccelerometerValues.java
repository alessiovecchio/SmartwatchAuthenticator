package it.unipi.dii.WalkingDetection;

import java.util.ArrayList;
import java.util.List;

/**
 * Class containing the acceleration values
 */
public class StepAccelerometerValues {

    private List<Float> Ax;
    private List<Float> Ay;
    private List<Float> Az;
    private List<Float> module;
    private List<Long> timestamps;

    public StepAccelerometerValues(){

        Ax = new ArrayList<>();
        Ay = new ArrayList<>();
        Az = new ArrayList<>();
        module = new ArrayList<>();
        timestamps = new ArrayList<>();
    }

    public void addTriplet(float x,float y,float z,long timestamp,float module){

        Ax.add(x);
        Ay.add(y);
        Az.add(z);
        this.module.add(module);
        timestamps.add(timestamp);
    }

    public List<Float> getModules() {
        return module;
    }

    public List<Float> getAx() {
        return Ax;
    }

    public List<Float> getAy() {
        return Ay;
    }

    public List<Float> getAz() {
        return Az;
    }

    public List<Long> getTimestamps() {
        return timestamps;
    }
}
