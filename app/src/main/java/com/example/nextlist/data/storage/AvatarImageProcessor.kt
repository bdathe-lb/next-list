package com.example.nextlist.data.storage

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProcessedAvatar(
    val bytes: ByteArray,
    val contentType: String = "image/webp",
)

class InvalidAvatarException(val reason: String) : IllegalArgumentException(reason)

@Singleton
class AvatarImageProcessor @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun process(sourceUri: String): ProcessedAvatar = withContext(Dispatchers.IO) {
        val uri = sourceUri.toUri()
        val resolver = context.contentResolver
        validateSource(resolver, uri)

        var bitmap = decodeSampled(resolver, uri)
        bitmap = rotateForExif(resolver, uri, bitmap)
        bitmap = scaleToLongEdge(bitmap, MAX_LONG_EDGE)

        val qualities = intArrayOf(84, 72, 60)
        var output: ByteArray? = null
        for (quality in qualities) {
            val candidate = compressWebp(bitmap, quality)
            if (candidate.size <= TARGET_MAX_BYTES) {
                output = candidate
                break
            }
        }

        while (output == null && max(bitmap.width, bitmap.height) > MIN_LONG_EDGE) {
            val scaled = scaleToLongEdge(bitmap, (max(bitmap.width, bitmap.height) * 0.8f).toInt())
            bitmap = scaled
            val candidate = compressWebp(bitmap, 60)
            if (candidate.size <= TARGET_MAX_BYTES) output = candidate
        }

        bitmap.recycle()
        ProcessedAvatar(output ?: throw InvalidAvatarException("compressed_size"))
    }

    private fun validateSource(resolver: ContentResolver, uri: Uri) {
        val mimeType = resolver.getType(uri)
        if (mimeType != null && !mimeType.startsWith("image/")) {
            throw InvalidAvatarException("mime_type")
        }
        val sourceSize = resolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
        }
        if (sourceSize != null && sourceSize > MAX_SOURCE_BYTES) {
            throw InvalidAvatarException("source_size")
        }
    }

    private fun decodeSampled(resolver: ContentResolver, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsStream = resolver.openInputStream(uri)
            ?: throw InvalidAvatarException("source_unavailable")
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw InvalidAvatarException("invalid_dimensions")
        }

        var sampleSize = 1
        while (
            max(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) >
            DECODE_LONG_EDGE
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw InvalidAvatarException("decode_failed")
    }

    private fun rotateForExif(
        resolver: ContentResolver,
        uri: Uri,
        source: Bitmap,
    ): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
        }
        if (matrix.isIdentity) return source
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
            if (it !== source) source.recycle()
        }
    }

    private fun scaleToLongEdge(source: Bitmap, targetLongEdge: Int): Bitmap {
        val longEdge = max(source.width, source.height)
        if (longEdge <= targetLongEdge) return source
        val ratio = targetLongEdge.toFloat() / longEdge
        return source.scale(
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            filter = true,
        ).also {
            if (it !== source) source.recycle()
        }
    }

    @Suppress("DEPRECATION")
    private fun compressWebp(bitmap: Bitmap, quality: Int): ByteArray =
        ByteArrayOutputStream().use { output ->
            if (!bitmap.compress(Bitmap.CompressFormat.WEBP, quality, output)) {
                throw InvalidAvatarException("compression_failed")
            }
            output.toByteArray()
        }

    private companion object {
        const val DECODE_LONG_EDGE = 4096
        const val MAX_LONG_EDGE = 1536
        const val MIN_LONG_EDGE = 640
        const val TARGET_MAX_BYTES = 2 * 1024 * 1024
        const val MAX_SOURCE_BYTES = 25L * 1024 * 1024
    }
}
