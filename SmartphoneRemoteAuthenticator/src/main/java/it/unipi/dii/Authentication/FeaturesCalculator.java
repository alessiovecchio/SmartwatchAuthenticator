package it.unipi.dii.Authentication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FeaturesCalculator {

    public static float Max(List<Float> vector){
        return Collections.max(vector);
    }

    public static float Min(List<Float> vector){
        return Collections.min(vector);
    }

    public static float Median(List<Float> vector){
        ArrayList<Float> sorted = new ArrayList<>(vector);
        Collections.sort(sorted);
        if(sorted.size()%2 == 0){
            int median_index = sorted.size()/2;
            return (sorted.get(median_index-1)+sorted.get(median_index))/2;
        }else{
            int median_index = ((sorted.size()+1)/2)-1;
            return sorted.get(median_index);
        }

    }

    public static float Mean(List<Float> vector){
        float sum = 0.0f;
        for(float d: vector){
            sum +=d;
        }
        return sum/vector.size();
    }

    public static float stdDev(List<Float> vector, float avg){

        float stDev = 0.0f;
        float num = 0.0f;
        for(float d: vector){
            num += Math.pow((d-avg),2);
        }
        stDev = (float) Math.sqrt(num/(vector.size()-1));
        return stDev;


    }

    public static float Kurtosis(List<Float> vector,float avg,float stDev){
        float num = 0.0f;
        float n = vector.size();
        float sum = 0.0f;
        float kurtosis = 0.0f;
        double avg_pow = Math.pow(avg,2);

        for(float d: vector){
            num = (d-avg)/stDev;
            sum += Math.pow(num,4);
        }

        sum *= n;
        sum *= (n+1);
        sum /= (n-1);
        sum /= (n-2);
        sum /= (n-3);
        float membro = (float) (Math.pow(n-1,2)*3);
        membro /= (n-2);
        membro /= (n-3);
        kurtosis = sum - membro;
        return kurtosis;

    }

    public static float Skewness(List<Float> vector,float avg,float stDev){
        float num = 0.0f;
        float sum = 0.0f;
        float n = vector.size();
        for(float d: vector){
            num = (d-avg)/stDev;
            sum += Math.pow(num,3);
        }
        float skewness = (sum*n)/((n-1)*(n-2));
        return skewness;
    }

    public static float MeanCrossingRate(List<Float> vector, float avg){
        float num = 0.0f;
        for(int i=1; i<vector.size();i++){
            num += Math.abs(Math.signum(vector.get(i)-avg)-Math.signum(vector.get(i-1)-avg));
        }
        return num/2;

    }

    public static float RootMeanSquare(List<Float> vector){
        float sum_pow = 0.0f;
        for(float d:vector){
            sum_pow += Math.pow(d,2);
        }
        return (float) Math.sqrt(sum_pow/vector.size());
    }

    public static float AverageAbsoluteVariation(List<Float> vector){
        float sum = 0.0f;
        for(int i=0; i<vector.size()-1;i++){
            sum += Math.abs(vector.get(i+1)+vector.get(i));
        }
        return sum/vector.size();
    }

    public static float Module(float x,float y,float z){
        return (float)Math.sqrt(Math.pow(x,2)+Math.pow(y,2)+Math.pow(z,2));
    }

    public static double Module(double x,double y,double z){
        return Math.sqrt(Math.pow(x,2)+Math.pow(y,2)+Math.pow(z,2));
    }

}