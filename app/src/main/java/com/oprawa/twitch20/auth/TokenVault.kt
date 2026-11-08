package com.oprawa.twitch20.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal data class StoredSession(
    val clientId: String,
    val accessToken: String,
    val refreshToken: String,
    val login: String = "",
    val userId: String = "",
) {
    override fun toString(): String = "StoredSession(redacted)"
}

/** AES-GCM ciphertext only, excluded from Android backup. Never stores a client secret. */
internal class TokenVault(context: Context) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "twitch-session.enc"))
    private val alias = context.packageName + ".twitch-session.v1"

    @Synchronized fun read(): StoredSession? {
        return try {
            val encrypted = file.openRead().use { input ->
                require(input.channel.size() <= 32_768)
                DataInputStream(input).use { data ->
                    require(data.readInt() == 1)
                    val ivSize = data.readInt()
                    require(ivSize == 12)
                    val iv = ByteArray(ivSize).also(data::readFully)
                    val remaining = ByteArrayOutputStream()
                    data.copyTo(remaining)
                    iv to remaining.toByteArray()
                }
            }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.first))
            val json = JSONObject(String(cipher.doFinal(encrypted.second), Charsets.UTF_8))
            StoredSession(json.getString("clientId"), json.getString("accessToken"), json.getString("refreshToken"), json.optString("login", ""), json.optString("userId", ""))
                .takeIf { it.accessToken.isNotBlank() && it.refreshToken.isNotBlank() }
        } catch (_: FileNotFoundException) {
            null
        } catch (_: Exception) {
            // Corrupt data or invalidated keystore key requires explicit login again.
            clear()
            null
        }
    }

    @Synchronized fun write(session: StoredSession) {
        val bytes = JSONObject().put("clientId", session.clientId).put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken).put("login", session.login).put("userId", session.userId)
            .toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val ciphertext = cipher.doFinal(bytes)
        val stream = file.startWrite()
        try {
            val data = DataOutputStream(stream)
            data.writeInt(1)
            data.writeInt(cipher.iv.size)
            data.write(cipher.iv)
            data.write(ciphertext)
            data.flush()
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw IllegalStateException("Не удалось сохранить защищённую сессию")
        } finally {
            bytes.fill(0)
        }
    }

    @Synchronized fun clear() {
        file.delete()
        runCatching { KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias) }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
}
