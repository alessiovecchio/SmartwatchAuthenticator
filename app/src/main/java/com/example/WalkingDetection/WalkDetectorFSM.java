package com.example.WalkingDetection;

import android.util.Log;

import com.example.Authentication.FeaturesCalculator;
import com.example.Constants;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import uk.me.berndporr.iirj.Butterworth;

public class WalkDetectorFSM {


    private int head; //the index in the ring buffer which points to the next element to be filled.
    private int groups_found; //Contains the current number of group of peaks found.

    private float[] moduleRingBuffer; //This array implements the ring buffer where the FSM will receive the module values from the calling method
    private long[] moduleTimestampRingBuffer; //This array implements the ring buffer where the FSM will receive the module timestamps from the calling method
    private long[] stepStartTime; //This array will contain all the start time of each step.
    private long[] stepEndTime; //This array will contain all the end time of each step.
    private long[] stepCentreTime; //This array will contain all the end time of each step.
    private long[] stepLength; //This array will contain the step Length.
    private long lastGroupStart; //This variable is used to store the start timestamp of the last group of peaks
    private int index_lastGroup_start; //This variable contains the index in the ring buffer of the start timestamp/module value
    private long lastGroupEnd; //This variable is used to store the end timestamp of the last group of peaks
    private int index_lastGroup_end; //This variable contains the index in the ring buffer of the end timestamp/module value

    private int[] gStarts;
    private int[] gEnds;
    private int[] gTimes;
    private int[] gSteps;

    private int State; //This variable contains the state of the FSM

    public WalkDetectorFSM(){

        head = 0;
        groups_found = 0;
        State = Constants.GROUP_START_SEARCH;
        moduleRingBuffer = new float[Constants.RING_SIZE];
        moduleTimestampRingBuffer = new long[Constants.RING_SIZE];
        stepStartTime = new long[Constants.MIN_NUM_STEPS+1];
        stepCentreTime = new long[Constants.MIN_NUM_STEPS+1];
        stepEndTime = new long[Constants.MIN_NUM_STEPS+1];
        stepLength = new long[Constants.MIN_NUM_STEPS];

        gStarts = new int[8];
        gEnds = new int[8];
        gTimes = new int[8];
        gSteps = new int[7];

    }

    /**
     * This method is used to store inside the ring buffer of the FSM the module values of the accelerometer
     * samples.
     * @param value : module value to has to be stored inside the ring buffer.
     * @param timestamp : the timestamp related to the module value that has to be stored.
     */
    public void addToBuffer(float value, long timestamp){

        moduleRingBuffer[head] = value;
        moduleTimestampRingBuffer[head] = timestamp;
        head = (head + 1 ) % Constants.RING_SIZE;

    }

