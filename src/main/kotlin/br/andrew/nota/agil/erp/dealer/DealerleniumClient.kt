package br.andrew.nota.agil.erp.dealer

import br.andrew.nota.agil.erp.sap.SapContasPagarProperties
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClient
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant

/**
 * Acesso do Nota Agil ao dealerlenium, que concentra as integracoes com o Dealer (Dealernet Workflow).
 * Somente variaveis de ambiente:
 * - `INTEGRACAO_ERP_DEALER_URL`: URL base do dealerlenium (ex.: http://dealerlenium:8080);
 * - `INTEGRACAO_ERP_DEALER_TOKENURL`, `_CLIENTID`, `_CLIENTSECRET`: service account Keycloak
 *   (client_credentials). Vazias, usam as do SAP (`INTEGRACAO_ERP_SAP_*`): a mesma service account
 *   do Nota Agil, com a role `integracao_contas_pagar` exigida pelos dois servicos.
 * Sem URL ou sem credencial, o Dealer fica indisponivel.
 */
@Component
@ConfigurationProperties(prefix = "integracao.erp.dealer")
class DealerleniumProperties {
    var url: String = ""
    var tokenUrl: String = ""
    var clientId: String = ""
    var clientSecret: String = ""
    var connectTimeoutSeconds: Int = 10
    /** O dealerlenium chama o WS do Dealer, que nao pagina: consulta de 31 dias pode demorar. */
    var readTimeoutSeconds: Int = 120
}

/** Credenciais efetivas: as do Dealer ou, vazias, as do SAP. */
internal data class CredenciaisDealerlenium(val tokenUrl: String, val clientId: String, val clientSecret: String) {
    val completas: Boolean get() = listOf(tokenUrl, clientId, clientSecret).all { it.isNotBlank() }

    companion object {
        fun de(dealer: DealerleniumProperties, sap: SapContasPagarProperties): CredenciaisDealerlenium {
            val proprias = CredenciaisDealerlenium(dealer.tokenUrl, dealer.clientId, dealer.clientSecret)
            return if (proprias.completas) proprias else CredenciaisDealerlenium(sap.tokenUrl, sap.clientId, sap.clientSecret)
        }
    }
}

/**
 * Cliente HTTP do dealerlenium (`/api/contas-pagar/...`). Token em memoria ate 30 s antes de expirar;
 * em 401 renova uma vez. Nao loga token, CNPJ, numero de nota nem valores.
 */
