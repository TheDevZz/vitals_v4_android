package com.vitals.sdk.parcel

import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Parcel
import android.os.Parcelable

class SignalData(
    val startTime: Long,
    val endTime: Long,
    val fps: Double,
    val pixels: DoubleArray,
    val shape: IntArray,
) : Parcelable {

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(startTime)
        dest.writeLong(endTime)
        dest.writeDouble(fps)
        dest.writeDoubleArray(pixels)
        dest.writeIntArray(shape)
    }

    companion object CREATOR : Parcelable.Creator<SignalData> {
        override fun createFromParcel(parcel: Parcel): SignalData {
            val startTime = parcel.readLong()
            val endTime = parcel.readLong()
            val fps = parcel.readDouble()
            val pixels = parcel.createDoubleArray() ?: doubleArrayOf()
            val shape = parcel.createIntArray() ?: intArrayOf()
            return SignalData(startTime, endTime, fps, pixels, shape)
        }

        override fun newArray(size: Int): Array<SignalData?> {
            return arrayOfNulls(size)
        }
    }
}

enum class Gender(val value: Int) {
    Female(0),
    Male(1);

    companion object {
        fun fromValue(value: Int): Gender {
            return values().find { it.value == value } ?: throw IllegalArgumentException("Invalid gender value: $value")
        }
    }
}

data class BaseFeature(
    var age: Int,
    var gender: Gender,
    var height: Double, // m
    var weight: Double, // kg
) : Parcelable {

    constructor(parcel: Parcel) : this(
        age = parcel.readInt(),
        gender = Gender.fromValue(parcel.readInt()),
        height = parcel.readDouble(),
        weight = parcel.readDouble()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(age)
        parcel.writeInt(gender.value) // 存储枚举的整数值
        parcel.writeDouble(height)
        parcel.writeDouble(weight)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<BaseFeature> {
        override fun createFromParcel(parcel: Parcel): BaseFeature {
            return BaseFeature(parcel)
        }

        override fun newArray(size: Int): Array<BaseFeature?> {
            return arrayOfNulls(size)
        }
    }
}

class Credential(
    val timestamp: Long,
    val sign: String,
): Parcelable {
    constructor(parcel: Parcel) : this(
        timestamp = parcel.readLong(),
        sign = parcel.readString() ?: ""
    )

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(timestamp)
        parcel.writeString(sign)
    }

    companion object CREATOR : Parcelable.Creator<Credential> {
        override fun createFromParcel(parcel: Parcel): Credential {
            return Credential(parcel)
        }

        override fun newArray(size: Int): Array<Credential?> {
            return arrayOfNulls(size)
        }
    }
}

class ParcelableVitalsSampledData(
    val credential: Credential,
    val signalData: SignalData,
    val pickedLandmarks: List<List<PointF>>,
    val pickedFrames: List<Bitmap>,
    val baseFeature: BaseFeature? = null,
): Parcelable {
    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeParcelable(credential, flags)
        dest.writeParcelable(signalData, flags)
        // 写入地标数据
        dest.writeInt(pickedLandmarks.size)
        pickedLandmarks.forEach { landmarkList ->
            dest.writeInt(landmarkList.size)
            landmarkList.forEach { point ->
                dest.writeFloat(point.x)
                dest.writeFloat(point.y)
            }
        }
        // 写入Bitmap列表
        dest.writeInt(pickedFrames.size)
        pickedFrames.forEach { bitmap ->
            dest.writeParcelable(bitmap, flags)
        }
        // 写入baseFeature
        if (baseFeature != null) {
            dest.writeInt(1)
            dest.writeParcelable(baseFeature, flags)
        } else {
            dest.writeInt(0)
        }
    }

    companion object CREATOR : Parcelable.Creator<ParcelableVitalsSampledData> {
        override fun createFromParcel(parcel: Parcel): ParcelableVitalsSampledData {
            val credential = parcel.readParcelable<Credential>(Credential::class.java.classLoader)!!
            val signalData = parcel.readParcelable<SignalData>(SignalData::class.java.classLoader)!!

            // 读取地标数据
            val landmarksSize = parcel.readInt()
            val pickedLandmarks = mutableListOf<List<PointF>>()
            repeat(landmarksSize) {
                val pointsSize = parcel.readInt()
                val points = mutableListOf<PointF>()
                repeat(pointsSize) {
                    val x = parcel.readFloat()
                    val y = parcel.readFloat()
                    points.add(PointF(x, y))
                }
                pickedLandmarks.add(points)
            }

            // 读取Bitmap列表
            val framesSize = parcel.readInt()
            val pickedFrames = mutableListOf<Bitmap>()
            repeat(framesSize) {
                val bitmap = parcel.readParcelable<Bitmap>(Bitmap::class.java.classLoader)!!
                pickedFrames.add(bitmap)
            }

            // 读取baseFeature
            val hasBaseFeature = parcel.readInt()
            val baseFeature = if (hasBaseFeature == 1) {
                parcel.readParcelable<BaseFeature>(BaseFeature::class.java.classLoader)
            } else {
                null
            }

            return ParcelableVitalsSampledData(credential, signalData, pickedLandmarks, pickedFrames, baseFeature)
        }

        override fun newArray(size: Int): Array<ParcelableVitalsSampledData?> {
            return arrayOfNulls(size)
        }
    }
}
