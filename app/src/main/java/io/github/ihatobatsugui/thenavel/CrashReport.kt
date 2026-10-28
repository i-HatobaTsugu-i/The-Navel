package io.github.ihatobatsugui.thenavel

import android.content.Context
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 闪退现场：没有 logcat 权限时，把未捕获异常的堆栈写进内部存储，
 * 下次启动由 MainActivity 读出来显示在屏幕上。
 */
object CrashReport {

    private const val FILE = "crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val trace = StringWriter()
                throwable.printStackTrace(PrintWriter(trace))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                File(app.filesDir, FILE).writeText("$stamp\n$trace")
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
            }
        }
    }

    fun read(context: Context): String? {
        val file = File(context.filesDir, FILE)
        return if (file.exists()) file.readText() else null
    }

    fun clear(context: Context) {
        val file = File(context.filesDir, FILE)
        if (file.exists()) file.delete()
    }
}
