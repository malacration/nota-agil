package br.andrew.nota.agil.infrastructure.security.keycloak

import io.jsonwebtoken.JwtException
import io.jsonwebtoken.security.Jwks
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.Key
import java.security.interfaces.RSAPublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Chaves publicas do realm (JWKS) em cache, com protecao contra recargas abusivas.
 *
 * O `kid` vem do cabecalho de um JWT ainda nao autenticado, entao nao pode decidir
 * sozinho quando o SSO e consultado:
 * - Kid presente no cache e cache valido (5 min): leitura sem lock e sem rede.
 * - Kid presente no cache, cache expirado: uma unica thread revalida ([ReentrantLock.tryLock]);
 *   as demais, e a propria thread se a consulta falhar, seguem com a chave antiga.
 * - Kid desconhecido: so consulta o SSO se ja passou [KeycloakProperties.jwksRefreshMinInterval]
 *   desde a ultima tentativa (com sucesso ou falha). Dentro do intervalo e recusado sem rede.
 *   Uma consulta por vez; quem chega durante a consulta aguarda o resultado (rotacao legitima).
 */
@Component
class KeycloakJwksProvider internal constructor(
    private val properties: KeycloakProperties,
    private val clock: Clock,
) {
    @Autowired
    constructor(properties: KeycloakProperties) : this(properties, Clock.systemUTC())

    private class Snapshot(val keys: Map<String, Key>, val validade: Instant)

    private val http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build()
    private val lock = ReentrantLock()
    @Volatile private var cache = Snapshot(emptyMap(), Instant.EPOCH)
    /** Fim da ultima consulta ao SSO; base do intervalo minimo entre recargas. */
    @Volatile private var ultimaTentativa: Instant? = null

    fun chave(kid: String): Key {
        if (kid.isBlank()) throw JwtException("Token sem identificador de chave")
        val atual = cache
        val conhecida = atual.keys[kid]
        if (conhecida != null) {
            if (clock.instant().isBefore(atual.validade)) return conhecida
            return revalidar(kid, atual, conhecida)
        }
        return buscarDesconhecida(kid)
    }

    private fun revalidar(kid: String, atual: Snapshot, conhecida: Key): Key {
        if (podeConsultar() && lock.tryLock()) {
            try {
                if (cache === atual && podeConsultar()) {
                    try {
                        atualizar()
                    } catch (e: Exception) {
                        registrarFalha(e)
                    }
                }
            } finally {
                lock.unlock()
            }
        }
        val depois = cache
        // Sem recarga (em andamento, intervalo ou falha): mantem a chave antiga.
        // Recarga concluida sem o kid: a chave saiu do realm e deixa de valer.
        return depois.keys[kid] ?: if (depois === atual) conhecida else throw naoEncontrada()
    }

    private fun buscarDesconhecida(kid: String): Key {
        if (!podeConsultar()) throw naoEncontrada()
        val obtido = try {
            lock.tryLock(ESPERA_MAXIMA.toMillis(), TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw JwtException("Validacao do token interrompida")
        }
        if (!obtido) throw JwtException("Nao foi possivel carregar as chaves publicas do SSO")
        try {
            // Outra thread pode ter acabado de recarregar enquanto esta aguardava.
            cache.keys[kid]?.let { return it }
            if (!podeConsultar()) throw naoEncontrada()
            try {
                atualizar()
            } catch (e: Exception) {
                registrarFalha(e)
                throw JwtException("Nao foi possivel carregar as chaves publicas do SSO")
            }
        } finally {
            lock.unlock()
        }
        return cache.keys[kid] ?: throw naoEncontrada()
    }

    private fun podeConsultar(): Boolean {
        val ultima = ultimaTentativa ?: return true
        val intervalo = properties.jwksRefreshMinInterval.takeUnless { it.isNegative } ?: Duration.ZERO
        return !clock.instant().isBefore(ultima.plus(intervalo))
    }

    /** Deve ser chamado com [lock] obtido. So substitui o cache em caso de sucesso. */
    private fun atualizar() {
        try {
            val request = HttpRequest.newBuilder(URI.create(properties.jwksUri()))
                .timeout(TIMEOUT).GET().build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) throw JwtException("SSO respondeu HTTP ${response.statusCode()} ao JWKS")
            val novas = Jwks.setParser().build().parse(response.body()).getKeys()
                .filter { it["use"] == null || it["use"] == "sig" }
                .mapNotNull { jwk -> (jwk.toKey() as? RSAPublicKey)?.let { jwk.id to it } }
                .toMap()
            if (novas.isEmpty()) throw JwtException("SSO nao retornou chaves de assinatura RSA")
            cache = Snapshot(novas, clock.instant().plus(VALIDADE_CACHE))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } finally {
            ultimaTentativa = clock.instant()
        }
    }

    private fun registrarFalha(e: Exception) {
        // Nunca inclui token nem kid (controlado pelo cliente); so a natureza da falha.
        val detalhe = if (e.javaClass == JwtException::class.java) e.message else e.javaClass.simpleName
        log.warn("Falha ao carregar JWKS do SSO ({}); proxima tentativa em no minimo {}",
            detalhe, properties.jwksRefreshMinInterval)
    }

    private fun naoEncontrada() = JwtException("Chave do token nao encontrada no realm")

    private companion object {
        val log = LoggerFactory.getLogger(KeycloakJwksProvider::class.java)
        val TIMEOUT: Duration = Duration.ofSeconds(5)
        val VALIDADE_CACHE: Duration = Duration.ofMinutes(5)
        /** Conexao (5 s) + resposta (5 s) + folga: limita quem aguarda uma consulta em andamento. */
        val ESPERA_MAXIMA: Duration = Duration.ofSeconds(12)
    }
}
