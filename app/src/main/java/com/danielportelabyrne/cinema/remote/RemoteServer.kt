package com.danielportelabyrne.cinema.remote

import android.app.Activity
import android.app.AlertDialog
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.danielportelabyrne.cinema.data.remote.RemoteProtocol
import com.danielportelabyrne.cinema.data.remote.RemoteProtocol.Key
import com.danielportelabyrne.cinema.platform.Log
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

/**
 * Cinema's side of the phone remote (see [RemoteProtocol]): answers phones looking for TVs, shows a pairing code, and
 * turns the phone's presses into key presses in Cinema's own window. Keys can only reach Cinema itself (Android lets an
 * app inject input into its own windows, nothing else), so the remote works while Cinema is on screen.
 */
object RemoteServer {
    private const val TAG = "RemoteServer"
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newCachedThreadPool { r -> Thread(r, "cinema-remote").apply { isDaemon = true } }

    @Volatile private var started = false
    @Volatile private var resumed: Activity? = null
    private var window: RemoteProtocol.PairingWindow? = null
    private var dialog: AlertDialog? = null
    private lateinit var app: Application

    fun start(application: Application) {
        if (started) return
        started = true
        app = application
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { resumed = a }
            override fun onActivityPaused(a: Activity) { if (resumed === a) resumed = null }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        pool.execute(::discovery)
        pool.execute(::http)
    }

    private val tokens get() = app.getSharedPreferences("remote_tokens", Context.MODE_PRIVATE)
    private fun known(token: String?) = !token.isNullOrBlank() && tokens.contains(token)

    private fun tvName(): String = android.provider.Settings.Global.getString(app.contentResolver, "device_name")
        ?: "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

    private fun discovery() {
        try {
            DatagramSocket(null).use { s ->
                s.reuseAddress = true
                s.broadcast = true
                s.bind(InetSocketAddress(RemoteProtocol.DISCOVERY_PORT))
                val buf = ByteArray(256)
                while (true) {
                    val p = DatagramPacket(buf, buf.size)
                    s.receive(p)
                    if (String(p.data, 0, p.length).trim() != RemoteProtocol.HELLO) continue
                    val reply = RemoteProtocol.announce(tvName()).toByteArray()
                    s.send(DatagramPacket(reply, reply.size, p.address, p.port))
                }
            }
        } catch (e: Exception) { Log.w(TAG, "discovery stopped: ${e.javaClass.simpleName}") }
    }

    private fun http() {
        try {
            ServerSocket().use { server ->
                server.reuseAddress = true
                server.bind(InetSocketAddress(RemoteProtocol.PORT))
                while (true) { val c = server.accept(); pool.execute { handle(c) } }
            }
        } catch (e: Exception) { Log.w(TAG, "remote server stopped: ${e.javaClass.simpleName}") }
    }

    private class Req(val method: String, val path: String, val headers: Map<String, String>, val body: String)

