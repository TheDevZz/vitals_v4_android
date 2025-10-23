package com.vitals.sdk.framework

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.vitals.sdk.api.MeasureResult
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.Array
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random

class KeyStoreHelper {

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
        }
    }

    fun createAndStoreKey(alias: String): SecretKey {
        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setUserAuthenticationRequired(false) // Set to true if needed
            .build()

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        )
        keyGenerator.init(keyGenParameterSpec)
        return keyGenerator.generateKey()
    }

    fun getSecretKey(alias: String): SecretKey {
        return keyStore.getKey(alias, null) as? SecretKey ?: createAndStoreKey(alias)
    }
}

class SecureFileHandler {

    private val keyStoreHelper = KeyStoreHelper()
    private val KEYSTORE_ALIAS = "secure_file_key"
    private val TRANSFORMATION = "AES/CBC/PKCS7Padding"

    companion object {
        private const val TAG = "SecureFileHandler"
    }

    fun write(text: String, filePath: String): Boolean = write(text, File(filePath))

    fun write(text: String, file: File): Boolean = encryptTextToFile(text, file)

    fun read(filePath: String) = read(File(filePath))

    fun read(file: File) = decryptTextFromFile(file)

    fun encryptTextToFile(text: String, file: File): Boolean {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, keyStoreHelper.getSecretKey(KEYSTORE_ALIAS))

            file.parentFile?.mkdirs()

            FileOutputStream(file).use { fos ->
                fos.write(cipher.iv)

                CipherOutputStream(fos, cipher).use { cos ->
                    cos.write(text.toByteArray(Charsets.UTF_8))
                }
            }
            return true
        } catch (e: Exception) {
            Logger.e(TAG, "Encryption error: ${e.message}")
            return false
        }
    }

    fun decryptTextFromFile(file: File): String? {
        try {
            if (!file.exists()) {
                return null
            }

            FileInputStream(file).use { fis ->
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val iv = ByteArray(cipher.blockSize)
                fis.read(iv)

                cipher.init(Cipher.DECRYPT_MODE, keyStoreHelper.getSecretKey(KEYSTORE_ALIAS), IvParameterSpec(iv))

                CipherInputStream(fis, cipher).use { cis ->
                    return cis.reader().readText()
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Decryption error: ${e.message}")
            return null
        }
    }
}


class FileEncryptor {
    private val TAG = "FileEncryptor"
    private val version: Int = 1
    private val AES_ALGORITHM = "AES/CBC/PKCS7Padding"
    private val RSA_ALGORITHM = "RSA/ECB/PKCS1Padding"
    private val RSA_PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCrTxvxccRAZAPxGBAuh09FSe0k/TS/M6Lf2TQVN5YIBNfAfmfR71DBG0c5tyAdy/SehwN8dcnw/eKy8meh1CkHV0pNtnTzfdKGH/1+Twsg3ma7boH2mHajhnUTLbnIh2gy93l/N3eLTTPCE6lW8+2XrrIfrHge49FzteUUOSbeLwIDAQAB"

    // 用于对称加密的密钥生成
    private fun generateSymmetricKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(256) // 选择256位的AES加密
        return keyGen.generateKey()
    }

    // 用于非对称加密的密钥生成
    fun rsaPublicKey(): PublicKey {
        val keyBuffer = Base64.decode(RSA_PUBLIC_KEY, Base64.DEFAULT)
        val keySpec = X509EncodedKeySpec(keyBuffer)
        val keyFactory = KeyFactory.getInstance("RSA")
        val publicKey = keyFactory.generatePublic(keySpec)
        return publicKey
    }

    // 使用对称密钥加密文本
    private fun encryptTextWithSymmetricKey(text: String, secretKey: SecretKey): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(AES_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedText = cipher.doFinal(text.toByteArray())
        return Pair(encryptedText, cipher.iv)
    }

    // 使用非对称公钥加密对称密钥
    private fun encryptSymmetricKeyWithPublicKey(secretKey: SecretKey, publicKey: PublicKey): ByteArray {
        val cipher = Cipher.getInstance(RSA_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(secretKey.encoded)
    }

    // 将密钥和密文写入文件
    fun encryptAndWriteToFile(text: String, file: File, publicKey: PublicKey) {
        val symmetricKey = generateSymmetricKey()
        val (encryptedText, iv) = encryptTextWithSymmetricKey(text, symmetricKey)
        val encryptedKey = encryptSymmetricKeyWithPublicKey(symmetricKey, publicKey)
        file.parentFile?.mkdirs()
        file.outputStream().use { fos ->
            fos.write(version)
            fos.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(encryptedKey.size).array())
            fos.write(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(iv.size).array())
            fos.write(encryptedKey)
            fos.write(iv)
            fos.write(encryptedText)
        }
    }

    fun write(text: String, file: File): Boolean {
        try {
            encryptAndWriteToFile(text, file, rsaPublicKey())
            return true
        } catch (e: Throwable) {
            Logger.e(TAG, "Encryption error: ${e.message}")
            return false
        }
    }

    fun write(text: String, filePath: String): Boolean = write(text, File(filePath))
}

