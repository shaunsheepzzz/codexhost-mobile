package com.app.pakeplus

import android.app.Activity
import android.webkit.ClientCertRequest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ClientCertificateHandlerTest {
    private class Request(val server: String = "gateway", val serverPort: Int = 19445) : ClientCertRequest() {
        val types = arrayOf("RSA")
        val issuers = arrayOf<Principal>(javax.security.auth.x500.X500Principal("CN=issuer"))
        var proceeded = 0
        var ignored = 0
        var cancelled = 0
        var receivedKey: PrivateKey? = null
        var receivedChain: Array<X509Certificate>? = null
        override fun getHost() = server
        override fun getPort() = serverPort
        override fun getKeyTypes() = types
        override fun getPrincipals() = issuers
        override fun proceed(key: PrivateKey, chain: Array<X509Certificate>) {
            proceeded++; receivedKey = key; receivedChain = chain
        }
        override fun ignore() { ignored++ }
        override fun cancel() { cancelled++ }
        fun completions() = proceeded + ignored + cancelled
    }

    private class Fixture {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val selections = mutableListOf<Pair<ClientCertRequest, (String?) -> Unit>>()
        val work = ArrayDeque<Runnable>()
        val ui = ArrayDeque<() -> Unit>()
        val key = mock(PrivateKey::class.java)
        val chain = arrayOf(mock(X509Certificate::class.java), mock(X509Certificate::class.java))
        var load: (String) -> Pair<PrivateKey?, Array<X509Certificate>?> = { key to chain }
        val handler = ClientCertificateHandler(activity,
            choose = { request, callback -> selections.add(request to callback) },
            load = { alias -> load(alias) },
            background = Executor { work.add(it) },
            ui = { ui.add(it) },
        )
        fun flush() { while (ui.isNotEmpty()) ui.removeFirst().invoke() }
        fun answer(alias: String?) { selections.last().second(alias); flush() }
        fun finishLoad() { work.removeFirst().run(); flush() }
    }

    @Test fun preservesRequestAndReturnsFullIdentityExactlyOnce() {
        val f = Fixture(); val r = Request()
        f.handler.handle(r); f.flush()
        assertSame(r, f.selections.single().first)
        assertEquals("gateway", f.selections.single().first.host)
        assertEquals(19445, f.selections.single().first.port)
        assertArrayEquals(r.types, f.selections.single().first.keyTypes)
        assertArrayEquals(r.issuers, f.selections.single().first.principals)
        f.answer("identity"); f.finishLoad()
        assertSame(f.key, r.receivedKey); assertArrayEquals(f.chain, r.receivedChain)
        f.answer("identity"); assertEquals(1, r.completions()); assertTrue(f.work.isEmpty())
    }

    @Test fun cancellationIgnoresAndAllowsRetry() {
        val f = Fixture(); val r = Request()
        f.handler.handle(r); f.flush(); f.answer(null)
        assertEquals(1, r.ignored); assertEquals(0, r.cancelled)
        val retry = Request(); f.handler.handle(retry); f.flush()
        assertEquals(2, f.selections.size)
        f.answer("identity"); f.finishLoad(); assertEquals(1, retry.proceeded)
    }

    @Test fun serializesConcurrentRequestsWithoutReusingAliases() {
        val f = Fixture(); val first = Request(); val second = Request("other", 19446)
        f.handler.handle(first); f.handler.handle(second); f.flush()
        assertEquals(1, f.selections.size)
        f.answer("first"); assertEquals(1, f.selections.size)
        f.finishLoad(); assertEquals(2, f.selections.size)
        assertSame(second, f.selections.last().first)
        f.answer("second"); f.finishLoad()
        assertEquals(1, first.proceeded); assertEquals(1, second.proceeded)
    }

    @Test fun missingIdentityOrLoadFailureNeverProceeds() {
        val failures: List<(String) -> Pair<PrivateKey?, Array<X509Certificate>?>> = listOf(
            { null to null }, { mock(PrivateKey::class.java) to emptyArray() },
            { mock(PrivateKey::class.java) to null }, { throw IllegalStateException("removed") },
        )
        for (failure in failures) {
            val f = Fixture(); val r = Request(); f.load = failure
            f.handler.handle(r); f.flush(); f.answer("identity"); f.finishLoad()
            assertEquals(1, r.ignored); assertEquals(0, r.proceeded)
        }
    }

    @Test fun closeFinishesActiveQueuedAndFutureRequestsAndDiscardsLateSelection() {
        val f = Fixture(); val first = Request(); val queued = Request()
        f.handler.handle(first); f.handler.handle(queued); f.flush()
        f.handler.close(); f.flush(); f.answer("late")
        val future = Request(); f.handler.handle(future); f.flush()
        for (r in listOf(first, queued, future)) { assertEquals(1, r.ignored); assertEquals(1, r.completions()) }
        assertTrue(f.work.isEmpty()); assertEquals(1, f.selections.size)
    }

    @Test fun closeDiscardsLateKeyResult() {
        val f = Fixture(); val r = Request()
        f.handler.handle(r); f.flush(); f.answer("identity")
        f.handler.close(); f.flush(); f.finishLoad()
        assertEquals(1, r.ignored); assertEquals(1, r.completions())
    }

    @Test fun selectorFailureEndsRequestAndMovesToNext() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val handler = ClientCertificateHandler(activity, choose = { _, _ -> throw IllegalStateException() }, ui = { it() })
        val r = Request(); handler.handle(r); assertEquals(1, r.ignored)
    }
}
