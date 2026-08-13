package io.github.zixt233.pirt.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PirtMarkdownImagesTest {
    @Test
    fun decodesBase64ImageDataUri() {
        assertArrayEquals(
            byteArrayOf(1, 2, 3, 4),
            loadMarkdownImage("data:image/png;base64,AQIDBA=="),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonImageDataUri() {
        loadMarkdownImage("data:text/plain;base64,SGVsbG8=")
    }
}
