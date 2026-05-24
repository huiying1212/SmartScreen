package com.datacollector.android.app.crash

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Captures uncaught exceptions and writes them to a rotating local file.
 * Files live under filesDir/crashes/ and are kept for up to 14 days.
 * Users opt-in to upload these via Settings.
 */
@Singleton
class CrashLogger @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val tag = "CrashLogger"

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                writeCrash(thread, throwable)
            } catch (t: Throwable) {
                Log.e(tag, "Failed to log crash", t)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun writeCrash(thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, "crashes").apply { mkdirs() }
        rotate(dir)
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val out = File(dir, "crash_$ts.txt")
        out.bufferedWriter().use { w ->
            w.write("Time: ${Date()}\n")
            w.write("Thread: ${thread.name}\n")
            w.write("Message: ${throwable.message}\n\n")
            PrintWriter(w).use { pw -> throwable.printStackTrace(pw) }
        }
        Log.e(tag, "Wrote crash report ${out.name}")
    }

    private fun rotate(dir: File) {
        val cutoff = System.currentTimeMillis() - 14L * 24 * 60 * 60 * 1000
        dir.listFiles()?.forEach { f ->
            if (f.lastModified() < cutoff) f.delete()
        }
    }

    fun listReports(): List<File> =
        File(context.filesDir, "crashes")
            .takeIf { it.isDirectory }
            ?.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
}
