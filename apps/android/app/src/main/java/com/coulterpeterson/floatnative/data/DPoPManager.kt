package com.coulterpeterson.floatnative.data

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

class DPoPManager(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "floatnative_dpop_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

        private const val KEY_PRIVATE_KEY = "dpop_private_key"
        private const val KEY_PUBLIC_KEY = "dpop_public_key"
        private const val KEY_TIME_OFFSET = "dpop_time_offset"
    }

    init {
        // Load offset on init
        timeOffsetSeconds = prefs.getLong(KEY_TIME_OFFSET, 0L)
    }

    // Time offset in seconds (Service Time - Local Time)
    var timeOffsetSeconds: Long = 0
        set(value) {
            field = value
            prefs.edit().putLong(KEY_TIME_OFFSET, value).apply()
        }

    private var keyPair: KeyPair? = null

    @Synchronized
    private fun getOrGenerateKeyPair(): KeyPair {
        if (keyPair != null) return keyPair!!

        // Try to load
        val privString = prefs.getString(KEY_PRIVATE_KEY, null)
        val pubString = prefs.getString(KEY_PUBLIC_KEY, null)

        if (privString != null && pubString != null) {
            try {
                val privBytes = Base64.decode(privString, Base64.NO_WRAP)
                val pubBytes = Base64.decode(pubString, Base64.NO_WRAP)

                val kf = KeyFactory.getInstance("EC")
                val privSpec = PKCS8EncodedKeySpec(privBytes)
                val pubSpec = X509EncodedKeySpec(pubBytes)

                val privateKey = kf.generatePrivate(privSpec)
                val publicKey = kf.generatePublic(pubSpec)

                keyPair = KeyPair(publicKey, privateKey)
                return keyPair!!
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Generate new
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1")) // P-256
        val map = kpg.generateKeyPair()

        // Save
        val privEnc = Base64.encodeToString(map.private.encoded, Base64.NO_WRAP)
        val pubEnc = Base64.encodeToString(map.public.encoded, Base64.NO_WRAP)

        prefs.edit()
            .putString(KEY_PRIVATE_KEY, privEnc)
            .putString(KEY_PUBLIC_KEY, pubEnc)
            .apply()

        keyPair = map
        return map
    }

    fun generateProof(httpMethod: String, httpUrl: String, accessToken: String? = null): String {
        val keys = getOrGenerateKeyPair()
        val publicKey = keys.public as ECPublicKey

        // 1. JWK Header
        // We need to construct a JWK manually from the public key parameters (x, y)
        // This is a bit tedious in Java/Kotlin without a library like Nimbus, but feasible.
        // Or we can just include the "jwk" claim with basic kty/crv/x/y.

        // Get X and Y coordinates from EC Public Key
        val w = publicKey.w
        val xBytes = toByteArray(w.affineX, 32)
        val yBytes = toByteArray(w.affineY, 32)
        
        val jwk = JSONObject()
        jwk.put("kty", "EC")
        jwk.put("crv", "P-256")
        jwk.put("x", base64UrlEncode(xBytes))
        jwk.put("y", base64UrlEncode(yBytes))
        
        val header = JSONObject()
        header.put("typ", "dpop+jwt")
        header.put("alg", "ES256")
        header.put("jwk", jwk)

        // 2. Payload
        // Strip query params from URL
        var htu = httpUrl
        if (httpUrl.contains("?")) {
            htu = httpUrl.split("?")[0]
        }

        val payload = JSONObject()
        payload.put("iat", (System.currentTimeMillis() / 1000) + timeOffsetSeconds)
        payload.put("jti", UUID.randomUUID().toString())
        payload.put("htm", httpMethod)
        payload.put("htu", htu)

        if (accessToken != null) {
           // ath: base64url(sha256(access_token))
           val md = MessageDigest.getInstance("SHA-256")
           val hash = md.digest(accessToken.toByteArray(Charsets.UTF_8))
           payload.put("ath", base64UrlEncode(hash))
        }

        // 3. Sign
        val headerStr = base64UrlEncode(header.toString().toByteArray(Charsets.UTF_8))
        val payloadStr = base64UrlEncode(payload.toString().toByteArray(Charsets.UTF_8))
        val toSign = "$headerStr.$payloadStr"

        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(keys.private)
        signature.update(toSign.toByteArray(Charsets.UTF_8))
        val sigBytes = signature.sign()
        
        // Convert DER signature to JOSE (Client-side R+S concatenation) requires parsing DER.
        // Android's typical crypto providers output DER. 
        // We need R and S as raw 32-byte integers concatenated used for JWT ES256.
        val joseSig = derToJose(sigBytes)

        return "$toSign.${base64UrlEncode(joseSig)}"
    }

    private fun base64UrlEncode(bytes: ByteArray): String {
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private fun toByteArray(value: java.math.BigInteger, size: Int): ByteArray {
        val array = value.toByteArray()
        if (array.size == size) return array
        // BigInteger might add a sign byte if MSB is set, or remove leading zeros.
        // If > size, strip leading bytes (sign extension).
        // If < size, pad with zeros.
        if (array.size > size) {
            return array.copyOfRange(array.size - size, array.size)
        }
        val padded = ByteArray(size)
        System.arraycopy(array, 0, padded, size - array.size, array.size)
        return padded
    }
    
    // Convert DER encoded signature (ASN.1) to Raw R|S concatenation (64 bytes)
    private fun derToJose(der: ByteArray): ByteArray {
        // DER Structure: 0x30 <len> 0x02 <lenR> <R> 0x02 <lenS> <S>
        // Use a simple parser or generic ASN1 parser
        // Assuming strictly correct DER from system provider
        
        var pos = 0
        if (der[pos++] != 0x30.toByte()) throw IllegalArgumentException("Not a DER sequence")
        
        var len = der[pos++].toInt()
        if (len < 0) { // multi-byte length, skip (unlikely for ECDSA P-256 sigs which are small)
             // simplified for this context, usually < 127
        }

        // R
        if (der[pos++] != 0x02.toByte()) throw IllegalArgumentException("Not an integer")
        var rLen = der[pos++].toInt()
        var rStart = pos
        pos += rLen
        
        // S
        if (der[pos++] != 0x02.toByte()) throw IllegalArgumentException("Not an integer")
        var sLen = der[pos++].toInt()
        var sStart = pos
        
        // Extract R and S, remove leading zero if present for unsigned BigInteger interpretation
        val rBytes = Arrays_copyOfRange(der, rStart, rStart + rLen)
        val sBytes = Arrays_copyOfRange(der, sStart, sStart + sLen)
        
        // Normalize to 32 bytes
        val rNorm = normalizeInt(rBytes)
        val sNorm = normalizeInt(sBytes)
        
        val result = ByteArray(64)
        System.arraycopy(rNorm, 0, result, 0, 32)
        System.arraycopy(sNorm, 0, result, 32, 32)
        return result
    }
    
    private fun Arrays_copyOfRange(original: ByteArray, from: Int, to: Int): ByteArray {
        val newLength = to - from
        val copy = ByteArray(newLength)
        System.arraycopy(original, from, copy, 0, newLength)
        return copy
    }

    private fun normalizeInt(bytes: ByteArray): ByteArray {
        var start = 0
        // Skip leading zero if present (sign byte)
        if (bytes.size > 32 && bytes[0] == 0.toByte()) {
            start = 1
        }
        val len = bytes.size - start
        
        val result = ByteArray(32)
        val destPos = if (len < 32) 32 - len else 0
        System.arraycopy(bytes, start, result, destPos, if (len > 32) 32 else len)
        return result
    }
}