object ExperienceHelper {
    private fun createParams(): Pair<Int, Int> {
        val a = Random.nextInt(13, 89) // 13~89
        val b = Random.nextInt(1279, 3457) // 1279~3457
        return Pair(a, b)
    }

    private fun string(res: MeasureResult): String {
        val values = ArrayList<Float>()
        values.add(res.heartRate)
        values.add(res.heartRateVariability)
        values.add(res.respirationRate)
        values.add(res.oxygenSaturation)
//        values.add(res.systolicBloodPressure)
//        values.add(res.diastolicBloodPressure)
        values.add(res.stress)
        values.add(res.confidence)
        val (a, b) = createParams()
        val vMaps = values.map {
            var v = (it * 1000).roundToInt()
            v = v * a + b
            v
        }
        val strList = ArrayList<String>()
        strList.add(a.toString())
        strList.add(b.toString())
        strList.add(res.createTime.toString())
        vMaps.forEach {
            strList.add(it.toString())
        }
        return strList.joinToString(",")
    }

    private fun parse(text: String): MeasureResult? {
        return try {
            val strList = text.split(",")
            val a = strList[0].toInt()
            val b = strList[1].toInt()
            val createTime = strList[2].toLong()
            val values = ArrayList<Float>()
            for (i in 3 until strList.size) {
                var v = strList[i].toInt()
                v = (v - b) / a
                val vf = v / 1000f
                values.add(vf)
            }
            val res = MeasureResult(
                heartRate = values[0],
                heartRateVariability = values[1],
                respirationRate = values[2],
                oxygenSaturation = values[3],
//                systolicBloodPressure = values[4],
//                diastolicBloodPressure = values[5],
                stress = values[6],
                confidence = values[7],
                createTime = createTime,
            )
            res
        } catch (t: Throwable) {
            null
        }
    }

    fun read(file: File): List<MeasureResult> {
        val text = SecureFileHandler().read(file)
        if (text.isNullOrEmpty()) {
            return emptyList()
        }
        return text.lines().mapNotNull {
            parse(it)
        }
    }

    fun write(file: File, res: MeasureResult) {
        var text = ""
        if (file.exists()) {
            text = SecureFileHandler().read(file) ?: ""
        }
        val list = ArrayList(text.lines())
        list.add(string(res))

        val si = list.size - 15
        val filterList = if (si > 0) {
            list.subList(si, list.size)
        } else {
            list
        }
        text = filterList.joinToString("\n")
        SecureFileHandler().write(text, file)
    }

    fun getExperience(file: File): MeasureResult? {
        val list = read(file)
        if (list.isEmpty()) {
            return null
        }
        if (list.size == 1) {
            val res = list[0]
            res.heartRate += Random.nextDouble(-3.0, 3.0).toFloat()
            res.heartRateVariability += Random.nextDouble(-2.0, 2.0).toFloat()
            res.respirationRate += Random.nextDouble(-2.0, 2.0).toFloat()
            res.oxygenSaturation += Random.nextDouble(-1.0, 1.0).toFloat()
//            res.systolicBloodPressure += Random.nextDouble(-5.0, 5.0).toFloat()
//            res.diastolicBloodPressure += Random.nextDouble(-4.0, 4.0).toFloat()
            res.stress += Random.nextDouble(-0.5, 0.5).toFloat()

            res.heartRate = res.heartRate.coerceIn(1f, 150f)
            res.heartRateVariability = res.heartRateVariability.coerceIn(1f, 125f)
            res.respirationRate = res.respirationRate.coerceIn(1f, 32f)
            res.oxygenSaturation = res.oxygenSaturation.coerceIn(94f, 100f)
//            res.systolicBloodPressure = res.systolicBloodPressure.coerceIn(1f, 200f)
//            res.diastolicBloodPressure = res.diastolicBloodPressure.coerceIn(1f, 120f)
            res.stress = res.stress.coerceIn(0.1f, 5f)
            return res
        }
        val res = MeasureResult(
            heartRate = getExpValue(list.map { it.heartRate }),
            heartRateVariability = getExpValue(list.map { it.heartRateVariability }),
            respirationRate = getExpValue(list.map { it.respirationRate }),
            oxygenSaturation = getExpValue(list.map { it.oxygenSaturation }),
//            systolicBloodPressure = getExpValue(list.map { it.systolicBloodPressure }),
//            diastolicBloodPressure = getExpValue(list.map { it.diastolicBloodPressure }),
            stress = getExpValue(list.map { it.stress }),
            confidence = Random.nextDouble(0.08, 0.121).toFloat(), // 0.08~0.12
        )
        return res
    }

    private fun getExpValue(list: List<Float>): Float {
        if (list.isEmpty()) {
            return 0f
        }
        var min = 0f
        var max = 0f
        var sum = list.sum()
        var count = list.size
        if (list.size > 10) {
            max = list.max()
            min = list.min()
            sum = sum - max - min
            count -= 2
        }

        val mean = sum / count
        val offset = (Math.random() - 0.5) * min(max - mean, mean - min)
        return (mean + offset).toFloat()
    }
}