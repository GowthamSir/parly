package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

object ImageEncoder {
    private const val MAX_DIMENSION = 720
    private const val MAX_BASE64_LENGTH = 145_000

    /**
     * Reads a photo chosen via Android Photo Picker (PickVisualMedia), downscales it,
     * and compresses it into a compact JPEG data URI that fits comfortably inside Firestore bounds.
     */
    suspend fun encodeUriToDataUri(context: Context, uri: Uri): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val resolver = context.applicationContext.contentResolver
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                } ?: return@withContext Result.failure(IllegalArgumentException("Unable to read selected image."))

                val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
                var sampleSize = 1
                while (maxSide / sampleSize > MAX_DIMENSION * 2) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val rawBitmap = resolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                } ?: return@withContext Result.failure(IllegalArgumentException("Unable to decode image."))

                val scaled = scaleDownIfNeeded(rawBitmap, MAX_DIMENSION)
                if (scaled != rawBitmap) {
                    rawBitmap.recycle()
                }

                var quality = 78
                var encodedBytes: ByteArray
                do {
                    val out = ByteArrayOutputStream()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                    encodedBytes = out.toByteArray()
                    quality -= 10
                } while (encodedBytes.size * 4 / 3 > MAX_BASE64_LENGTH && quality >= 30)

                scaled.recycle()

                val base64 = Base64.encodeToString(encodedBytes, Base64.NO_WRAP)
                val dataUri = "data:image/jpeg;base64,$base64"
                if (dataUri.length > 155_000) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Selected image is too large even after compression.")
                    )
                }
                Result.success(dataUri)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun decodeDataUriToBitmap(dataUri: String): Bitmap? {
        if (!dataUri.startsWith("data:image")) return null
        val commaIdx = dataUri.indexOf(',')
        if (commaIdx < 0) return null
        return try {
            val base64Part = dataUri.substring(commaIdx + 1)
            val bytes = Base64.decode(base64Part, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap, maxDim: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDim && h <= maxDim) return bitmap
        val ratio = min(maxDim.toFloat() / w, maxDim.toFloat() / h)
        val targetW = max(1, (w * ratio).toInt())
        val targetH = max(1, (h * ratio).toInt())
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}
