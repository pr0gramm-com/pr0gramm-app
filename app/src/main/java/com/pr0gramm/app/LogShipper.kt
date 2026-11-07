package com.pr0gramm.app

import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * Sends log lines over plain TCP to "host:port". Only enabled in builds
 * made with -PlogShipper=host:port, meant for debugging on a device.
 */
class LogShipper(target: String) {
    // shortcut: drops lines when the queue is full or the receiver is down, fine for debugging only.
    private val queue = LinkedBlockingQueue<String>(10_000)
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    init {
        val host = target.substringBeforeLast(':')
        val port = target.substringAfterLast(':').toInt()

        thread(name = "LogShipper", isDaemon = true) {
            while (true) {
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(host, port), 5000)
                        val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)

                        while (true) {
                            writer.write(queue.take())
                            if (queue.isEmpty()) writer.flush()
                        }
                    }
                } catch (_: Exception) {
                    Thread.sleep(5000)
                }
            }
        }
    }

    fun send(level: Int, tag: String, message: String) {
        val levelStr = Logging.levels.getOrNull(level)
        val time = synchronized(dateFormat) { dateFormat.format(Date()) }
        queue.offer("$time $levelStr $tag: $message\n")
    }
}