    private fun read(input: InputStream): Req? {
        val ins = BufferedInputStream(input)
        fun line(): String? {
            val sb = StringBuilder()
            while (true) {
                val b = ins.read(); if (b < 0) return if (sb.isEmpty()) null else sb.toString()
                if (b == '\n'.code) return sb.toString().trimEnd('\r')
                sb.append(b.toChar()); if (sb.length > 8192) return null
            }
        }
        val first = line()?.split(' ') ?: return null
        if (first.size < 2) return null
        val headers = HashMap<String, String>()
        while (true) { val l = line() ?: break; if (l.isEmpty()) break; headers[l.substringBefore(':').trim().lowercase()] = l.substringAfter(':').trim() }
        val len = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 16_384) ?: 0
        val body = ByteArray(len); var got = 0
        while (got < len) { val n = ins.read(body, got, len - got); if (n < 0) break; got += n }
        return Req(first[0], first[1].substringBefore('?'), headers, String(body, 0, got, Charsets.UTF_8))
    }

    private fun handle(socket: Socket) = socket.use { s ->
        s.soTimeout = 5000
        val req = try { read(s.getInputStream()) } catch (_: Exception) { null } ?: return@use
        val (status, body) = route(req)
        val bytes = body.toByteArray()
        val reason = when (status) { 200 -> "OK"; 401 -> "Unauthorized"; 403 -> "Forbidden"; 404 -> "Not Found"; 409 -> "Conflict"; else -> "Bad Request" }
        s.getOutputStream().apply {
            write("HTTP/1.1 $status $reason\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes); flush()
        }
    }

    private fun route(r: Req): Pair<Int, String> {
        val form = RemoteProtocol.form(r.body)
        return when (r.method to r.path) {
            "POST" to "/pair" -> showCode()
            "POST" to "/pair/confirm" -> confirm(form["code"])
            "GET" to "/ping" -> if (known(r.headers[RemoteProtocol.TOKEN_HEADER.lowercase()])) 200 to "ok" else 401 to ""
            "POST" to "/key" -> if (!known(r.headers[RemoteProtocol.TOKEN_HEADER.lowercase()])) 401 to "" else {
                val key = RemoteProtocol.key(form["key"]) ?: return 400 to "unknown key"
                if (resumed == null) return 409 to "Cinema is not on screen"
                press(code(key)); 200 to "ok"
            }
            "POST" to "/text" -> if (!known(r.headers[RemoteProtocol.TOKEN_HEADER.lowercase()])) 401 to "" else {
                val text = form["text"].orEmpty().take(500)
                if (resumed == null) return 409 to "Cinema is not on screen"
                type(text); 200 to "ok"
            }
            else -> 404 to ""
        }
    }

    @Synchronized private fun showCode(): Pair<Int, String> {
        val activity = resumed ?: return 409 to "Open Cinema on the TV first"
        val code = RemoteProtocol.newCode()
        window = RemoteProtocol.PairingWindow(code, System.currentTimeMillis())
        main.post {
            dialog?.dismiss()
            dialog = AlertDialog.Builder(activity)
                .setTitle("Pair a phone remote")
                .setMessage("Enter this code on your phone:\n\n$code")
                .setPositiveButton("Cancel") { d, _ -> d.dismiss(); synchronized(this) { window = null } }
                .show()
            main.postDelayed({ dialog?.dismiss() }, RemoteProtocol.CODE_MILLIS)
        }
        return 200 to "ok"
    }

    @Synchronized private fun confirm(code: String?): Pair<Int, String> {
        val w = window ?: return 403 to ""
        if (!w.check(code, System.currentTimeMillis())) { if (!w.open(System.currentTimeMillis())) window = null; return 403 to "" }
        window = null
        main.post { dialog?.dismiss(); dialog = null }
        val token = RemoteProtocol.newToken()
        tokens.edit().putLong(token, System.currentTimeMillis()).apply()
        return 200 to token
    }

    private fun code(k: Key): Int = when (k) {
        Key.UP -> KeyEvent.KEYCODE_DPAD_UP; Key.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
        Key.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT; Key.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
        Key.OK -> KeyEvent.KEYCODE_DPAD_CENTER; Key.BACK -> KeyEvent.KEYCODE_BACK
        Key.HOME -> KeyEvent.KEYCODE_MOVE_HOME; Key.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
        Key.REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND; Key.FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
        Key.MENU -> KeyEvent.KEYCODE_MENU; Key.SEARCH -> KeyEvent.KEYCODE_SEARCH; Key.DEL -> KeyEvent.KEYCODE_DEL
    }

    /**
     * Hands the key to Cinema's own window, as the system does for a real remote. (Injecting through Instrumentation is refused
     * on a TV: it needs a permission only the system holds.) Runs on the main thread, in the order the phone sent them.
     */
    private fun press(code: Int) {
        main.post {
            val a = resumed ?: return@post
            val t = SystemClock.uptimeMillis()
            try {
                a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
                a.dispatchKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
            } catch (e: Exception) { Log.w(TAG, "key not delivered: ${e.javaClass.simpleName}") }
        }
    }

    /** Types [text] into whatever has focus, as a keyboard would. */
    private fun type(text: String) {
        main.post {
            val a = resumed ?: return@post
            try {
                KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD).getEvents(text.toCharArray())?.forEach { a.dispatchKeyEvent(it) }
            } catch (e: Exception) { Log.w(TAG, "text not delivered: ${e.javaClass.simpleName}") }
        }
    }
}
