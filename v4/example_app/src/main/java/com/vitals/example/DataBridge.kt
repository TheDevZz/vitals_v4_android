package com.vitals.example

import android.os.Parcel
import android.os.Parcelable
import com.vitals.sdk.api.VitalsSampledData
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

object DataBridge {

    var sampledData: VitalsSampledData? = null

    @Throws(IOException::class)
    fun writeParcelableToFile(`object`: Parcelable?, file: File) {
        val parcel = Parcel.obtain()
        try {
            // 将对象写入 Parcel
            parcel.writeParcelable(`object`, 0)

            // 获取 Parcel 的字节数据
            val bytes = parcel.marshall()

            // 写入文件
            val fos = FileOutputStream(file)
            fos.write(bytes)
            fos.close()
        } finally {
            parcel.recycle()
        }
    }

    @Throws(IOException::class)
    fun <T : Parcelable?> readParcelableFromFile(file: File, classLoader: ClassLoader?): T? {
        // 读取文件字节
        val fis = FileInputStream(file)
        val bytes = ByteArray(file.length().toInt())
        fis.read(bytes)
        fis.close()


        // 反序列化
        val parcel = Parcel.obtain()
        try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0) // 重要：重置读取位置
            return parcel.readParcelable(classLoader)
        } finally {
            parcel.recycle()
        }
    }
}