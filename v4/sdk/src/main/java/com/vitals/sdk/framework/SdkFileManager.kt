package com.vitals.sdk.framework

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.pathString

class SdkFileManager() {
    /*
    cache
        vitals
            log
            models
            tms
            tmp
    file
        vitals
            data
                exp.dat
                fea.dat
     */
    companion object {
        const val VITALS_DIR_NAME = "vitals"
        const val LOG_DIR_NAME = "log"
        const val MODEL_DIR_NAME = "models"
        const val TOMBSTONE_DIR_NAME = "tms"
        const val TEMP_DIR_NAME = "temp"
        const val DATA_DIR_NAME = "data"
        const val EXPERIENCE_FILE_NAME = "exp.dat"
    }

    enum class FileType {
        TOMBSTONE
    }

    fun getDirPath(context: Context, dirName: String): String {
        return when(dirName) {
            DATA_DIR_NAME, ->
                Path(context.filesDir.path, VITALS_DIR_NAME, dirName).pathString
            LOG_DIR_NAME, ->
                Path((context.externalCacheDir?:context.cacheDir).path, VITALS_DIR_NAME, dirName).pathString
            MODEL_DIR_NAME, TOMBSTONE_DIR_NAME, TEMP_DIR_NAME, ->
                Path(context.cacheDir.path, VITALS_DIR_NAME, dirName).pathString

            else ->
                Path(context.cacheDir.path, VITALS_DIR_NAME, dirName).pathString
        }.apply {
            File(this).mkdirs()
        }
    }

    fun getTempPath(context: Context, fileName: String): String {
        return Path(getDirPath(context, TEMP_DIR_NAME), fileName).pathString
    }

    fun getFilePath(context: Context, fileName: String): String {
        return when(fileName) {
            EXPERIENCE_FILE_NAME ->
                Path(getDirPath(context, DATA_DIR_NAME), fileName).pathString

            else ->
                getTempPath(context, fileName)
        }
    }

    fun genFilePath(context: Context, fileType: FileType): String {
        return when(fileType) {
            FileType.TOMBSTONE -> {
                Path(
                    getDirPath(context, TOMBSTONE_DIR_NAME),
                    SimpleDateFormat("yyyyMMddHHmmss", Locale.US).format(System.currentTimeMillis())
                ).pathString
            }
        }
    }

}