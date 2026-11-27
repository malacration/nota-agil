package br.andrew.nota.agil.erp.sap

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

/**
 * Notas fiscais de entrada do SAP (OPCH) como fonte alternativa ao Qive. Somente leitura.
 *
 * Caminho: Nota Agil -> sap-rovema `POST /contas-pagar/notas-entrada/listagem` e
 * `POST /contas-pagar/notas-entrada/{docEntry}/anexo-pdf` (mesma service account e mesmo perfil
 * `integracao_contas_pagar` da consulta de pagamento; ver [SapContasPagarProperties]).
 *
 * Nao loga CNPJ, chave, numero de nota nem token.
 */
@Component
class SapNotasEntradaClient internal constructor(
    private val properties: SapContasPagarProperties,
    private val tokens: SapRovemaTokenProvider?,
    private val restClient: RestClient?,
) {
    @Autowired
    constructor(properties: SapContasPagarProperties, tokens: SapRovemaTokenProvider) :
        this(properties, tokens, SapRovemaTokenProvider.criarRestClient(properties))

    private val log = LoggerFactory.getLogger(javaClass)

    val disponivel: Boolean
        get() = (properties.configurada || tokens?.semToken == true) && tokens != null && restClient != null

    /**
     * Listagem por periodo (ate 31 dias) ou pelos [ListagemNotasEntradaSapRequest.docEntries].
     * Erro de filtro (400/422) vira 400 com a mensagem do sap-rovema; filial inexistente, 404;
     * demais falhas, 502.
     */
    fun listar(requisicao: ListagemNotasEntradaSapRequest): List<NotaEntradaSap> {
        exigirDisponivel()
        return try {
            comNovoTokenEm401 {
                restClient!!.post()
                    .uri(properties.url.trimEnd('/') + CAMINHO_LISTAGEM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .autorizado()
                    .body(requisicao)
                    .retrieve()
                    .body(ListagemNotasEntradaSapResponse::class.java)
                    ?: throw RestClientException("Resposta vazia do sap-rovema")
            }.notas
        } catch (e: HttpStatusCodeException) {
            val status = e.statusCode.value()
            val mensagem = runCatching { e.getResponseBodyAs(ErroSapRovema::class.java)?.mensagem }.getOrNull()
            log.warn("Listagem de notas de entrada no SAP falhou: status={}", status)
            throw when (status) {
                400, 422 -> ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem ?: "Filtro invalido")
                404 -> ResponseStatusException(HttpStatus.NOT_FOUND, mensagem ?: "Filial nao encontrada no SAP")
                401, 403 -> ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "sap-rovema recusou o token (HTTP $status): a service account precisa da role integracao_contas_pagar")
                else -> ResponseStatusException(HttpStatus.BAD_GATEWAY, "Falha na consulta ao SAP (HTTP $status)")
            }
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: br.andrew.nota.agil.erp.FalhaTokenServiceAccount) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, br.andrew.nota.agil.erp.FalhaTokenServiceAccount.MENSAGEM)
        } catch (e: Exception) {
            log.warn("Listagem de notas de entrada no SAP falhou: {}", e.javaClass.simpleName)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Sem resposta valida do sap-rovema")
        }
    }

    /** Filiais do SAP (BusinessPlaces) com CNPJ: `GET /contas-pagar/filiais`. */
    fun filiais(): List<FilialSapRovema> {
        exigirDisponivel()
        return try {
            comNovoTokenEm401 {
                restClient!!.get()
                    .uri(properties.url.trimEnd('/') + CAMINHO_FILIAIS)
                    .accept(MediaType.APPLICATION_JSON)
                    .let { if (tokens!!.semToken) it else it.header("Authorization", "Bearer ${tokens.token()}") }
                    .retrieve()
                    .body(Array<FilialSapRovema>::class.java)
                    ?.toList() ?: emptyList()
            }
        } catch (e: HttpStatusCodeException) {
            log.warn("Listagem de filiais no SAP falhou: status={}", e.statusCode.value())
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, when (e.statusCode.value()) {
                401, 403 -> "sap-rovema recusou o token (HTTP ${e.statusCode.value()}): a service account precisa da role integracao_contas_pagar"
                else -> "Falha na consulta de filiais ao SAP (HTTP ${e.statusCode.value()})"
            })
        } catch (e: ResponseStatusException) {
            throw e
        } catch (e: br.andrew.nota.agil.erp.FalhaTokenServiceAccount) {
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, br.andrew.nota.agil.erp.FalhaTokenServiceAccount.MENSAGEM)
        } catch (e: Exception) {
            log.warn("Listagem de filiais no SAP falhou: {}", e.javaClass.simpleName)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Sem resposta valida do sap-rovema")
        }
    }

    /**
     * BPLIds das filiais do USUARIO, como o sap-rovema decide (`GET /branch` com o token do proprio
     * usuario: vendedor, colaborador ou super vendedor). Sem service account: e o SAP que identifica
     * o usuario pelo token. 401/403 viram mensagem para a tela (sem detalhes do token).
     */
    fun filiaisDoUsuario(tokenUsuario: String): List<Int> {
        if (properties.url.isBlank()) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Integracao com o SAP nao configurada (INTEGRACAO_ERP_SAP_URL)")
        return try {
            restClient!!.get()
                .uri(properties.url.trimEnd('/') + CAMINHO_BRANCH)
                .accept(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer $tokenUsuario")
                .retrieve()
                .body(Array<BranchUsuarioSap>::class.java)
                ?.mapNotNull { it.bplId }?.distinct() ?: emptyList()
        } catch (e: HttpStatusCodeException) {
            log.warn("Filiais do usuario no SAP: sap-rovema respondeu status={}", e.statusCode.value())
            throw ResponseStatusException(HttpStatus.FORBIDDEN, when (e.statusCode.value()) {
                401, 403 -> "O SAP recusou seu acesso: confira no SSO o codigo de colaborador (sap_code) e um perfil do sap-rovema com acesso a /branch"
                else -> "Falha ao consultar suas filiais no SAP (HTTP ${e.statusCode.value()})"
            })
        } catch (e: Exception) {
            log.warn("Filiais do usuario no SAP: {}", e.javaClass.simpleName)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Sem resposta valida do sap-rovema ao consultar suas filiais")
        }
    }

    /** PDF anexado a nota no SAP; null quando a nota nao tem PDF (404). Falha tecnica lanca excecao. */
    fun anexoPdf(docEntry: Int, cnpjFilial: String): ByteArray? {
        exigirDisponivel()
        return try {
            comNovoTokenEm401 {
                restClient!!.post()
                    .uri(properties.url.trimEnd('/') + CAMINHO_ANEXO, docEntry)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_PDF, MediaType.APPLICATION_JSON)
                    .autorizado()
                    .body(mapOf("cnpjFilial" to cnpjFilial))
                    .retrieve()
                    .body(ByteArray::class.java)
            }?.takeIf { it.isNotEmpty() }
        } catch (e: HttpClientErrorException.NotFound) {
            null
        }
    }

    private fun exigirDisponivel() {
        if (!disponivel) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Integracao com o SAP nao configurada (INTEGRACAO_ERP_SAP_*)")
    }

    private fun RestClient.RequestBodySpec.autorizado(): RestClient.RequestBodySpec =
        // Sem token so no teste local/homolog com seguranca desativada (SapRovemaTokenProvider.semToken).
        apply { if (!tokens!!.semToken) header("Authorization", "Bearer ${tokens.token()}") }

    private fun <T> comNovoTokenEm401(chamada: () -> T): T = try {
        chamada()
    } catch (e: HttpClientErrorException.Unauthorized) {
        tokens!!.invalidar()
        chamada()
    }

    companion object {
        const val CAMINHO_LISTAGEM = "/contas-pagar/notas-entrada/listagem"
        const val CAMINHO_FILIAIS = "/contas-pagar/filiais"
        const val CAMINHO_BRANCH = "/branch"
        const val CAMINHO_ANEXO = "/contas-pagar/notas-entrada/{docEntry}/anexo-pdf"
    }
}

