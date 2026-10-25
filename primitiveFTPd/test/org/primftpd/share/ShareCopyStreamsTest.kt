package org.primftpd.share

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException

class ShareCopyStreamsTest {
    @Test
    fun copiesAllBytesAcrossBufferBoundaries() {
        val bytes = ByteArray(100_003) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        copySharedStream(ByteArrayInputStream(bytes), output)
        assertArrayEquals(bytes, output.toByteArray())
    }

    @Test
    fun providerReadFailureReachesCallerInsteadOfReportingSuccess() {
        val failure = IOException("Provider disconnected")
        val input = object : InputStream() {
            override fun read(): Int = throw failure
        }
        try {
            copySharedStream(input, ByteArrayOutputStream())
            fail("A failed copy must not report success")
        } catch (actual: IOException) {
            assertSame(failure, actual)
        }
    }

    @Test
    fun cancellationStopsCopyWithoutClearingInterruptFlag() {
        val output = ByteArrayOutputStream()
        Thread.currentThread().interrupt()
        try {
            copySharedStream(ByteArrayInputStream(byteArrayOf(1, 2, 3)), output)
            fail("Cancelled copy must stop")
        } catch (expected: InterruptedIOException) {
            assertTrue(Thread.currentThread().isInterrupted)
            assertEquals(0, output.size())
        } finally {
            Thread.interrupted()
        }
    }
}