@Component
class DealerleniumClient internal constructor(
    private val properties: DealerleniumProperties,
    private val sap: SapContasPagarProperties,
    private val restClient: RestClient,
    private val clock: Clock,
) {
    @Autowired
    constructor(properties: DealerleniumProperties, sap: SapContasPagarProperties) : this(
        properties, sap,
        RestClient.builder().requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds.toLong()))
            setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds.toLong()))
        }).build(),
        Clock.systemUTC(),
    )

    private val log = LoggerFactory.getLogger(javaClass)
    private var token: Pair<String, Instant>? = null

    val configurado: Boolean get() = properties.url.isNotBlank() && CredenciaisDealerlenium.de(properties, sap).completas

    fun listar(requisicao: ListagemNotasDealerRequest): List<NotaEntradaDealerlenium> =
        chamar("listagem de notas") {
            restClient.post().uri(url(CAMINHO_LISTAGEM))
                .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${token()}")
                .body(requisicao)
                .retrieve().body(ListagemNotasDealerResponse::class.java)?.notas ?: emptyList()
        }

    fun empresas(): List<EmpresaDealerlenium> =
        chamar("listagem de empresas") {
            restClient.get().uri(url(CAMINHO_EMPRESAS))
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer ${token()}")
                .retrieve().body(Array<EmpresaDealerlenium>::class.java)?.toList() ?: emptyList()
        }

    /**
     * Ids das empresas do USUARIO no dealerlenium (`GET /usuario/empresas` com o token do proprio
     * usuario; o dealerlenium libera pelas roles com o id da empresa).
     */
    fun empresasDoUsuario(tokenUsuario: String): List<String> {
        if (properties.url.isBlank()) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Integracao com o Dealer nao configurada (INTEGRACAO_ERP_DEALER_URL)")
        return try {
            restClient.get().uri(url(CAMINHO_EMPRESAS_USUARIO))
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $tokenUsuario")
                .retrieve().body(Array<EmpresaUsuarioDealer>::class.java)
                ?.mapNotNull { it.id?.trim()?.uppercase()?.takeIf(String::isNotEmpty) }?.distinct() ?: emptyList()
        } catch (e: HttpStatusCodeException) {
            log.warn("Empresas do usuario no dealerlenium: status={}", e.statusCode.value())
            throw ResponseStatusException(HttpStatus.FORBIDDEN, when (e.statusCode.value()) {
                401, 403 -> "O dealerlenium recusou seu acesso ao consultar suas empresas do Dealer"
                else -> "Falha ao consultar suas empresas no Dealer (HTTP ${e.statusCode.value()})"
            })
        } catch (e: Exception) {
            log.warn("Empresas do usuario no dealerlenium: {}", e.javaClass.simpleName)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Sem resposta valida do dealerlenium ao consultar suas empresas")
        }
    }

    private fun url(caminho: String) = properties.url.trimEnd('/') + caminho

    private fun <T> chamar(operacao: String, chamada: () -> T): T {
        if (!configurado) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Integracao com o Dealer nao configurada (INTEGRACAO_ERP_DEALER_URL e service account)")
        return try {
            try {
                chamada()
            } catch (e: HttpClientErrorException.Unauthorized) {
                invalidar()
                chamada()
            }
        } catch (e: HttpStatusCodeException) {
            val status = e.statusCode.value()
            val mensagem = runCatching { e.getResponseBodyAs(ErroDealerlenium::class.java)?.message }.getOrNull()
            log.warn("Dealerlenium: {} falhou com status={}", operacao, status)
            throw when (status) {
                400 -> ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem ?: "Filtro invalido")
                401, 403 -> ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "dealerlenium recusou o token (HTTP $status): a service account precisa da role integracao_contas_pagar")
                // 502/503/504 do dealerlenium ja vem com a causa do WS do Dealer, sem credenciais.
                in 500..599 -> ResponseStatusException(HttpStatus.BAD_GATEWAY, mensagem ?: "Falha no dealerlenium (HTTP $status)")
                else -> ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha no dealerlenium (HTTP $status)")
            }
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: br.andrew.nota.agil.erp.FalhaTokenServiceAccount) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, br.andrew.nota.agil.erp.FalhaTokenServiceAccount.MENSAGEM)
        } catch (e: Exception) {
            log.warn("Dealerlenium: {} falhou: {}", operacao, e.javaClass.simpleName)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Sem resposta valida do dealerlenium")
        }
    }

    @Synchronized
    internal fun token(): String {
        token?.let { (valor, expira) -> if (clock.instant().isBefore(expira)) return valor }
        val credenciais = CredenciaisDealerlenium.de(properties, sap)
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
            add("client_id", credenciais.clientId)
            add("client_secret", credenciais.clientSecret)
        }
        val resposta = try {
            restClient.post().uri(credenciais.tokenUrl)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON)
                .body(form).retrieve().body(TokenResponse::class.java)
        } catch (e: org.springframework.web.client.RestClientResponseException) {
            log.warn("Keycloak recusou o token da service account: status={}", e.statusCode.value())
            throw br.andrew.nota.agil.erp.FalhaTokenServiceAccount(e.statusCode.value())
        }
        val valor = resposta?.accessToken?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Keycloak nao devolveu access_token para a integracao Dealer")
        token = valor to clock.instant().plusSeconds((resposta.expiresIn ?: 60L).coerceAtLeast(31L) - 30L)
        return valor
    }

    @Synchronized
    private fun invalidar() {
        token = null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal data class TokenResponse(
        @JsonProperty("access_token") val accessToken: String? = null,
        @JsonProperty("expires_in") val expiresIn: Long? = null,
    )

    companion object {
        const val CAMINHO_LISTAGEM = "/api/contas-pagar/notas-entrada/listagem"
        const val CAMINHO_EMPRESAS = "/api/contas-pagar/empresas"
        const val CAMINHO_EMPRESAS_USUARIO = "/usuario/empresas"
    }
}

/** Filtro por periodo (ate 31 dias) ou, com [ids], busca exata das notas. */
data class ListagemNotasDealerRequest(
    val empresaCodigo: Int,
    val dataInicial: String? = null,
    val dataFinal: String? = null,
    val cpfCnpjFornecedor: String? = null,
    val numeroNota: String? = null,
    val ids: List<String>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ListagemNotasDealerResponse(val notas: List<NotaEntradaDealerlenium> = emptyList())

@JsonIgnoreProperties(ignoreUnknown = true)
data class NotaEntradaDealerlenium(
    val id: String,
    val numero: Long? = null,
    val dataEmissao: String? = null,
    val dataLancamento: String? = null,
    val nomeFornecedor: String? = null,
    val cpfCnpjFornecedor: String? = null,
    val valorTotal: BigDecimal? = null,
    val parcelas: List<ParcelaDealerlenium> = emptyList(),
    val problema: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ParcelaDealerlenium(val tituloCodigo: Int? = null, val vencimento: String? = null, val valor: BigDecimal? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EmpresaDealerlenium(val codigoDealer: Int, val cnpj: String, val nome: String? = null, val id: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EmpresaUsuarioDealer(val id: String? = null, val label: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ErroDealerlenium(val message: String? = null)
