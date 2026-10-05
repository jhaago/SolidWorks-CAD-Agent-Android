package com.jhaago.cadagent.remote.live

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

class KeystoreRemoteCredentialStore(context: Context) : RemoteCredentialStore {
    private val directory = context.applicationContext.noBackupFilesDir
    private val alias = "cad-agent-remote-pairing-v1"
    private fun file(endpoint: RemoteEndpoint): AtomicFile {
        val digest = MessageDigest.getInstance("SHA-256").digest(endpoint.origin.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, "remote-workstation-$digest.enc"))
    }
    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        if (!create) throw IllegalStateException()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).setRandomizedEncryptionRequired(true).build())
        }.generateKey()
    }
    @Synchronized override fun read(endpoint: RemoteEndpoint): PairedWorkstation? {
        val atomic = file(endpoint)
        val input = try { atomic.openRead() } catch (_: java.io.FileNotFoundException) { return null }
        try {
            val bytes = input.use { if (atomic.baseFile.length() > 65536) throw IllegalStateException(); it.readBytes() }
            require(bytes.size in 30..65536 && bytes[0] == 1.toByte())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
            val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(13, bytes.size)), Charsets.UTF_8))
            require(json.getString("origin") == endpoint.origin)
            return PairedWorkstation(endpoint, json.requiredString("deviceId", 128), json.requiredString("credential", 256))
        } catch (_: Exception) { throw RemoteFailure(0, "storage_failed", "Saved pairing is unavailable. Forget this workstation and pair again.") }
    }
    @Synchronized override fun write(workstation: PairedWorkstation) {
        val atomic = file(workstation.endpoint)
        try {
            val json = JSONObject(mapOf("origin" to workstation.endpoint.origin, "deviceId" to workstation.deviceId, "credential" to workstation.credential))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(true)) }
            val bytes = byteArrayOf(1) + cipher.iv + cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
            val output = atomic.startWrite()
            try { output.write(bytes); atomic.finishWrite(output) }
            catch (error: Exception) { atomic.failWrite(output); throw error }
        } catch (_: Exception) { throw RemoteFailure(0, "storage_failed", "Pairing could not be saved securely.") }
    }
    @Synchronized override fun delete(endpoint: RemoteEndpoint) {
        try {
            require(directory.isDirectory && directory.canRead() && directory.canWrite())
            val atomic = file(endpoint)
            atomic.delete()
            // AtomicFile ignores deletion failures. Check all recovery copies too.
            val records = listOf(atomic.baseFile, File(atomic.baseFile.path + ".bak"), File(atomic.baseFile.path + ".new"))
            check(records.none { it.exists() })
        } catch (_: Exception) {
            throw RemoteFailure(0, "storage_failed", "Saved pairing could not be removed securely.")
        }
    }
}