    /**
     * This method implements the Finite State Machine whose behaviour represent the Walking Detection algorithm.
     * Each time a new accelerometer sample is available that method is called passing to it (through the ring
     * buffer of the FSM) the module value that has to be analyzed. The FSM analysing a set of
     * accelerometer modules will tell if a walking segment is found or not.
     * @return: an integer representing the walking segment search status.
     *
     */
    public int walkingDetection(){

        //Here i'm extracting the 3 module values used to find a peak (current module, previous and next)
        float analyzed_module = moduleRingBuffer[(head + Constants.RING_SIZE -2)%Constants.RING_SIZE];
        float prev_module = moduleRingBuffer[(head + Constants.RING_SIZE -3)%Constants.RING_SIZE];
        float next_module = moduleRingBuffer[(head + Constants.RING_SIZE -1)%Constants.RING_SIZE];
        int current_index = (head + Constants.RING_SIZE -2)%Constants.RING_SIZE;

        if(State == Constants.GROUP_START_SEARCH){

            //i'm searching for a new peak group start evaluating the "peak definition" condition
            //If the condition is verified i will store the start time of the group passing after to te group end search state.
            if(analyzed_module > prev_module && analyzed_module >= next_module && analyzed_module >= Constants.PEAK_THR){

                State = Constants.GROUP_END_SEARCH;
                lastGroupStart = moduleTimestampRingBuffer[current_index];
                lastGroupEnd = moduleTimestampRingBuffer[current_index];
                index_lastGroup_start = index_lastGroup_end = current_index;

                return Constants.NEW_STEP_FOUND;

            }

            //If i'm here means that a new group start has not been found. For this reason if i have already
            //found some steps, i have to check if is passed too much time. In that case i have to clear all
            //the groups of peaks information.
            if(groups_found > 0){
                //long elapsed = moduleTimestampRingBuffer[current_index] - lastGroupEnd;
                long elapsed = ((current_index-index_lastGroup_end+Constants.RING_SIZE) % Constants.RING_SIZE) * Constants.SAMPLING_PERIOD;
                //Log.d("debug","elapsed: "+elapsed+", timestamp module: "+moduleTimestampRingBuffer[current_index]+",lastgroupend: "+lastGroupEnd);
                if( elapsed > Constants.STEPDMAX ){
                    groups_found = 0;
                    return Constants.INVALID_WALK_SEGMENT;
                }

            }

            return Constants.NO_STEP_FOUND;

        }else if( State == Constants.GROUP_END_SEARCH ){

            //I'm looking for a new peak to be inserted in the group. If founded insert it
            if ( analyzed_module > prev_module && analyzed_module >= next_module && analyzed_module >= Constants.PEAK_THR ){
                lastGroupEnd = moduleTimestampRingBuffer[current_index];
                index_lastGroup_end = current_index;
                return Constants.SEARCHING_GROUP_END;
            }

            //If i'm here means that i have not found a new peak, so i have to check if it's passed
            //too much time since the last peak or since the group start.
            //long elapsed_since_last_peak = moduleTimestampRingBuffer[current_index] - lastGroupEnd;
            //long elapsed_since_group_start = moduleTimestampRingBuffer[current_index] - lastGroupStart;
            long elapsed_since_last_peak = ((current_index-index_lastGroup_end+Constants.RING_SIZE) % Constants.RING_SIZE) * Constants.SAMPLING_PERIOD;
            long elapsed_since_group_start = ((current_index-index_lastGroup_start+Constants.RING_SIZE) % Constants.RING_SIZE) * Constants.SAMPLING_PERIOD;

            //Log.d("debug","elapsed since last peak: "+elapsed_since_last_peak+",elapsed since group start: "+elapsed_since_group_start+", timestamp module: "+moduleTimestampRingBuffer[current_index]+",lastgroupstart: "+lastGroupStart+",lastgroupend: "+lastGroupEnd);

            //if the condition is true means that the group is terminated and i have to store
            //all the information related to the group.
            if( elapsed_since_group_start >= Constants.GROUP_MAX_DUR || elapsed_since_last_peak >= Constants.GROUP_MAX_INTERVAL){

                stepStartTime[groups_found] = lastGroupStart;
                stepEndTime[groups_found] = lastGroupEnd;

                gStarts[groups_found] = index_lastGroup_start;
                gEnds[groups_found] = index_lastGroup_end;
                gTimes[groups_found] = (index_lastGroup_start +((index_lastGroup_end-index_lastGroup_start+Constants.RING_SIZE)%Constants.RING_SIZE)/2)%Constants.RING_SIZE;

                //To extract the middle time of the current group of peaks, first we find the index
                //of the circular buffer in which resides the middle timestamp of the group, then
                //we access to the timestamp circular buffer in the computed index.
                //int middle_index = (index_lastGroup_start +((index_lastGroup_end-index_lastGroup_start+Constants.RING_SIZE)%Constants.RING_SIZE)/2)%Constants.RING_SIZE;
                //long middle_time = moduleTimestampRingBuffer[middle_index];
                //stepCentreTime[groups_found] = middle_index*20;

                groups_found++;

                //When the first group is found there is nothing else to do. Simply search for new steps (No next tests).
                if(groups_found == 1){
                    State = Constants.GROUP_START_SEARCH;
                    return Constants.FIRST_GROUP_FOUND;
                }

                //Now the step length test is performed.
                //long step_dur = stepCentreTime[groups_found-1] - stepCentreTime[groups_found-2];
                int step_dur = ((gTimes[groups_found-1]-gTimes[groups_found-2]+Constants.RING_SIZE)%Constants.RING_SIZE) * Constants.SAMPLING_PERIOD;
                gSteps[groups_found-2] = step_dur;
                stepLength[groups_found-2] = step_dur;
                if(step_dur > Constants.STEPDMAX || step_dur < Constants.STEPDMIN){
                    //Log.d("debug","step duration: "+step_dur);
                    groups_found = 0;
                    State = Constants.GROUP_START_SEARCH;
                    return Constants.STEP_LENGTH_TEST_FALSE;

                }

                //Step length test has been passed. Now i have to perform the segment duration test.
                //In other words we have to check if we have reached the min number of step.
                if(groups_found -1 < Constants.MIN_NUM_STEPS){
                    State = Constants.GROUP_START_SEARCH;
                    return Constants.SEGMENT_DURATION_TEST_FALSE;
                }

                //If i'm here the segment duration test has been passed and then has to be performed
                //the last test: Segment Regularity test.
                boolean test_result = stepRegularityTest();
                if(!test_result){
                    shiftBuffers();
                    State = Constants.GROUP_START_SEARCH;
                    return Constants.STEP_REGULARITY_TEST_FALSE;
                }

                State = Constants.GROUP_START_SEARCH;
                groups_found = 0;


                return Constants.STEP_REGULARITY_TEST_TRUE;


            }
            return Constants.SEARCHING_GROUP_END;


        }

        return Constants.STEP_REGULARITY_TEST_TRUE;
    }


