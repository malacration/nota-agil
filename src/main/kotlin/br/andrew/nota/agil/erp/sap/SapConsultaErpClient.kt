package br.andrew.nota.agil.erp.sap

import br.andrew.nota.agil.erp.ConsultaErpClient
import br.andrew.nota.agil.model.erp.Erp
import br.andrew.nota.agil.model.erp.RegistrarConsultaErpRequest
import br.andrew.nota.agil.model.erp.ResultadoConsultaErp
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

/**
 * Consulta de pagamento de parcela de nota de ENTRADA (contas a pagar) no SAP B1.
 *
 * Caminho: Nota Agil -> sap-rovema `POST /contas-pagar/pagamento-parcela/consulta` -> Service Layer
 * (so a entidade BusinessPlaces, filiais) + sap-odbc (fornecedor, notas de entrada por chave de
 * acesso/numero, parcelas e pagamentos; SQL somente leitura com bind). Somente leitura; a regra de
 * quitacao fica no sap-rovema, que conhece o
 * SAP. Aqui se traduz o resultado e, por defesa em profundidade, um PAGO sem evidencia minima
 * (parcela com valor positivo e pagamentos que somam valor positivo) vira falha tecnica.
 *
 * Somente `PAGO` e sucesso (acao de sucesso no SE). Os demais codigos sao falha; quais aguardam
 * reconsulta (EM_ABERTO, PAGO_PARCIAL, NOTA_NAO_ENCONTRADA...) ou nova tentativa (falha tecnica)
 * antes de ir ao fluxo manual e decidido no ConsultaErpService/PoliticaReconsultaErp, nao aqui.
 * O valor comparado e o LIQUIDO da parcela (o SE manda o liquido). Falha tecnica vira `ERRO_CONSULTA_ERP` com o mesmo conjunto
 * de chaves em `dados`, para que campos mapeados em `integracao.erp.campos-retorno` sempre existam.
 *
 * Chave de acesso, serie e tipo: os do pedido do SE; o que faltar vem do [DadosFiscaisNotaResolver]
 * (task de criacao do processo / Qive). Sem chave e sem tipo depois disso, o numero sozinho nao e
 * seguro (colisao CTe x NFS-e do mesmo fornecedor): `DADOS_NOTA_INSUFICIENTES`, sem consultar o SAP.
 * De onde vieram esses dados (pedido, task do processo, task achada pelo numero, Qive) vai em
 * `dados.origemDadosFiscais` de todo resultado depois da resolucao, para a conferencia manual.
 *
 * Nao loga CNPJ, chave, numero de nota, valores nem token: so codigo, origem e status HTTP.
 */
