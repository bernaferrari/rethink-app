/*
 * Copyright (C) 2021 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bernaferrari.bravedns.iab

import android.text.TextUtils
import android.util.Base64
import android.util.Log
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.InvalidKeyException
import java.security.KeyFactory
import java.security.NoSuchAlgorithmException
import java.security.PublicKey
import java.security.Signature
import java.security.SignatureException
import java.security.spec.InvalidKeySpecException
import java.security.spec.X509EncodedKeySpec

/** Purchase-signature verification for the website build. */
internal object Security {
    private const val TAG = "Security"
    private const val KEY_FACTORY_ALGORITHM = "RSA"
    private const val SIGNATURE_ALGORITHM = "SHA1withRSA"
    private const val BASE_64_ENCODED_PUBLIC_KEY = ""

    @JvmStatic
    fun verifyPurchase(signedData: String?, signature: String?): Boolean {
        if (TextUtils.isEmpty(signedData) || TextUtils.isEmpty(BASE_64_ENCODED_PUBLIC_KEY) || TextUtils.isEmpty(signature)) {
            Log.w(TAG, "Purchase verification failed: missing data.")
            return false
        }
        return try {
            verify(generatePublicKey(), signedData.orEmpty(), signature.orEmpty())
        } catch (error: IOException) {
            Log.e(TAG, "Error generating PublicKey from encoded key: ${error.message}")
            false
        }
    }

    private fun generatePublicKey(): PublicKey = try {
        val decodedKey = Base64.decode(BASE_64_ENCODED_PUBLIC_KEY, Base64.DEFAULT)
        KeyFactory.getInstance(KEY_FACTORY_ALGORITHM).generatePublic(X509EncodedKeySpec(decodedKey))
    } catch (error: NoSuchAlgorithmException) {
        throw RuntimeException(error)
    } catch (error: InvalidKeySpecException) {
        val message = "Invalid key specification: $error"
        Log.w(TAG, message)
        throw IOException(message)
    }

    private fun verify(publicKey: PublicKey, signedData: String, signature: String): Boolean {
        val signatureBytes = try {
            Base64.decode(signature, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            Log.w(TAG, "Base64 decoding failed.")
            return false
        }
        return try {
            Signature.getInstance(SIGNATURE_ALGORITHM).run {
                initVerify(publicKey)
                update(signedData.toByteArray(StandardCharsets.UTF_8))
                verify(signatureBytes).also { verified ->
                    if (!verified) Log.w(TAG, "Signature verification failed...")
                }
            }
        } catch (error: NoSuchAlgorithmException) {
            throw RuntimeException(error)
        } catch (_: InvalidKeyException) {
            Log.e(TAG, "Invalid key specification.")
            false
        } catch (_: SignatureException) {
            Log.e(TAG, "Signature exception.")
            false
        }
    }
}
