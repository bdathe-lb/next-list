package com.example.nextlist.data.storage

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarImageProcessorTest {
    @Test
    fun photoPickerStyleContentUriIsDecodedAndReencodedAsWebp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "nextlist-avatar-test.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/NextListTests")
            }
        }
        val uri = checkNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        )

        try {
            val bitmap = Bitmap.createBitmap(64, 96, Bitmap.Config.ARGB_8888).apply {
                eraseColor(0xFF426B5A.toInt())
            }
            resolver.openOutputStream(uri).use { output ->
                checkNotNull(output)
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            bitmap.recycle()

            val result = AvatarImageProcessor(context).process(uri.toString())

            assertEquals("image/webp", result.contentType)
            assertTrue(result.bytes.size <= 2 * 1024 * 1024)
            assertEquals(
                "RIFF",
                String(result.bytes, 0, 4, StandardCharsets.US_ASCII),
            )
            assertEquals(
                "WEBP",
                String(result.bytes, 8, 4, StandardCharsets.US_ASCII),
            )
        } finally {
            resolver.delete(uri, null, null)
        }
    }
}
