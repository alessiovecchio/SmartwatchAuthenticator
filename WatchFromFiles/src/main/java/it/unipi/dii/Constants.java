package it.unipi.dii;

public class Constants {

    //Returned code Walking detection FSM
    public static final int NEW_STEP_FOUND = 1; //New group of peaks start found
    public static final int NO_STEP_FOUND = -1; //No start of new group of peaks found
    public static final int SEARCHING_GROUP_END = 2; //Group of peaks end still not found
    public static final int FIRST_GROUP_FOUND = 3;
    public static final int STEP_LENGTH_TEST_FALSE = -2; //Step length test not passed
    public static final int SEGMENT_DURATION_TEST_FALSE = -3; //Segment duration test not passed
    public static final int STEP_REGULARITY_TEST_FALSE = -4; //Step regularity test not passed
    public static final int STEP_REGULARITY_TEST_TRUE = 4; //Walking segment found
    public static final int INVALID_WALK_SEGMENT = -5; //Walking segment invalid. New group of peaks start not found for too much time

    //Walking detection FSM parameters
    public static final int GROUP_START_SEARCH = 0; //State in which the FSM search for a new group of peaks start
    public static final int GROUP_END_SEARCH = 1; //State in which the FSM is searching for new peaks until the group end is found.

    public static final int STEPDMIN = 325;//345; //Minimum step duration (ms)
    public static final int STEPDMAX = 1000;//850; //Maximum step duration (ms)
    public static final float PEAK_THR = 1.2f;//1.18; //Corresponds to 1.2g (has to be adapted to smartwatch case since peaks lower)
    public static final int GROUP_MAX_DUR = 400; //Maximum duration for a group of peaks (ms)
    public static final int GROUP_MAX_INTERVAL = 240;//230; //Maximum interval between 2 consecutive peaks in a group (ms)
    public static final int STEPS_RSD_MAX = 16; //Maximum RelativeSTD % among step durations (tested for both odd and even steps)
    public static final int MIN_NUM_STEPS = 7; //Minimum number of steps in a walking segment
    public static final int MAX_WALK_SEGMENT_DURATION = 8000; //Maximum duration od a walking segment (ms).
    public static final int RING_SIZE = 4096; //Dimension of the Circular buffer used by the FSM to store the module/timestamp values

    public static final float SAMPLING_FREQUENCY = 25; //Sampling frequency (Hz)
    public static final int SAMPLING_PERIOD = 40; //(ms)
    public static final float MAX_CYCLE_DURATION = 1.5f;//1.35; //Maximum duration of a gait cycle (2 steps) determined empirically

    //Autocorrelation filter parameters
    public static final float MIN_AC_DP2 = 0.87f;
    public static final float MAX_AC_DP2 = 1.35f;
    public static final float MIN_AC_DP1 = 0.30f;
    public static final float MAX_AC_DP1 = 0.75f;

    public static final int FEATURE_VECT_DIM = 19;

    public static final int BLUETOOTH_LINE_SIZE = 24;

    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    public static final String COMMUNICATION_PATH = "/communicationData";
    public static final String NOTIFICATION_RESULT_PATH = "/notificationResult";

    public static final int TEST_SET_SIZE = 100;
    public static final int TRAINING_SET_SIZE = 10000;




}
