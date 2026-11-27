package br.andrew.nota.agil.security

import br.andrew.nota.agil.infrastructure.security.keycloak.KeycloakJwksProvider
import br.andrew.nota.agil.infrastructure.security.keycloak.KeycloakProperties
import com.sun.net.httpserver.HttpServer
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.net.InetSocketAddress
import java.security.Key
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class KeycloakJwksProviderTest {
    private val contador = AtomicInteger()
    @Volatile private var codigo = 200
    @Volatile private var kid = "chave-1"
    /** Quando definido, o SSO avisa que recebeu a consulta e so responde apos a liberacao. */
    @Volatile private var recebida: CountDownLatch? = null
    @Volatile private var liberar: CountDownLatch? = null
    private val publicKey = Jwts.SIG.RS256.keyPair().build().public as RSAPublicKey
    private val pool = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = Executors.newCachedThreadPool()
        createContext("/realms/teste/protocol/openid-connect/certs") { exchange ->
            contador.incrementAndGet()
            recebida?.countDown()
            liberar?.await(10, TimeUnit.SECONDS)
            val n = encode(publicKey.modulus.toByteArray())
            val e = encode(publicKey.publicExponent.toByteArray())
            val json = """{"keys":[{"kty":"RSA","use":"sig","alg":"RS256","kid":"$kid","n":"$n","e":"$e"}]}"""
            val bytes = json.toByteArray()
            exchange.sendResponseHeaders(codigo, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        start()
    }
    private val clock = RelogioManual(Instant.parse("2026-01-01T00:00:00Z"))
    private val properties = KeycloakProperties().apply {
        url = "http://127.0.0.1:${server.address.port}"; realm = "teste"
    }
    private val provider = KeycloakJwksProvider(properties, clock)

    @AfterEach
    fun close() {
        liberar?.countDown()
        pool.shutdownNow()
        server.stop(0)
        (server.executor as java.util.concurrent.ExecutorService).shutdownNow()
    }

    @Test
    fun `intervalo padrao entre recargas deve ser de 30 segundos`() {
        assertEquals(Duration.ofSeconds(30), KeycloakProperties().jwksRefreshMinInterval)
    }

    @Test
    fun `Spring deve instanciar o provider pelo construtor publico`() {
        org.springframework.context.annotation.AnnotationConfigApplicationContext().use { ctx ->
            ctx.beanFactory.registerSingleton("keycloakProperties", properties)
            ctx.register(KeycloakJwksProvider::class.java)
            ctx.refresh()
            assertEquals(publicKey, ctx.getBean(KeycloakJwksProvider::class.java).chave("chave-1"))
        }
    }

    @Test
    fun `deve consultar JWKS e reutilizar chave em cache`() {
        assertEquals(publicKey, provider.chave("chave-1"))
        assertEquals(publicKey, provider.chave("chave-1"))
        assertEquals(1, contador.get())
    }

    @Test
    fun `cache deve expirar apos cinco minutos`() {
        provider.chave("chave-1")
        clock.avancar(Duration.ofMinutes(4))
        provider.chave("chave-1")
        assertEquals(1, contador.get())
        clock.avancar(Duration.ofMinutes(1).plusSeconds(1))
        assertEquals(publicKey, provider.chave("chave-1"))
        assertEquals(2, contador.get())
    }

    @Test
    fun `rajada de kids forjados nao deve gerar mais de uma consulta ao SSO`() {
        repeat(200) { i -> assertThrows<JwtException> { provider.chave("forjado-$i") } }
        assertEquals(1, contador.get())
        // Chave legitima continua atendida pelo cache, sem nova consulta.
        assertEquals(publicKey, provider.chave("chave-1"))
        assertEquals(1, contador.get())
    }

    @Test
    fun `rajada concorrente de kids forjados deve gerar no maximo uma consulta`() {
        provider.chave("chave-1")
        clock.avancar(Duration.ofSeconds(31))
        val inicio = CountDownLatch(1)
        val tarefas = (1..64).map { i ->
            pool.submit<Result<Key>> { inicio.await(); runCatching { provider.chave("forjado-$i") } }
        }
        inicio.countDown()
        tarefas.forEach { assertTrue(it.get(15, TimeUnit.SECONDS).isFailure) }
        assertEquals(2, contador.get(), "uma consulta inicial + no maximo uma recarga")
    }

    @Test
    fun `kid em cache nao deve esperar recarga lenta em andamento`() {
        provider.chave("chave-1")
        clock.avancar(Duration.ofSeconds(31))
        recebida = CountDownLatch(1)
        liberar = CountDownLatch(1)
        val lenta: Future<Result<Key>> = pool.submit<Result<Key>> { runCatching { provider.chave("desconhecido") } }
        assertTrue(recebida!!.await(5, TimeUnit.SECONDS), "a recarga deveria estar em andamento")

        // Durante a consulta pendurada, a chave conhecida e servida imediatamente.
        val rapida = pool.submit<Key> { provider.chave("chave-1") }
        assertEquals(publicKey, rapida.get(1, TimeUnit.SECONDS))
        assertFalse(lenta.isDone)

        liberar!!.countDown()
        assertTrue(lenta.get(10, TimeUnit.SECONDS).isFailure)
        assertEquals(2, contador.get())
    }

    @Test
    fun `kid em cache expirado nao deve esperar revalidacao lenta em andamento`() {
        provider.chave("chave-1")
        clock.avancar(Duration.ofMinutes(6))
        recebida = CountDownLatch(1)
        liberar = CountDownLatch(1)
        val revalidando = pool.submit<Key> { provider.chave("chave-1") }
        assertTrue(recebida!!.await(5, TimeUnit.SECONDS))

        val outra = pool.submit<Key> { provider.chave("chave-1") }
        assertEquals(publicKey, outra.get(1, TimeUnit.SECONDS))

        liberar!!.countDown()
        assertEquals(publicKey, revalidando.get(10, TimeUnit.SECONDS))
        assertEquals(2, contador.get())
    }

    @Test
    fun `kid novo deve ser aceito apos o intervalo para suportar rotacao`() {
        provider.chave("chave-1")
        kid = "chave-2"
        // Dentro do intervalo: recusado sem rede.
        assertThrows<JwtException> { provider.chave("chave-2") }
        assertEquals(1, contador.get())

        clock.avancar(Duration.ofSeconds(30))
        assertEquals(publicKey, provider.chave("chave-2"))
        assertEquals(2, contador.get())
        // Chave removida do realm deixa de valer depois da recarga.
        assertThrows<JwtException> { provider.chave("chave-1") }
        assertEquals(2, contador.get())
    }

    @Test
    fun `intervalo configurado deve ser respeitado`() {
        properties.jwksRefreshMinInterval = Duration.ofSeconds(60)
        provider.chave("chave-1")
        kid = "chave-2"
        clock.avancar(Duration.ofSeconds(45))
        assertThrows<JwtException> { provider.chave("chave-2") }
        assertEquals(1, contador.get())
        clock.avancar(Duration.ofSeconds(15))
        assertEquals(publicKey, provider.chave("chave-2"))
        assertEquals(2, contador.get())
    }

    @Test
    fun `falha do SSO deve manter chaves antigas e respeitar o intervalo`() {
        provider.chave("chave-1")
        codigo = 503
        clock.avancar(Duration.ofMinutes(6))

        // Cache expirado + SSO fora: chave conhecida continua valendo.
        assertEquals(publicKey, provider.chave("chave-1"))
        assertEquals(2, contador.get())
        assertEquals(publicKey, provider.chave("chave-1"))
        assertThrows<JwtException> { provider.chave("forjado") }
        assertEquals(2, contador.get(), "falha tambem inicia o intervalo")

        clock.avancar(Duration.ofSeconds(30))
        codigo = 200
        assertEquals(publicKey, provider.chave("chave-1"))
        assertEquals(3, contador.get())
    }

    @Test
    fun `deve recusar falha HTTP sem cache chave inexistente e token sem kid`() {
        assertThrows<JwtException> { provider.chave("") }
        assertEquals(0, contador.get())

        codigo = 503
        assertThrows<JwtException> { provider.chave("chave-1") }
        assertEquals(1, contador.get())
        // Retentativa imediata nao chega ao SSO.
        assertThrows<JwtException> { provider.chave("chave-1") }
        assertEquals(1, contador.get())

        clock.avancar(Duration.ofSeconds(30))
        codigo = 200
        assertEquals(publicKey, provider.chave("chave-1"))
        assertThrows<JwtException> { provider.chave("nao-existe") }
        assertEquals(2, contador.get())
    }

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(if (bytes[0] == 0.toByte()) bytes.copyOfRange(1, bytes.size) else bytes)

    private class RelogioManual(@Volatile private var agora: Instant) : Clock() {
        fun avancar(d: Duration) { agora = agora.plus(d) }
        override fun instant(): Instant = agora
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
    }
}
