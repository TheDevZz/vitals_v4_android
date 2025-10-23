package com.vitals.example;

import android.os.Parcel;
import android.os.Parcelable;

class BloodPressureMeasureResult implements Parcelable {
    public float systolicBloodPressure;
    public float diastolicBloodPressure;

    public BloodPressureMeasureResult() {
        this.systolicBloodPressure = 0f;
        this.diastolicBloodPressure = 0f;
    }

    public BloodPressureMeasureResult(float systolicBloodPressure, float diastolicBloodPressure) {
        this.systolicBloodPressure = systolicBloodPressure;
        this.diastolicBloodPressure = diastolicBloodPressure;
    }

    protected BloodPressureMeasureResult(Parcel in) {
        systolicBloodPressure = in.readFloat();
        diastolicBloodPressure = in.readFloat();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(systolicBloodPressure);
        dest.writeFloat(diastolicBloodPressure);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BloodPressureMeasureResult> CREATOR = new Creator<BloodPressureMeasureResult>() {
        @Override
        public BloodPressureMeasureResult createFromParcel(Parcel in) {
            return new BloodPressureMeasureResult(in);
        }

        @Override
        public BloodPressureMeasureResult[] newArray(int size) {
            return new BloodPressureMeasureResult[size];
        }
    };
}
