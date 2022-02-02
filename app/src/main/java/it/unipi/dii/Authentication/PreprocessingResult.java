package it.unipi.dii.Authentication;

import java.util.ArrayList;

public class PreprocessingResult {

   public ArrayList<Float> acc_x ;
   public ArrayList<Float> acc_y ;
   public ArrayList<Float> acc_z ;
   public ArrayList<Float> modules;

    public PreprocessingResult(){
        acc_x = new ArrayList<>();
        acc_y = new ArrayList<>();
        acc_z = new ArrayList<>();
        modules = new ArrayList<>();
    }




}
