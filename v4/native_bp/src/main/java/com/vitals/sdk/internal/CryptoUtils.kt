package com.vitals.sdk.internal

import java.io.InputStream
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {
    fun hashSHA256(input: String): String {
        val bytes = input.toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * 将输入流包装为解密流（从流中读取IV）
     *
     * 此方法会从输入流的前16个字节读取IV，然后使用剩余数据进行解密
     *
     * @param inputStream 需要解密的输入流（前16字节为IV）
     * @param key 十六进制格式的密钥字符串
     * @return 解密后的输入流
     * @throws IllegalStateException 如果无法读取足够的IV字节
     */
    fun decryptStream(
        inputStream: InputStream,
        key: String
    ): InputStream {
        // 从流中读取前16字节作为IV
        val iv = ByteArray(16)
        var totalRead = 0

        while (totalRead < 16) {
            val read = inputStream.read(iv, totalRead, 16 - totalRead)
            if (read == -1) {
                throw IllegalStateException("Cannot read IV: stream ended after $totalRead bytes")
            }
            totalRead += read
        }

        // 将十六进制字符串转换为字节数组
        val keyBytes = key.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        // 创建AES密钥
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // 创建Cipher实例
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

        // 使用密钥和IV初始化Cipher（解密模式）
        val ivParameterSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)

        // 返回CipherInputStream，它会在读取时自动解密数据
        // 注意：此时inputStream已经读取了前16字节，剩余的是加密数据
        return CipherInputStream(inputStream, cipher)
    }

    /**
     * 将输入流包装为解密流（显式提供IV）
     *
     * @param inputStream 需要解密的输入流
     * @param key 十六进制格式的密钥字符串
     * @param iv 初始化向量（16字节）
     * @return 解密后的输入流
     */
    fun decryptStream(
        inputStream: InputStream,
        key: String,
        iv: ByteArray
    ): InputStream {
        require(iv.size == 16) { "IV must be 16 bytes" }

        // 将十六进制字符串转换为字节数组
        val keyBytes = key.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

        // 创建AES密钥
        val secretKey = SecretKeySpec(keyBytes, "AES")

        // 创建Cipher实例
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

        // 使用密钥和IV初始化Cipher（解密模式）
        val ivParameterSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivParameterSpec)

        // 返回CipherInputStream，它会在读取时自动解密数据
        return CipherInputStream(inputStream, cipher)
    }
}