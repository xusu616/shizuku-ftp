package org.primftpd.share

import android.app.Application
import android.net.Uri
import android.os.PowerManager
import androidx.annotation.MainThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import org.primftpd.util.FilenameUnique
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Owns one copy operation without retaining the Activity or its views. */
class ShareCopyViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        // Preserve serial copies across share screens so filename selection and creation cannot race.
        private val executor = Executors.newSingleThreadExecutor()
    }

    data class Progress(val completed: Int, val total: Int, val failures: Int, val finished: Boolean)

    private val logger = LoggerFactory.getLogger(javaClass)
    private var copyTask: Future<*>? = null
    private val mutableProgress = MutableLiveData<Progress>()
    val progress: LiveData<Progress> = mutableProgress
    lateinit var targetDir: TargetDir
        private set

    fun hasStarted(): Boolean = mutableProgress.value != null

    @MainThread
    fun start(target: TargetDir, uris: List<Uri>?, contents: List<String>?, type: String?) {
        // Recreation or a repeated lifecycle callback must observe the existing operation.
        if (hasStarted()) return
        val sourceUris = uris?.toList()
        val sourceContents = contents?.toList()
        val total = sourceUris?.size ?: sourceContents?.size ?: 0
        require(total > 0) { "A copy operation needs at least one item" }
        targetDir = target
        mutableProgress.value = Progress(0, total, 0, false)
        copyTask = executor.submit {
            val context = getApplication<Application>()
            val powerManager = context.getSystemService(PowerManager::class.java)
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pFTPd:share-copy")
            wakeLock.setReferenceCounted(false)
            wakeLock.acquire(60 * 60 * 1000L)
            var failures = 0
            try {
                for (index in 0 until total) {
                    if (Thread.currentThread().isInterrupted) return@submit
                    try {
                        if (sourceUris != null) {
                            val uri = sourceUris[index]
                            val filename = FilenameUnique.filename(
                                uri, sourceContents?.getOrNull(index), type, target, context
                            )
                            val input = context.contentResolver.openInputStream(uri)
                                ?: throw IOException("Provider could not open $uri")
                            input.use {
                                target.createOutStream(filename).use { output ->
                                    copySharedStream(input, output)
                                }
                            }
                        } else {
                            val content = checkNotNull(sourceContents)[index]
                            val filename = FilenameUnique.filename(null, null, "txt", target, context)
                            target.createOutStream(filename).bufferedWriter().use { writer ->
                                writer.write(content)
                                writer.newLine()
                            }
                        }
                    } catch (e: InterruptedIOException) {
                        if (Thread.currentThread().isInterrupted) return@submit
                        logger.warn("Provider interrupted shared item {}", index, e)
                        failures++
                    } catch (e: IOException) {
                        logger.warn("Could not copy shared item {}", index, e)
                        failures++
                    } catch (e: SecurityException) {
                        // The sender/provider can revoke its URI grant while we copy.
                        logger.warn("Shared item is no longer accessible: {}", index, e)
                        failures++
                    } catch (e: IllegalArgumentException) {
                        // External providers can reject invalid/removed document URIs.
                        logger.warn("Provider rejected shared item {}", index, e)
                        failures++
                    }
                    mutableProgress.postValue(Progress(index + 1, total, failures, false))
                }
            } finally {
                if (wakeLock.isHeld) wakeLock.release()
            }
            mutableProgress.postValue(Progress(total, total, failures, true))
        }
    }

    override fun onCleared() {
        // Finishing the screen cancels copying; a configuration change retains this ViewModel.
        copyTask?.cancel(true)
    }
}

internal fun copySharedStream(input: InputStream, output: OutputStream) {
    val buffer = ByteArray(8192)
    while (true) {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Share copy cancelled")
        val count = input.read(buffer)
        if (count == -1) return
        output.write(buffer, 0, count)
    }
}
