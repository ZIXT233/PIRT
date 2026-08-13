package io.github.zixt233.pirt.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import io.github.zixt233.pirt.model.ChatImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

private const val MAX_MARKDOWN_IMAGE_BYTES = 20 * 1024 * 1024

@Composable
internal fun rememberPirtMarkdownImageTransformer(): ImageTransformer = remember {
    PirtMarkdownImageTransformer()
}

private class PirtMarkdownImageTransformer : ImageTransformer {
    @Composable
    override fun transform(link: String): ImageData? {
        val bytes by produceState<ByteArray?>(initialValue = null, link) {
            value = withContext(Dispatchers.IO) {
                runCatching { loadMarkdownImage(link) }.getOrNull()
            }
        }
        val imageBytes = bytes ?: return null
        val bitmap = remember(imageBytes) {
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)?.asImageBitmap()
        } ?: return null
        var viewerOpen by remember(link) { mutableStateOf(false) }
        val chatImage = remember(imageBytes, link) {
            ChatImage(
                data = Base64.getEncoder().encodeToString(imageBytes),
                mimeType = markdownImageMimeType(link, imageBytes),
            )
        }
        if (viewerOpen) {
            FullScreenImageViewer(
                image = chatImage,
                bitmap = bitmap,
                onDismiss = { viewerOpen = false },
            )
        }
        return ImageData(
            painter = BitmapPainter(bitmap),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .clickable { viewerOpen = true },
            contentDescription = null,
            contentScale = ContentScale.Fit,
        )
    }
}

private fun markdownImageMimeType(link: String, bytes: ByteArray): String {
    if (link.startsWith("data:image/", ignoreCase = true)) {
        return link.substringAfter("data:").substringBefore(';').lowercase()
    }
    return when {
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A),
        ) -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte() -> "image/jpeg"
        bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII).startsWith("GIF8") -> "image/gif"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        else -> "image/png"
    }
}

internal fun loadMarkdownImage(link: String): ByteArray {
    require(!link.startsWith("data:", ignoreCase = true) || link.startsWith("data:image/", ignoreCase = true)) {
        "Unsupported image data URI"
    }
    if (link.startsWith("data:image/", ignoreCase = true)) {
        val marker = ";base64,"
        val markerIndex = link.indexOf(marker, ignoreCase = true)
        require(markerIndex >= 0) { "Unsupported image data URI" }
        val decoded = Base64.getMimeDecoder().decode(link.substring(markerIndex + marker.length))
        require(decoded.size <= MAX_MARKDOWN_IMAGE_BYTES) { "Markdown image is too large" }
        return decoded
    }

    val url = URL(link)
    require(url.protocol == "https" || url.protocol == "http") { "Unsupported Markdown image URL" }
    val connection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "image/*")
    }
    try {
        connection.connect()
        require(connection.responseCode in 200..299) { "Image request failed: HTTP ${connection.responseCode}" }
        val declaredLength = connection.contentLengthLong
        require(declaredLength < 0 || declaredLength <= MAX_MARKDOWN_IMAGE_BYTES) { "Markdown image is too large" }
        return connection.inputStream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_MARKDOWN_IMAGE_BYTES) { "Markdown image is too large" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    } finally {
        connection.disconnect()
    }
}
