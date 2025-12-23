package com.vitals.sdk.solutions.live.imp

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.Deflater
import java.util.zip.Inflater

object Base64Deflater {

    /**
     * 使用Deflater压缩字符串，并以Base64编码
     * @param data 要压缩的字符串
     * @return Base64编码的压缩字符串
     */
    fun compressToBase64(data: String): String {
        return try {
            // 1. 将字符串转换为UTF-8字节数组
            val inputBytes = data.toByteArray(StandardCharsets.UTF_8)

            // 2. 使用Deflater进行压缩
            val compressedBytes = deflateCompress(inputBytes)

            // 3. 将压缩后的字节数组转换为Base64字符串
            Base64.encodeToString(compressedBytes, Base64.NO_WRAP)

        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("压缩失败: ${e.message}")
        }
    }

    /**
     * 解压Base64编码的压缩数据
     * @param base64Data Base64编码的压缩字符串
     * @return 解压后的原始字符串
     */
    fun decompressFromBase64(base64Data: String): String {
        return try {
            // 1. Base64解码
            val compressedBytes = Base64.decode(base64Data, Base64.NO_WRAP)

            // 2. 使用Inflater解压
            val decompressedBytes = inflateDecompress(compressedBytes)

            // 3. 将字节数组转换回字符串
            String(decompressedBytes, StandardCharsets.UTF_8)

        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("解压失败: ${e.message}")
        }
    }

    /**
     * 核心压缩方法：使用最大压缩率
     */
    private fun deflateCompress(input: ByteArray): ByteArray {
        // 创建Deflater，使用最大压缩率，nowrap=true表示无zlib头部
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)

        return ByteArrayOutputStream().use { baos ->
            // 设置输入数据
            deflater.setInput(input)
            deflater.finish()

            val buffer = ByteArray(1024)

            // 压缩数据
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                baos.write(buffer, 0, count)
            }

            deflater.end()
            baos.toByteArray()
        }
    }

    /**
     * 核心解压方法
     */
    private fun inflateDecompress(compressed: ByteArray): ByteArray {

        val inflater = Inflater(true) // nowrap=true，与压缩时对应

        return ByteArrayOutputStream().use { baos ->
            inflater.setInput(compressed)

            val buffer = ByteArray(1024)

            // 解压数据
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                baos.write(buffer, 0, count)
            }

            inflater.end()
            baos.toByteArray()
        }
    }

    /**
     * 性能测试和验证方法
     */
    fun testCompression(original: String) {
        println("=== 压缩测试 ===")
        println("原始字符串长度: ${original.length} 字符")
        println("原始字节大小: ${original.toByteArray(StandardCharsets.UTF_8).size} 字节")

        val startTime = System.currentTimeMillis()
        val compressedBase64 = compressToBase64(original)
        val compressionTime = System.currentTimeMillis() - startTime

        println("压缩后Base64长度: ${compressedBase64.length} 字符")
        println("压缩耗时: ${compressionTime}ms")

        val decompressStart = System.currentTimeMillis()
        val decompressed = decompressFromBase64(compressedBase64)
        val decompressionTime = System.currentTimeMillis() - decompressStart

        println("解压耗时: ${decompressionTime}ms")
        println("解压验证: ${original == decompressed}")
        println("压缩比: ${"%.2f".format(original.toByteArray().size.toDouble() / compressedBase64.length)}")
    }
}