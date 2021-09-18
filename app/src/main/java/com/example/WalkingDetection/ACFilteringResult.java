package com.example.WalkingDetection;

public class ACFilteringResult {

    private boolean result;
    private float AC_C2;
    private float AC_DP2;
    private float AC_C1;
    private float AC_DP1;


    public ACFilteringResult(boolean result, float AC_C2, float AC_DP2, float AC_C1, float AC_DP1) {
        this.result = result;
        this.AC_C2 = AC_C2;
        this.AC_DP2 = AC_DP2;
        this.AC_C1 = AC_C1;
        this.AC_DP1 = AC_DP1;
    }

    public boolean isResult() {
        return result;
    }

    public float getAC_C2() {
        return AC_C2;
    }

    public float getAC_DP2() {
        return AC_DP2;
    }

    public float getAC_C1() {
        return AC_C1;
    }

    public float getAC_DP1() {
        return AC_DP1;
    }
}
