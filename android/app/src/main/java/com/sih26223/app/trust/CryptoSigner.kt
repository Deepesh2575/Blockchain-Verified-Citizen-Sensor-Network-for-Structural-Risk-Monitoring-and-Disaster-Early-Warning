package com.sih26223.app.trust

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature

class CryptoSigner {

    companion object {
        private const val TAG = "CryptoSigner"
        private const val KEY_ALIAS = "SIH2026_Event_Key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }

    init {
        generateKeyIfNeeded()
    }

    private fun generateKeyIfNeeded() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyPairGenerator = KeyPairGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE
                )
                
                val parameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build()
                    
                keyPairGenerator.initialize(parameterSpec)
                keyPairGenerator.generateKeyPair()
                Log.i(TAG, "New Hardware-backed ECDSA key pair generated.")
            } else {
                Log.i(TAG, "Hardware-backed key pair already exists.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Keystore key", e)
        }
    }

    fun signEventData(eventData: String): String? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val privateKey = keyStore.getKey(KEY_ALIAS, null) as java.security.PrivateKey
            
            val signature = Signature.getInstance("SHA256withECDSA")
            signature.initSign(privateKey)
            signature.update(eventData.toByteArray(Charsets.UTF_8))
            
            val sigBytes = signature.sign()
            Base64.encodeToString(sigBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to sign event data", e)
            null
        }
    }
}