@Component
class SapConsultaErpClient internal constructor(
    private val properties: SapContasPagarProperties,
    private val tokens: SapRovemaTokenProvider?,
    private val restClient: RestClient?,
    private val dadosFiscais: DadosFiscaisNotaResolver? = null,
) : ConsultaErpClient {

    @Autowired
    constructor(properties: SapContasPagarProperties, tokens: SapRovemaTokenProvider, dadosFiscais: DadosFiscaisNotaResolver?) :
        this(properties, tokens, criarRestClient(properties), dadosFiscais)

    /** Sem configuracao: fora do catalogo e sem chamada externa. */
    constructor() : this(SapContasPagarProperties(), null, null)

    private val log = LoggerFactory.getLogger(javaClass)

    override val erp = Erp.SAP
    override val consultaPagamentoImplementada: Boolean
        get() = (properties.configurada || tokens?.semToken == true) && tokens != null && restClient != null

    override fun consultar(requisicao: RegistrarConsultaErpRequest): ResultadoConsultaErp {
        if (!consultaPagamentoImplementada) {
            return ResultadoConsultaErp(false, "ERP_NAO_CONFIGURADO",
                "Consulta de pagamento no SAP sem configuracao (integracao.erp.sap.*)", chavesVazias())
        }
        val fiscais = dadosFiscais?.completar(requisicao) ?: DadosFiscaisNotaResolver.soDaRequisicao(requisicao)
        if (fiscais.completado) log.info("Dados fiscais da nota completados (origem {})", fiscais.origem)
        return consultarSap(requisicao, fiscais).let { it.copy(dados = it.dados + (ORIGEM_DADOS_FISCAIS to fiscais.origem)) }
    }

    private fun consultarSap(requisicao: RegistrarConsultaErpRequest, fiscais: DadosFiscaisNotaResolver.DadosFiscais): ResultadoConsultaErp {
        if (fiscais.chaveAcesso == null && fiscais.tipo == null) {
            return ResultadoConsultaErp(false, "DADOS_NOTA_INSUFICIENTES",
                "Sem chave de acesso nem tipo do documento (no pedido do SoftExpert e na task de criacao): " +
                    "o numero sozinho pode casar documento de outro tipo no SAP; confira manualmente", chavesVazias())
        }
        val corpo = ConsultaPagamentoParcelaSapRequest(
            cnpjFilial = requisicao.cpfCnpjTomador,
            cnpjFornecedor = requisicao.cnpjPrestador,
            numeroNota = requisicao.numDocFiscal,
            serieNota = fiscais.serie,
            numeroParcela = requisicao.numeroParcela,
            valorParcela = requisicao.valorParcela,
            chaveAcesso = fiscais.chaveAcesso,
            tipoNota = fiscais.tipo?.name?.uppercase(),
        )
        val resposta = try {
            enviar(corpo)
        } catch (e: HttpClientErrorException.Unauthorized) {
            tokens!!.invalidar()
            try { enviar(corpo) } catch (e2: Exception) { return falhaTecnica(e2) }
        } catch (e: Exception) {
            return falhaTecnica(e)
        }
        return traduzir(resposta)
    }

    private fun enviar(corpo: ConsultaPagamentoParcelaSapRequest): ConsultaPagamentoParcelaSapResponse =
        restClient!!.post()
            .uri(properties.url.trimEnd('/') + CAMINHO)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            // Sem token so no teste local/homolog com seguranca desativada (SapRovemaTokenProvider.semToken).
            .apply { if (!tokens!!.semToken) header("Authorization", "Bearer ${tokens.token()}") }
            .body(corpo)
            .retrieve()
            .body(ConsultaPagamentoParcelaSapResponse::class.java)
            ?: throw RestClientException("Resposta vazia do sap-rovema")

    private fun falhaTecnica(e: Exception): ResultadoConsultaErp {
        val status = (e as? org.springframework.web.client.HttpStatusCodeException)?.statusCode?.value()
        log.warn("Falha tecnica na consulta de pagamento SAP: {} status={}", e.javaClass.simpleName, status)
        val detalhe = when {
            e is br.andrew.nota.agil.erp.FalhaTokenServiceAccount -> br.andrew.nota.agil.erp.FalhaTokenServiceAccount.MENSAGEM
            status == HttpStatus.UNAUTHORIZED.value() || status == HttpStatus.FORBIDDEN.value() ->
                "integracao nao autorizada (token Keycloak ou perfil no sap-rovema)"
            status != null -> "sap-rovema respondeu HTTP $status"
            else -> "sem resposta valida do sap-rovema"
        }
        return ResultadoConsultaErp(false, "ERRO_CONSULTA_ERP", "Falha tecnica na consulta ao SAP: $detalhe", chavesVazias())
    }

    internal fun traduzir(resposta: ConsultaPagamentoParcelaSapResponse): ResultadoConsultaErp {
        val codigo = resposta.codigo?.takeIf { it in CODIGOS_SAP }
            ?: return ResultadoConsultaErp(false, "ERRO_CONSULTA_ERP",
                "Falha tecnica na consulta ao SAP: codigo de resultado desconhecido", chavesVazias())
        if (codigo == "PAGO" && !pagoComEvidencia(resposta)) {
            log.warn("sap-rovema respondeu PAGO sem parcela/pagamento positivos; tratado como falha tecnica")
            return ResultadoConsultaErp(false, "ERRO_CONSULTA_ERP",
                "Falha tecnica na consulta ao SAP: resultado PAGO sem evidencia de pagamento", chavesVazias())
        }
        val nota = resposta.nota
        val parcela = resposta.parcela
        val ultimo = resposta.pagamentos.lastOrNull()
        val dados = linkedMapOf(
            "notaDocEntry" to nota?.docEntry?.toString(),
            "notaDocNum" to nota?.docNum?.toString(),
            "notaSerie" to nota?.serie,
            "notaData" to nota?.dataDocumento,
            "notaMoeda" to nota?.moeda,
            "notaCriterio" to nota?.criterio,
            "parcelaNumero" to parcela?.numero?.toString(),
            "parcelaVencimento" to parcela?.vencimento,
            "parcelaValor" to parcela?.valor.dinheiro(),
            "parcelaRetencao" to parcela?.retencaoPagamento.dinheiro(),
            "parcelaValorLiquido" to parcela?.valorLiquido.dinheiro(),
            "parcelaValorBaixado" to parcela?.valorBaixado.dinheiro(),
            "parcelaSaldo" to parcela?.saldo.dinheiro(),
            "parcelaStatus" to parcela?.status,
            "pagamentosValor" to parcela?.valorPagamentos.dinheiro(),
            "pagamentosQuantidade" to (if (parcela == null) null else resposta.pagamentos.size.toString()),
            "pagamentoDocEntry" to ultimo?.docEntry?.toString(),
            "pagamentoDocNum" to ultimo?.docNum?.toString(),
            "pagamentoData" to ultimo?.data,
            "pagamentos" to resposta.pagamentos.joinToString("; ") {
                "DocNum ${it.docNum ?: "-"} (DocEntry ${it.docEntry ?: "-"}) em ${it.data ?: "-"}: " +
                    "pago ${(it.valorPago ?: it.valorAplicado).dinheiro()}" +
                    (it.valorRetido?.takeIf { r -> r.signum() != 0 }?.let { r -> ", retido ${r.dinheiro()}" } ?: "")
            },
            // Preenchida em consultar(), que conhece a resolucao dos dados fiscais.
            ORIGEM_DADOS_FISCAIS to null,
        ).mapValues { it.value.orEmpty() }
        return ResultadoConsultaErp(
            sucesso = codigo == "PAGO",
            codigo = codigo,
            mensagem = resposta.mensagem?.take(500) ?: codigo,
            dados = dados,
        )
    }

    companion object {
        const val CAMINHO = "/contas-pagar/pagamento-parcela/consulta"

        /** Codigos devolvidos pelo sap-rovema; qualquer outro e tratado como falha tecnica. */
        val CODIGOS_SAP = setOf(
            "PAGO", "PAGO_PARCIAL", "EM_ABERTO", "BAIXADA_SEM_PAGAMENTO", "NOTA_NAO_ENCONTRADA",
            "FORNECEDOR_NAO_ENCONTRADO", "FILIAL_NAO_ENCONTRADA", "PARCELA_NAO_ENCONTRADA",
            "VALOR_DIVERGENTE", "AMBIGUO", "MOEDA_NAO_SUPORTADA", "RETENCAO_NAO_SUPORTADA", "DADOS_NOTA_DIVERGENTES",
        )

        /** Chaves de `dados` de todo resultado SAP, na ordem do comentario no SE. */
        val CHAVES_DADOS = listOf(
            "notaDocEntry", "notaDocNum", "notaSerie", "notaData", "notaMoeda", "notaCriterio", "parcelaNumero", "parcelaVencimento",
            "parcelaValor", "parcelaRetencao", "parcelaValorLiquido", "parcelaValorBaixado", "parcelaSaldo", "parcelaStatus", "pagamentosValor",
            "pagamentosQuantidade", "pagamentoDocEntry", "pagamentoDocNum", "pagamentoData", "pagamentos", ORIGEM_DADOS_FISCAIS,
        )

        /**
         * Quem forneceu chave/serie/tipo enviados ao sap-rovema (ver [DadosFiscaisNotaResolver.DadosFiscais.origem]),
         * ex. `REQUISICAO+TASK_NUMERO`: chave derivada, nao mandada pelo SE.
         */
        const val ORIGEM_DADOS_FISCAIS = "origemDadosFiscais"

        private fun chavesVazias() = CHAVES_DADOS.associateWith { "" }

        /**
         * PAGO precisa trazer parcela com valor > 0 e pagamentos (> 0) que somem valor > 0 e, quando
         * o sap-rovema informa o liquido, que cubram o liquido menos a tolerancia maxima (R$ 0,10).
         */
        internal fun pagoComEvidencia(resposta: ConsultaPagamentoParcelaSapResponse): Boolean {
            val parcela = resposta.parcela ?: return false
            val valor = parcela.valor ?: return false
            val pago = parcela.valorPagamentos ?: return false
            val pagos = resposta.pagamentos.mapNotNull { it.valorPago ?: it.valorAplicado }
            val somaPagos = pagos.fold(BigDecimal.ZERO, BigDecimal::add)
            val liquido = parcela.valorLiquido
            return valor > BigDecimal.ZERO && pago > BigDecimal.ZERO && pagos.isNotEmpty() && somaPagos > BigDecimal.ZERO &&
                (liquido == null || (liquido > BigDecimal.ZERO && pago >= liquido - TOLERANCIA_MAXIMA))
        }

        /** Mesmo teto de `contas-pagar.tolerancia-valor` no sap-rovema. */
        private val TOLERANCIA_MAXIMA = BigDecimal("0.10")

        private fun BigDecimal?.dinheiro(): String? = this?.setScale(2, RoundingMode.HALF_UP)?.toPlainString()

        fun criarRestClient(properties: SapContasPagarProperties): RestClient = RestClient.builder()
            .requestFactory(SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds.toLong()))
                setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds.toLong()))
            })
            .build()
    }
}

