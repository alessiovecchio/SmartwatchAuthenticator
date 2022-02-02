package it.unipi.dii.Authentication;

import it.unipi.dii.Constants;
import it.unipi.dii.WalkingDetection.ACFilteringResult;

import uk.me.berndporr.iirj.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class AnomalyDetectionSystem {


    /**
     * This method is used to preprocess the 3 vectors (x,y,z) of accelerometer values applying on
     * them a low-pass second order Butterworth filter.
     * @param acc_x: contains accelerometer values for x axis.
     * @param acc_y: contains accelerometer values for y axis.
     * @param acc_z: contains accelerometer values for z axis.
     * @param timestamps: contains the timestamp of the collection time of the accelerometer samples
     * @return :the method returns a PreprocessingResult object which will contain the 3 vectors of accelerometer
     * data filtered + the vector with module values.
     */
    public static PreprocessingResult preProcessing(ArrayList<Float>acc_x,ArrayList<Float>acc_y,ArrayList<Float>acc_z,ArrayList<Long>timestamps){

        //Concatenation of the lists of accelerometer values collected for each step
        PreprocessingResult res = new PreprocessingResult();

        res.acc_x.addAll(acc_x);
        res.acc_y.addAll(acc_y);
        res.acc_z.addAll(acc_z);


        //In order to preprocess our sample data we have to apply a second-order low-pass Butterworth filter
        //with a cut-off frequency of 20 Hz on each of the 3 vectors (Acc_x, Acc_y, Acc_z)
        Butterworth butterworthFilter = new Butterworth();
        if(Constants.SAMPLING_FREQUENCY > 25) {
            butterworthFilter.lowPass(2, Constants.SAMPLING_FREQUENCY, 20);
        }

        //Once the filter has been created and initialised the "real-time" filter (sample per sample) can be started

        for(int i=0; i<res.acc_x.size();i++){
            if(Constants.SAMPLING_FREQUENCY > 25) {
                res.acc_x.set(i, (float) butterworthFilter.filter(res.acc_x.get(i)));
                res.acc_y.set(i, (float) butterworthFilter.filter(res.acc_y.get(i)));
                res.acc_z.set(i, (float) butterworthFilter.filter(res.acc_z.get(i)));
            }

            //The module values are re-computed after the Butterworth filter executed on the 3 axis vectors
            float mod = FeaturesCalculator.Module(res.acc_x.get(i),res.acc_y.get(i),res.acc_z.get(i));
            res.modules.add(i,mod);
        }

        return res;

    }

    /**
     * This method is used to create different folds in order to validate our model using the CV when test set
     * is not available.
     * @param n_folds: contains the number of folds we want to obtain.
     * @return An arrayList of String where each String describes a fold containing the indexes of the training
     * dataset that are used as training instance in that CV fold.
     */
    public static ArrayList<String> createFolds(int n_folds){

        ArrayList<String> result_folds = new ArrayList<>();
        ArrayList<Integer> extractedInt = new ArrayList<>();
        Random rand = new Random();
        int upperBound = n_folds;

        while(extractedInt.size()!=n_folds){

            int int_random = rand.nextInt(upperBound);

            if(!extractedInt.contains(int_random)){
                extractedInt.add(int_random);
                String new_fold = int_random+",";

                for(int i=0;i<n_folds;i++){

                    if(i!=int_random){
                        new_fold+=i+";";
                    }

                }

                result_folds.add(new_fold);


            }

        }
        return result_folds;

    }

    /**
     * This method is used to perform the model validation using the CV approach when a test set is not
     * available. In this way we exploit some instances of the training set as validation one in order to
     * obtain some information related to the FRR.
     * @param train_set: represents the entire train set that will be used for CV.
     * @param abnormal_set: represents the entire abnormal test set, containing all the instances of abnormal
     *                    users.
     * @param folds: contains the ArrayList of String that will be used to discriminate the different folds
     *             used during the CV.
     * @return a Map which contains the Anomaly scores of the normal/abnormal instances that can be used
     *         to create a ROC curve.
     */
    public static Map<String,ArrayList<Float>> Model_Cross_Validation(ArrayList<float[]>train_set, ArrayList<float[]>abnormal_set, ArrayList<String>folds){

        Map<String,ArrayList<Float>> result = new HashMap<>();

        ArrayList<Float> normal_th = new ArrayList<>();
        ArrayList<Float> normal_distances = new ArrayList<>();
        ArrayList<Float> normal_AS = new ArrayList<>();

        ArrayList<Float> abnormal_th = new ArrayList<>();
        ArrayList<Float> abnormal_distances = new ArrayList<>();
        ArrayList<Float> abnormal_AS = new ArrayList<>();

        ArrayList<Float> avg_abnormal_AS = new ArrayList<>();
        for(int i=0;i< abnormal_set.size();i++){
            avg_abnormal_AS.add(0.0f);
        }

        for(int i=0;i<folds.size();i++){

            String fold = folds.get(i);
            String[] tmp = fold.split(",");
            int index_validation_instance = Integer.parseInt(tmp[0]);
            float[] validation_instance = train_set.get(index_validation_instance);
            ArrayList<float[]> currentFold_ValidInst = new ArrayList<>();
            currentFold_ValidInst.add(validation_instance);

            ArrayList<float[]>currentFold_trainingInst = new ArrayList<>();

            for(int j=0;j<train_set.size();j++){

                if(j!=index_validation_instance){
                    currentFold_trainingInst.add(train_set.get(j));
                }

            }

            //normalize the dataset
            normalization_byCVFold(currentFold_trainingInst,currentFold_ValidInst,abnormal_set);


            ArrayList<Float> training_distances = get_NN_sumDistances(currentFold_trainingInst,null);

            float avg_TD = FeaturesCalculator.Mean(training_distances);
            float st_dev_T = FeaturesCalculator.stdDev(training_distances,avg_TD);


            float AD_TH = selectAnomalyDetectionThreshold(training_distances,"spec",1.0f,avg_TD,st_dev_T);

            //System.out.println(AD_TH);
            normal_th.add(AD_TH);

            ArrayList<Float> valid_distance = get_NN_sumDistances(currentFold_ValidInst,currentFold_trainingInst);
            normal_AS.add(getAnomalyScore(avg_TD,st_dev_T,valid_distance).get(0));


            normal_distances.add(valid_distance.get(0));


            ArrayList<Float> currentFold_abnormal_distances = get_NN_sumDistances(abnormal_set,currentFold_trainingInst);
            ArrayList<Float> tmp_abnormal_AS = getAnomalyScore(avg_TD,st_dev_T,currentFold_abnormal_distances);
            for(int k=0;k<currentFold_abnormal_distances.size();k++){
                abnormal_th.add(AD_TH);
                abnormal_distances.add(currentFold_abnormal_distances.get(k));
                abnormal_AS.add(tmp_abnormal_AS.get(k));
                //System.out.println(tmp_abnormal_AS.get(k));
                avg_abnormal_AS.set(k,avg_abnormal_AS.get(k)+tmp_abnormal_AS.get(k));
            }
            //System.out.println("FINE AS PER FOLD----");


        }

        for(int k=0;k<avg_abnormal_AS.size();k++){
            float iteration_number = folds.size();
            avg_abnormal_AS.set(k,avg_abnormal_AS.get(k)/iteration_number);
        }

        float normal_filtered = 0;
        float normal_ratio;
        for(int i=0;i<normal_distances.size();i++){

            if(normal_distances.get(i)>normal_th.get(i)){
                normal_filtered++;
            }


        }
        float size = normal_distances.size();
        normal_ratio = normal_filtered/size;

        //System.out.println("RESULTS NORMAL CLASS: % istanze corrette mal classificate = "+normal_ratio+" ACCURACY = "+(1-normal_ratio));

        float abnormal_filtered = 0;
        float abnormal_ratio = 0.0f;

        for(int i=0;i<abnormal_distances.size();i++){
            if(abnormal_distances.get(i)>abnormal_th.get(i)){
                abnormal_filtered++;
            }

        }
        size = abnormal_distances.size();
        abnormal_ratio = abnormal_filtered/size;

        float abn_accuracy = ((1-normal_ratio)+abnormal_ratio)/2;

        //System.out.println("RESULTS ABNORMAL CLASS: % istanze anormali corret classif = "+abnormal_ratio+" ACCURACY SISTEMA = "+abn_accuracy);


        result.put("normalAS",normal_AS);
        result.put("abnormalAS",avg_abnormal_AS);
        return result;

    }

    /**
     * This method is fundamental in order to achieve good classification results. In particular, that method
     * performs a normalization of all the datasets (training and validation) using an approach based on max/min
     * values of features within the instances of the training set.
     * @param tr_set
     * @param normal_valid_inst
     * @param abnormal_set
     */
    private static void normalization_byCVFold(ArrayList<float[]>tr_set,ArrayList<float[]>normal_valid_inst,ArrayList<float[]>abnormal_set) {

        int feature_size = tr_set.get(0).length;
        float[] max = new float[feature_size];
        float[] min = new float[feature_size];

        for(int i=0; i<feature_size;i++){
            max[i] = Float.MIN_VALUE;
            min[i] = Float.MAX_VALUE;
        }

        for(int i=0;i<tr_set.size();i++){

            float[] instance = tr_set.get(i);
            for(int j=0;j<instance.length;j++){

                if(instance[j] >= max[j]){
                    max[j] = instance[j];
                }

                if(instance[j] <= min[j]){
                    min[j] = instance[j];
                }
            }
        }

        for(int i=0;i< tr_set.size();i++){

            float[] instance = tr_set.get(i);
            for(int j=0;j< instance.length;j++){
                instance[j] = (instance[j] - min[j])/(max[j] - min[j]);
            }
            tr_set.set(i,instance);

        }

        for(int i=0;i< normal_valid_inst.size();i++){

            float[] instance = normal_valid_inst.get(i);
            for(int j=0;j< instance.length;j++){
                instance[j] = (instance[j] - min[j])/(max[j] - min[j]);
            }
            normal_valid_inst.set(i,instance);

        }

        for(int i=0;i< abnormal_set.size();i++){

            float[] instance = abnormal_set.get(i);
            for(int j=0;j< instance.length;j++){
                instance[j] = (instance[j] - min[j])/(max[j] - min[j]);
            }
            abnormal_set.set(i,instance);

        }


    }


    /**
     * This method is used to compute the anomaly score for the validation instances (normal and abnormal)
     * using the formula described in the paper. Each anomaly score is obtained taking the NN distance of
     * the gait instance, subtracting to it the avg_td param and then diving all by the std_td.
     * @param avg_TD
     * @param std_dev_T
     * @param distances
     * @return
     */
    private static ArrayList<Float> getAnomalyScore(float avg_TD, float std_dev_T, ArrayList<Float>distances){

        ArrayList<Float> AnomalyScore = new ArrayList<>();
        for(int i=0;i<distances.size();i++){
            float as = (distances.get(i)-avg_TD)/std_dev_T;
            //System.out.println("distanza = "+distances.get(i)+", avg = "+avg_TD+",std = "+std_dev_T+", as = "+as);
            AnomalyScore.add(i,as);
        }
        return AnomalyScore;


    }

    /**
     * This method instead computes the ROC curve of the system. In particular, using several values of AD
     * threshold, computes different couple of values (FRR,1-FMR) that will represent the point of the curve.
     * @param normal_AS
     * @param abnormal_AS
     * @param user_index
     */
    public static void getROCcurve(ArrayList<Float>normal_AS,ArrayList<Float>abnormal_AS, int user_index){

        float minTH = 0.0f;
        float maxTH = 0.0f;
        float step = 0.1f;
        FileWriter writer = null;

        try {
            writer = new FileWriter("/home/raff/Scrivania/ROC/ROC_X-u"+user_index+".csv",true);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String intestazione = "TH; TPR (1-FMR); FPR (FRR)\n";
        try {
            writer.write(intestazione);
        } catch (IOException e) {
            e.printStackTrace();
        }

        float min_allAS = FeaturesCalculator.Min(normal_AS);
        if(FeaturesCalculator.Min(abnormal_AS) < min_allAS){
            min_allAS = FeaturesCalculator.Min(abnormal_AS);
        }

        float max_allAS = FeaturesCalculator.Max(normal_AS);
        if(FeaturesCalculator.Max(abnormal_AS) > max_allAS){
            max_allAS = FeaturesCalculator.Max(abnormal_AS);
        }

        minTH = min_allAS ;
        maxTH = max_allAS ;

        for(float th = minTH; th<maxTH; th = th+step){

            float count_TP = 0.0f;
            float filtered_TP = 0.0f;
            float TPR = 0.0f;
            int i = 0;
            for(float score: abnormal_AS){

                /*if(th == minTH) {
                    i++;
                    System.out.println("index = " + i + ", score = " + score);
                }*/
                count_TP++;
                if(score >= th){
                    filtered_TP++;
                }

            }
            TPR = filtered_TP/count_TP;

            float count_FP = 0.0f;
            float filtered_FP = 0.0f;
            float FPR = 0.0f;
            for(float score: normal_AS){
                count_FP++;
                if(score >= th){
                    filtered_FP++;
                }

            }
            FPR = filtered_FP/count_FP;

            String line = ""+th+";"+TPR+";"+FPR+"\n";
            try {
                writer.write(line);
            } catch (IOException e) {
                e.printStackTrace();
            }


        }
        try {
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    /**
     * This method is used to extract from the input vector the following list of features according
     * the feature selection mechanism adopted in the reference paper.
     * - AAV
     * - Min
     * - Max
     * - MCR (Mean crossing rate)
     * - Mean
     * - median
     * - RMS (Root mean square)
     * - skewness
     * @param res: the vector containing accelerometer values (for a specific axis + module) from which
     *                  will be extracted the features. In particular the features extracted in this method
     *                  are selected according to the feature selection study exposed in the paper on which
     *                  this work is based on.
     *
     * @param filt: this object contains the 4 features (AC_C1,AC_DP1,AC_C2,AC_DP2) computed during the
     *            application of the autocorellation filter.
     */

    //Side-Note: in this approach we preferred to give precedence to modularity and readability of the code
    //instead of reducing complexity of the computations, calculating separately each feature. If performance
    //are not good we can try to reduce complexity of computations calculating in parallel more features
    //(looping just one time the relative vector).
    public static float[] featureExtraction(PreprocessingResult res, ACFilteringResult filt){

        float[] features = new float[Constants.FEATURE_VECT_DIM];
        int i=0;
        //Now the features are extracted and inserted in the array following the order specified in the reference paper.
        features[i++] = FeaturesCalculator.AverageAbsoluteVariation(res.acc_y);
        features[i++] = filt.getAC_C1();
        features[i++] = filt.getAC_DP2();
        float mean_x = FeaturesCalculator.Mean(res.acc_x);
        float std_x = FeaturesCalculator.stdDev(res.acc_x,mean_x);
        features[i++] = FeaturesCalculator.Kurtosis(res.acc_x,mean_x,std_x);
        features[i++] = FeaturesCalculator.Max(res.acc_x);
        features[i++] = FeaturesCalculator.Max(res.acc_z);
        float mean_m = FeaturesCalculator.Mean(res.modules);
        features[i++] = FeaturesCalculator.MeanCrossingRate(res.modules,mean_m);
        float mean_y = FeaturesCalculator.Mean(res.acc_y);
        features[i++] = FeaturesCalculator.MeanCrossingRate(res.acc_y,mean_y);
        float mean_z = FeaturesCalculator.Mean(res.acc_z);
        features[i++] = FeaturesCalculator.MeanCrossingRate(res.acc_z,mean_z);
        features[i++] = mean_x;
        features[i++] = mean_y;
        features[i++] = mean_z;
        features[i++] = FeaturesCalculator.Median(res.acc_x);
        features[i++] = FeaturesCalculator.Median(res.acc_z);
        features[i++] = FeaturesCalculator.Median(res.modules);
        features[i++] = FeaturesCalculator.Min(res.acc_x);
        features[i++] = FeaturesCalculator.RootMeanSquare(res.acc_z);
        float std_y = FeaturesCalculator.stdDev(res.acc_y,mean_y);
        features[i++] = FeaturesCalculator.Skewness(res.acc_y,mean_y,std_y);
        float std_m = FeaturesCalculator.stdDev(res.modules,mean_m);
        features[i++] = FeaturesCalculator.Skewness(res.modules,mean_m,std_m);

        //System.out.println("KURT_X= "+FeaturesCalculator.Kurtosis(res.acc_x,mean_x,std_x)+"SKEW_Y = "+FeaturesCalculator.Skewness(res.acc_y,mean_y,std_y)+"SKEW_M = "+FeaturesCalculator.Skewness(res.modules,mean_m,std_m));

        return features;
    }


    /**
     * This method represents the actual classifier which receives as input the gait instance that
     * must be classified. Then using NearestNeighbor parameters computed during the validation phase,
     * the method returns true if the gait instance is classified as correct (user authenticated),
     * false if the feature vector is not classified as correct.
     * recognized as corret
     * @param gait_instance :feature vector, extracted from walking segment, to be classified.
     * @return true is user authenticated false otherwise
     */
    public static boolean anomalyDetection(float[] gait_instance, File storagePath){

        float ad_th;
        float avg_td;
        float std_td;
        //Read the NN parameter from file and the training instances
        try {
            ArrayList<float[]> training_instances = readDataSet(storagePath,"training_set.csv");

            ArrayList<float[]> abnormal_instance = new ArrayList<>();
            abnormal_instance.add(gait_instance);

            //After the reading of the training set, we do the normalization of the abnormal instance
            //and of the training set itself.
            normalization_byCVFold(training_instances,new ArrayList<float[]>(),abnormal_instance);

            gait_instance = abnormal_instance.get(0);

            File f = new File(storagePath, "classifier_parameters.txt");

            if(!f.exists()){
                f.createNewFile();
            }

            BufferedReader br = new BufferedReader(new FileReader(f));
            String line;
            line = br.readLine();
            if(line == null){
                System.out.println("classifier_parameter.txt is empty.. Starting their computation!");

                ArrayList<Float> training_distances = get_NN_sumDistances(training_instances,null);

                avg_td = FeaturesCalculator.Mean(training_distances);
                std_td= FeaturesCalculator.stdDev(training_distances,avg_td);
                ad_th = selectAnomalyDetectionThreshold(training_distances,"spec",1.0f,avg_td,std_td);

                FileWriter classifierParamWriter = new FileWriter(f,true);
                String params = ad_th+","+avg_td+","+std_td+",";
                classifierParamWriter.write(params);
                classifierParamWriter.close();

            }else {
                String[] tmp = line.split(",");
                ad_th = Float.parseFloat(tmp[0]);
                avg_td = Float.parseFloat(tmp[1]);
                std_td = Float.parseFloat(tmp[2]);
            }

            br.close();


            float dist_min_g = NN_distances(gait_instance,training_instances,null);
            float AS_g = (dist_min_g - avg_td)/std_td;

            /*
            ad_th = (ad_th - avg_td)/std_td;
            */

            if(AS_g > ad_th){
                return false;
            }
            return true;

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.out.println("classifier_parameter.txt not found");
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("cannot read NN parameters");
        }
        return true;
    }

    /**
     * An utility method used to read from the device storage the fileName file which could contain
     * training or testing instances. The admitted filenames are:
     * - training_set.csv;
     * - normal_test_set.csv;
     * - abnormal_test_set.csv;
     * @param fileName
     * @return
     */
    public static ArrayList<float[]> readDataSet(File StoragePath,String fileName){

        ArrayList<float[]> data_set = new ArrayList<>();
        try {
            File f = new File(StoragePath,fileName);
            BufferedReader br = new BufferedReader(new FileReader(f));

            String line;
            String[] tmp;
            float[] instance;

            while ((line = br.readLine()) != null) {
                tmp = line.split(",");
                instance = new float[tmp.length];
                for(int i=0; i<tmp.length;i++){
                    instance[i] = Float.parseFloat(tmp[i]);
                }
                data_set.add(instance);
            }
            br.close();
            return data_set;

        }catch(FileNotFoundException fne){
            fne.printStackTrace();
            System.out.println("Cannot open "+fileName);
        }catch(IOException io){
            io.printStackTrace();
            System.out.println("Error during reading the csv file");
        }
        return data_set;
    }

    /**
     * This method takes as input the training instance set and another instance set. In particular the method could
     * work in 2 possible ways depending on the value of training_set parameter:
     * - If is null : the method computes for each instance of the $instances parameter the nearest neighbor distance
     *                w.r.t the remaining feature vectors of the list;
     * - if is not null: the method computes for each feature vector of the $instances parameter the nearest neighbor
     *                  distance w.r.t. the list of instances inside the training_set parameter.
     * @param instances
     * @param training_set
     * @return ArrayList of double values representing nearest neighbor distances for ech feature vector inside instances
     * parameter.
     */
    public static ArrayList<Float> get_NN_sumDistances(ArrayList<float[]> instances, ArrayList<float[]> training_set){

        ArrayList<Float> return_distances = new ArrayList<>();
        if(training_set == null){

            for(int i=0;i<instances.size();i++){
                float[] feat_vec = instances.get(i);
                ArrayList<Integer> skipList = new ArrayList<>();
                skipList.add(i);
                float kNN_distances = NN_distances(feat_vec,instances,skipList);
                return_distances.add(kNN_distances);
            }

        }else{
            for(int i=0;i<instances.size();i++) {
                float[] feat_vec = instances.get(i);
                float kNN_distances = NN_distances(feat_vec,training_set,null);
                return_distances.add(kNN_distances);
            }
        }

        return return_distances;

    }

    /**
     * This is an utility method executed by the Nearest Neighbor classifier to calculate the distance between
     * a feature vector (passed as argument) and the nearest neighbor inside a set of feature vectors.
     * @param feat_vec : feature vector for which we want to compute the distance to the nearest neighbor.
     * @param set : set of feature vectors in which we search the nearest neighbor of the target feature vector
     *            passed as argument.
     * @param skipList : index list of set's feature vectors that must be ignored during the search of nearest neighbor
     * @return : the distance between the input feature vector and the nearest neighbor inside the set parameter.
     */
    public static float NN_distances(float[] feat_vec, ArrayList<float[]> set, ArrayList<Integer> skipList) {

        float NN_distance = 99999999;
        for(int i=0;i<set.size();i++){
            //For each feature vector inside the set, if it is not in the skip list, is computed the
            //distance to target feature vector. At the end of the loop the NN_distance will contain
            //the distance to the nearest neighbor.
            if(skipList !=null && skipList.contains(i)){
                continue;
            }
            float[] set_instance = set.get(i);
            float sum = 0.0f;
            for(int j=0;j<set_instance.length;j++){
                sum += (feat_vec[j]-set_instance[j])*(feat_vec[j]-set_instance[j]);
            }
            sum = (float) Math.sqrt(sum);
            if(sum <= NN_distance){
                NN_distance = sum;
            }

        }
        return NN_distance;

    }


    private static float selectAnomalyDetectionThreshold(ArrayList<Float> distances, String mode, float AD_param,float training_dist_avg,float training_dist_std){

        if(mode.equals("spec")){
            ArrayList<Float> sorted_distances = new ArrayList<>(distances);
            Collections.sort(sorted_distances);
            float coverage = AD_param;
            if(coverage > 1){
                return sorted_distances.get(sorted_distances.size()-1)*coverage;
            }
            int indexCover = (int) (Math.ceil(coverage*sorted_distances.size())-1);
            if(indexCover < 0){
                indexCover = 0;
            }else if(indexCover > sorted_distances.size()-1){
                indexCover = sorted_distances.size()-1;
            }

            return sorted_distances.get(indexCover);

        }else if(mode.equals("sdev")){

            return (training_dist_avg + AD_param*training_dist_std);

        }

        return -1;

    }




}

