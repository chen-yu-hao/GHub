package dev.remotegpt.app.data

import dev.remotegpt.app.tr
import dev.remotegpt.app.R

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.remotegpt.client.Credential
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

class SecretStore(context: Context) {
    private val prefs = context.getSharedPreferences("encrypted_secrets", Context.MODE_PRIVATE)
    private val alias = "remote_gpt_credential_wrapping_v1"
    @Synchronized private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    fun save(id: String, secret: Credential) {
        val json = JSONObject().put("key", secret.privateKey).put("password", secret.password).put("passphrase", secret.passphrase).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(id.toByteArray()) }
        val bytes = cipher.iv + cipher.doFinal(json.toByteArray())
        check(prefs.edit().putString(id, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()) { tr(R.string.ui_could_not_save_credentials) }
    }
    fun read(id: String): Credential {
        val encoded = prefs.getString(id, null) ?: return Credential()
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size >= 28) { tr(R.string.ui_cannot_read_credentials_import_them_again) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))); updateAAD(id.toByteArray())
        }
        val json = JSONObject(cipher.doFinal(bytes.copyOfRange(12, bytes.size)).decodeToString())
        return Credential(json.optString("key"), json.optString("password"), json.optString("passphrase"))
    }
    fun remove(id: String) { check(prefs.edit().remove(id).commit()) { tr(R.string.ui_could_not_remove_credentials) } }
}
