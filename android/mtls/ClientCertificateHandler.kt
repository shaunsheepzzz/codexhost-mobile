package com.app.pakeplus

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.security.KeyChain
import android.webkit.ClientCertRequest
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** All state and WebView completions belong to the UI thread; keys never enter JavaScript. */
class ClientCertificateHandler(
    private val activity: Activity,
    private val choose: (ClientCertRequest, (String?) -> Unit) -> Unit = { request, callback ->
        KeyChain.choosePrivateKeyAlias(
            activity, { alias -> callback(alias) }, request.keyTypes, request.principals,
            request.host, request.port, null,
        )
    },
    private val load: (String) -> Pair<PrivateKey?, Array<X509Certificate>?> = { alias ->
        KeyChain.getPrivateKey(activity.applicationContext, alias) to
            KeyChain.getCertificateChain(activity.applicationContext, alias)
    },
    private val background: Executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "codex-mobile-client-certificate").apply { isDaemon = true }
    },
    private val ui: (() -> Unit) -> Unit = { task ->
        if (Looper.myLooper() == Looper.getMainLooper()) task()
        else Handler(Looper.getMainLooper()).post { task() }
    },
) {
    private class Pending(val request: ClientCertRequest) {
        var selected = false
        var finished = false
    }

    private val queue = ArrayDeque<Pending>()
    private var active: Pending? = null
    private var closed = false

    fun handle(request: ClientCertRequest) = ui {
        if (closed || activity.isFinishing || activity.isDestroyed) {
            request.ignore()
        } else {
            queue.addLast(Pending(request))
            next()
        }
    }

    private fun next() {
        if (closed || active != null || queue.isEmpty()) return
        val pending = queue.removeFirst()
        active = pending
        try {
            choose(pending.request) { alias -> ui { selected(pending, alias) } }
        } catch (_: Exception) {
            finish(pending, null)
        }
    }

    private fun selected(pending: Pending, alias: String?) {
        if (closed || pending.finished || pending.selected) return
        pending.selected = true
        if (alias == null) {
            finish(pending, null)
            return
        }
        try {
            background.execute {
                val identity = try { load(alias) } catch (_: Exception) { null }
                ui { finish(pending, identity) }
            }
        } catch (_: Exception) {
            finish(pending, null)
        }
    }

    private fun finish(pending: Pending, identity: Pair<PrivateKey?, Array<X509Certificate>?>?) {
        if (pending.finished) return
        pending.finished = true
        val key = identity?.first
        val chain = identity?.second
        try {
            if (!closed && !activity.isFinishing && !activity.isDestroyed && key != null && !chain.isNullOrEmpty()) {
                pending.request.proceed(key, chain)
            } else {
                // ignore, rather than cancel, so a user can retry after dismissing the chooser.
                pending.request.ignore()
            }
        } finally {
            if (active === pending) active = null
            next()
        }
    }

    fun close() = ui {
        closed = true
        active?.let { finish(it, null) }
        while (queue.isNotEmpty()) finish(queue.removeFirst(), null)
        (background as? java.util.concurrent.ExecutorService)?.shutdown()
    }
}
