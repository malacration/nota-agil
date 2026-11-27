package br.andrew.nota.agil.erp.sap

import br.andrew.nota.agil.infrastructure.security.SegurancaDesativada
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Access token de service account (client_credentials) do Keycloak para chamar o sap-rovema.
 *
 * O sap-rovema valida assinatura, issuer e expiracao; a service account precisa do claim
 * `sap_code` (mapper fixo no client) e da role `integracao_contas_pagar` do rules.yml.
 * O token fica em memoria ate 30 s antes de expirar. Nunca e logado.
 */
@Component
class SapRovemaTokenProvider internal constructor(
    private val properties: SapContasPagarProperties,
    private val clock: Clock,
    private val restClient: RestClient,
    /** `nota-agil.seguranca.desativada` / `NOTA_AGIL_SECURITY_DISABLED` (somente teste local/homolog). */
    private val segurancaDesativada: Boolean = false,
) {
    // Construtor explicito: ha outros RestClient no contexto (Qive, SoftExpert) que nao podem
    // ser injetados aqui por engano.
    @Autowired
    constructor(properties: SapContasPagarProperties, @Value(SegurancaDesativada.PROPRIEDADE) segurancaDesativada: Boolean) :
        this(properties, Clock.systemUTC(), criarRestClient(properties), segurancaDesativada)

    private var atual: Pair<String, Instant>? = null

    /**
     * Chamar o sap-rovema SEM `Authorization`: so com a seguranca desativada (teste local/homolog,
     * sap-rovema local com `SAP_ROVEMA_SECURITY_DISABLED`), URL definida e credenciais de service
     * account ausentes. Com credenciais configuradas o token continua sendo obtido e enviado.
     */
    val semToken: Boolean
        get() = segurancaDesativada && properties.url.isNotBlank() && !properties.configurada

    init {
        // Uma vez na subida: sem isso o unico sintoma de credencial esquecida seria o 401 do sap-rovema.
        if (semToken) {
            val ausentes = listOf("tokenUrl" to properties.tokenUrl, "clientId" to properties.clientId,
                "clientSecret" to properties.clientSecret).filter { it.second.isBlank() }.map { it.first }
            log.warn("SEGURANCA DESATIVADA (NOTA_AGIL_SECURITY_DISABLED): consultas ao sap-rovema vao SEM " +
                "Authorization (credenciais ausentes: {}). So funciona com o sap-rovema local com " +
                "SAP_ROVEMA_SECURITY_DISABLED=true; um sap-rovema normal respondera 401.",
                ausentes.joinToString { "integracao.erp.sap.$it" })
        }
    }

    @Synchronized
    fun token(): String {
        atual?.let { (token, expira) -> if (clock.instant().isBefore(expira)) return token }
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
            add("client_id", properties.clientId)
            add("client_secret", properties.clientSecret)
        }
        val resposta = try {
            restClient.post().uri(properties.tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(TokenResponse::class.java)
        } catch (e: org.springframework.web.client.RestClientResponseException) {
            log.warn("Keycloak recusou o token da service account: status={}", e.statusCode.value())
            throw br.andrew.nota.agil.erp.FalhaTokenServiceAccount(e.statusCode.value())
        }
        val token = resposta?.accessToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Keycloak nao devolveu access_token para a integracao SAP")
        val validade = Duration.ofSeconds((resposta.expiresIn ?: 60L).coerceAtLeast(31L) - 30L)
        atual = token to clock.instant().plus(validade)
        return token
    }

    @Synchronized
    fun invalidar() {
        atual = null
    }

    data class TokenResponse(
        @com.fasterxml.jackson.annotation.JsonProperty("access_token") val accessToken: String? = null,
        @com.fasterxml.jackson.annotation.JsonProperty("expires_in") val expiresIn: Long? = null,
    )

    companion object {
        private val log = LoggerFactory.getLogger(SapRovemaTokenProvider::class.java)

        fun criarRestClient(properties: SapContasPagarProperties): RestClient = RestClient.builder()
            .requestFactory(SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds.toLong()))
                setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds.toLong()))
            })
            .build()
    }
}