    public static ACFilteringResult autoCorrelationFiltering(ArrayList<Float>acc_x,ArrayList<Float>acc_y,ArrayList<Float>acc_z,ArrayList<Long>timestamps) {

        //First, from the list of steps the complete array of module values is obtained simply
        //concatenating each single module step list.
        ArrayList<Float> concat_modules = new ArrayList<>();
        for (int i = 0; i < acc_x.size(); i++) {
            concat_modules.add(FeaturesCalculator.Module(acc_x.get(i),acc_y.get(i),acc_z.get(i)));
        }

        //Before computing the four features the module vector is filtered using a 2° order Butterworth filter
        Butterworth butterworthFilter = new Butterworth();
        if(Constants.SAMPLING_FREQUENCY > 25) {
            butterworthFilter.lowPass(2, Constants.SAMPLING_FREQUENCY, 20);
            for (int i = 0; i < concat_modules.size(); i++) {
                concat_modules.set(i, (float) butterworthFilter.filter(concat_modules.get(i)));
            }
        }

        //Then the size of that list and the avg value are extracted.
        int size = concat_modules.size();
        float avg = 0;
        for (float d : concat_modules) {
            avg += d;
        }
        avg /= size;

        //Now unbiased autocorrelation coefficients are computed starting from the acceleration module values
        ArrayList<Float> AC_coefficients = new ArrayList<>();
        for (int i = 0; i < Constants.SAMPLING_FREQUENCY * Constants.MAX_CYCLE_DURATION; i++) {

            AC_coefficients.add(i, 0.0f);
            for (int j = 0; j < size - i; j++) {
                AC_coefficients.set(i, AC_coefficients.get(i) + (concat_modules.get(j) - avg) * (concat_modules.get(j + i) - avg));
            }
            AC_coefficients.set(i, AC_coefficients.get(i) / (size - i));

        }

        /*System.out.println("VECTOR AC_COEFF");
        for(int i=0;i<AC_coefficients.size();i++){
            System.out.println(AC_coefficients.get(i));
        }*/

        //Starting from the unbiased autocorrelation coefficients computed above, the 4 AC features are calculated.
        //First condition controlled: having sufficient number of autocorrelation coefficients.
        int AC_Coeff_size = AC_coefficients.size();
        float AC_C2 = -1;
        float AC_DP2 = -1;
        float AC_C1 = -1;
        float AC_DP1 = -1;

        if (AC_Coeff_size < Constants.MAX_AC_DP2 * Constants.SAMPLING_FREQUENCY) {
            ACFilteringResult res = new ACFilteringResult(false,0,0,0,0);
            return res;
        }

        for(float i=Constants.MIN_AC_DP2*Constants.SAMPLING_FREQUENCY; i<Constants.MAX_AC_DP2*Constants.SAMPLING_FREQUENCY; i++){
            float value = AC_coefficients.get((int)i);
            if(value > AC_C2){
                AC_C2 = value;
                AC_DP2 = i/Constants.SAMPLING_FREQUENCY;
            }
        }
        AC_C2 /= AC_coefficients.get(0);

        for(float i=Constants.MIN_AC_DP1*Constants.SAMPLING_FREQUENCY; i<Constants.MAX_AC_DP1*Constants.SAMPLING_FREQUENCY; i++){
            float value = AC_coefficients.get((int)i);
            if(value > AC_C1){
                AC_C1 = value;
                AC_DP1 = i/Constants.SAMPLING_FREQUENCY;
            }
        }
        AC_C1 /= AC_coefficients.get(0);

        //Now we apply the filtering criteria. The first is: the error E computed as |E| = duration - (4*AC_DP2)
        //must be less than a particular threshold Eth. So we extract the duration of the gait segment computing
        //the difference of the timestamp of the last accelerometer value of the segment - the first timestamp.

        long startTimestamp = timestamps.get(0);
        long endTimestamp = timestamps.get(timestamps.size()-1);
        float duration = (endTimestamp - startTimestamp)/1000.0f;

        //We compute the module of the error since it will be:
        //- Negative : if the walk segment duration is too low;
        //- Positive : if the walk segment duration is too high;
        float error = duration - (4 * AC_DP2);
        error = Math.abs(error);

        //If the error is above the pre-determined threshold the gait segment has to be discarded.
        //(That error indicates usually the occurring of spurious hands movements)
        //So an ACFilteringResult object is returned containing a false boolean which states that the filtering
        //has given negative result. When the filtering has negative results the Autocorrelation feature inserted
        //in the result object are meaningless (since they will not be used).


        /*System.out.print("AC_C1="+AC_C1+" AC_DP1="+AC_DP1+" AC_C2="+AC_C2+" AC_DP2="+AC_DP2);
        System.out.println(" Error= "+error+", duration= "+duration);*/

        //error adattato a 0.5 (invece di 0.43) per ridurre il numero di istanze filtrate
        //adattato anche il controllo su AC C2 0.5 invece di 0.6
        if(error > 0.5){
            ACFilteringResult res = new ACFilteringResult(false,0,0,0,0);
            return res;
        }

        //The second error checked is the one related to the AC_C2 coefficient. That error is used to
        //check if the user is walking in according to a consistent pattern. If the AC_C2 coefficient
        //is lower than a specified threshold, the walking segment will be discarded.
        if(AC_C2 < 0.5){
            ACFilteringResult res = new ACFilteringResult(false,0,0,0,0);
            return res;
        }

        //If i reach this point means that the AutoCorrelation filtering has given a positive result.
        ACFilteringResult res = new ACFilteringResult(true,AC_C2,AC_DP2,AC_C1,AC_DP1);
        return res;


    }

