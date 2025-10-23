package com.vitals.example;

import com.vitals.sdk.parcel.ParcelableVitalsSampledData;
import com.vitals.example.BloodPressureMeasureResult;

interface IBloodPressureAnalyzerService {
    BloodPressureMeasureResult analyzeBloodPressure(in ParcelableVitalsSampledData sampledData);
}