data class ListagemNotasEntradaSapRequest(
    val cnpjFilial: String,
    val dataInicial: String? = null,
    val dataFinal: String? = null,
    val cpfCnpjFornecedor: String? = null,
    val numeroNota: String? = null,
    val docEntries: List<Int>? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ListagemNotasEntradaSapResponse(val notas: List<NotaEntradaSap> = emptyList())

/** Nota de entrada como o sap-rovema devolve (`NotaEntradaListagem`). */
@JsonIgnoreProperties(ignoreUnknown = true)
data class NotaEntradaSap(
    val docEntry: Int,
    val docNum: Int? = null,
    val numero: Long? = null,
    val serie: String? = null,
    val modelo: String? = null,
    /** NFE, CTE, NFSE ou null (outro modelo). */
    val tipo: String? = null,
    val chaveAcesso: String? = null,
    val dataEmissao: String? = null,
    val dataLancamento: String? = null,
    val filial: Int? = null,
    val nomeFilial: String? = null,
    val cnpjFilial: String? = null,
    val cardCode: String? = null,
    val nomeFornecedor: String? = null,
    val cpfCnpjFornecedor: String? = null,
    val moeda: String? = null,
    val valorTotal: BigDecimal? = null,
    val retencaoFatura: BigDecimal? = null,
    val retencaoPagamento: BigDecimal? = null,
    val valorBruto: BigDecimal? = null,
    val valorLiquido: BigDecimal? = null,
    val anexosPdf: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BranchUsuarioSap(@com.fasterxml.jackson.annotation.JsonProperty("BPLID") @com.fasterxml.jackson.annotation.JsonAlias("BPLId") val bplId: Int? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class FilialSapRovema(val bplId: Int, val nome: String? = null, val cnpj: String? = null, val ativa: Boolean = true)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class ErroSapRovema(val erro: String? = null, val mensagem: String? = null)
