package com.civinb.danmuji.data.bili.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.civinb.danmuji.util.DebugLog
import org.json.JSONObject
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 登录凭证（Cookie）的本地加密存储。
 * 密钥由 Android Keystore 生成并保管（AES-256-GCM），密钥本身不能被导出；
 * 加密后的数据存在应用私有的 SharedPreferences 里。应用已关闭系统备份（allowBackup=false）。
 * 不使用 EncryptedSharedPreferences：androidx.security:security-crypto 已被官方弃用。
 */
class CredentialStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(cookies: Map<String, String>) {
        val json = JSONObject()
        cookies.forEach { (k, v) -> json.put(k, v) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_IV, Base64.getEncoder().encodeToString(cipher.iv))
            .putString(KEY_DATA, Base64.getEncoder().encodeToString(encrypted))
            .apply()
    }

    fun load(): Map<String, String>? {
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val data = prefs.getString(KEY_DATA, null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.getDecoder().decode(iv)))
            val plain = String(cipher.doFinal(Base64.getDecoder().decode(data)), Charsets.UTF_8)
            val json = JSONObject(plain)
            val out = HashMap<String, String>()
            val it = json.keys()
            while (it.hasNext()) {
                val k = it.next()
                out[k] = json.optString(k)
            }
            out
        } catch (e: Exception) {
            // 密钥丢失（例如系统清除了 Keystore）时无法解密，只能要求重新登录
            DebugLog.log("Cred", "凭证解密失败，已清除：${e.javaClass.simpleName}")
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFS = "credentials"
        const val KEY_IV = "iv"
        const val KEY_DATA = "data"
        const val ALIAS = "danmuji_credentials"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