/** Espelho do contrato do sap-rovema (ConsultaPagamentoParcelaRequest). */
data class ConsultaPagamentoParcelaSapRequest(
    val cnpjFilial: String,
    val cnpjFornecedor: String,
    val numeroNota: String,
    val serieNota: String?,
    val numeroParcela: String?,
    val valorParcela: BigDecimal,
    val chaveAcesso: String? = null,
    /** NFE, CTE ou NFSE (filtro por modelo no sap-rovema). */
    val tipoNota: String? = null,
)

/** Espelho do contrato do sap-rovema (ConsultaPagamentoParcelaResponse). */
data class ConsultaPagamentoParcelaSapResponse(
    val codigo: String? = null,
    val mensagem: String? = null,
    val nota: Nota? = null,
    val parcela: Parcela? = null,
    val pagamentos: List<Pagamento> = emptyList(),
) {
    data class Nota(
        val docEntry: Int? = null, val docNum: Int? = null, val numero: Int? = null, val serie: String? = null,
        val dataDocumento: String? = null, val cardCode: String? = null, val filial: Int? = null,
        val moeda: String? = null, val modelo: String? = null,
        /** CHAVE_ACESSO, NUMERO ou NUMERO_TRUNCADO. */
        val criterio: String? = null,
    )

    data class Parcela(
        val numero: Int? = null, val vencimento: String? = null, val valor: BigDecimal? = null,
        val valorBaixado: BigDecimal? = null, val saldo: BigDecimal? = null, val status: String? = null,
        val valorPagamentos: BigDecimal? = null,
        val retencaoPagamento: BigDecimal? = null, val valorLiquido: BigDecimal? = null,
    )

    data class Pagamento(
        val docEntry: Int? = null, val docNum: Int? = null, val data: String? = null,
        val valorAplicado: BigDecimal? = null, val valorRetido: BigDecimal? = null,
        val valorPago: BigDecimal? = null,
    )
}