    private boolean stepRegularityTest(){

        double rSDeven,rSDodd,avgEven,avgOdd,avgSQeven,avgSQodd;
        avgEven = avgOdd = avgSQeven = avgSQodd = 0.0;
        int even_counter = 0;
        int odd_counter = 0;

        for(int i=0;i<groups_found-1;i++){
            if(i%2==0){
                avgEven += stepLength[i];
                avgSQeven += Math.pow(stepLength[i],2);
                even_counter++;
            }else{
                avgOdd += stepLength[i];
                avgSQodd += Math.pow(stepLength[i],2);
                odd_counter++;
            }
        }

        avgEven = avgEven/even_counter;
        avgOdd = avgOdd/odd_counter;
        avgSQeven = avgSQeven/even_counter;
        avgSQodd = avgSQodd/odd_counter;

        rSDeven = Math.sqrt(avgSQeven - Math.pow(avgEven,2))/avgEven;
        rSDodd = Math.sqrt(avgSQodd - Math.pow(avgOdd,2))/avgOdd;

        if(rSDodd > Constants.STEPS_RSD_MAX || rSDeven > Constants.STEPS_RSD_MAX){
            return false;
        }

        return true;

    }

    private void shiftBuffers(){

        for(int i=0;i < groups_found-1;i++){
            stepStartTime[i] = stepStartTime[i+1];
            stepCentreTime[i] = stepCentreTime[i+1];
            stepEndTime[i] = stepEndTime[i+1];

            if( i < groups_found-2){
                stepLength[i] = stepLength[i+1];
            }

        }

    }


}
