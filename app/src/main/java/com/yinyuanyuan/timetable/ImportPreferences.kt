package com.yinyuanyuan.timetable

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URI
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ImportPreferences(context: Context) {
    private val prefs=context.getSharedPreferences("import_options",Context.MODE_PRIVATE)
    var compatibility: Boolean
        get()=prefs.getBoolean("compatibility",false)
        set(value){prefs.edit().putBoolean("compatibility",value).apply()}
    var portal: String
        get()=prefs.getString("portal","") ?: ""
        set(value){require(value.isBlank() || validPortal(value));prefs.edit().putString("portal",normalizePortal(value)).apply()}
    var useAgent: Boolean
        get()=prefs.getBoolean("agent",true)
        set(value){prefs.edit().putBoolean("agent",value).apply()}
    fun apiKey(): String? = runCatching {
        val encoded=prefs.getString("encrypted_key",null) ?: return null
        val parts=encoded.split('.')
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,secretKey(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
        String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),Charsets.UTF_8).takeIf {it.isNotBlank()}
    }.getOrNull()
    fun saveApiKey(value: String) {
        val key=value.trim()
        if(key.isEmpty()){prefs.edit().remove("encrypted_key").apply();return}
        require(key.length in 20..256 && key.none {it.isWhitespace()}) {"Key 格式不正确"}
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE,secretKey())
        val encrypted=Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+"."+Base64.encodeToString(cipher.doFinal(key.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
        prefs.edit().putString("encrypted_key",encrypted).apply()
    }
    private fun secretKey(): SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply {load(null)}
        (store.getKey("timetable_deepseek",null) as? SecretKey)?.let {return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("timetable_deepseek",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    companion object {
        fun normalizePortal(value: String): String {
            val text=value.trim()
            if(text.isBlank() || text.contains("://")) return text
            return "https://$text"
        }
        fun validPortal(value: String): Boolean = runCatching {
            val uri=URI(normalizePortal(value))
            uri.scheme.equals("https",true) && !uri.host.isNullOrBlank() && uri.userInfo==null && (uri.port==-1 || uri.port in 1..65535)
        }.getOrDefault(false)
    }
}
