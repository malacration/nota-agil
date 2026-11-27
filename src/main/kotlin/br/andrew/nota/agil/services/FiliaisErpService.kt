package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.CatalogoConsultaErp
import br.andrew.nota.agil.erp.notas.FilialErp
import br.andrew.nota.agil.erp.notas.NotasEntradaErpClient
import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.CompanyChanged
import br.andrew.nota.agil.model.erp.Erp
import br.andrew.nota.agil.model.erp.normalizaCnpj
import br.andrew.nota.agil.qive.interfaces.QiveApiClient
import br.andrew.nota.agil.repository.CompanyRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * Cadastro de empresas a partir das filiais do ERP (SAP: BusinessPlaces; Dealer: empresas do
 * dealerlenium com codigo). A empresa nasce vinculada ao Qive so se o CNPJ estiver na Qive; sem
 * vinculo, os jobs de busca de notas no Qive nao sao criados para ela (DynamicTasksConfig).
 * O ERP de pagamento vem preenchido com o ERP de origem quando a consulta de pagamento dele esta
 * implementada (CatalogoConsultaErp). Empresa ja cadastrada so ganha o que faltava (codigo do
 * Dealer, ERP de pagamento vazio); o que ja estava preenchido nao e alterado.
 */
@Service
class FiliaisErpService(
    clients: List<NotasEntradaErpClient>,
    private val repository: CompanyRepository,
    private val qiveApi: QiveApiClient,
    private val events: ApplicationEventPublisher,
    private val catalogo: CatalogoConsultaErp,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val porErp = clients.associateBy { it.erp }

    fun listar(erp: Erp): ListagemFiliaisErp {
        val filiais = consolidar(client(erp).filiais())
        val cadastradas = repository.findAll().associateBy { it.cnpj }
        val qive = cnpjsQive()
        return ListagemFiliaisErp(erp, qive != null, filiais.map { f ->
            val empresa = cadastradas[f.cnpj]
            FilialErpItem(
                cnpj = f.cnpj, nome = f.nome, codigoDealer = f.codigoDealer, ativa = f.ativa,
                cadastrada = empresa != null,
                vinculadoQive = empresa?.vinculadoQive,
                disponivelNoQive = qive?.let { f.cnpj in it },
                problema = f.problema,
            )
        })
    }

    fun importar(erp: Erp, cnpjs: List<String>): List<ResultadoFilialErp> {
        val pedidos = cnpjs.map(::normalizaCnpj).filter { it.length == 14 }.distinct()
        if (pedidos.isEmpty() || pedidos.size > MAX_IMPORTACAO)
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe de 1 a $MAX_IMPORTACAO CNPJs de filiais")
        // Rele as filiais no ERP: so cadastra o que o ERP devolve, com o nome e o codigo dele.
        val filiais = consolidar(client(erp).filiais()).associateBy { it.cnpj }
        val qive = cnpjsQive() ?: emptySet()
        return pedidos.map { cnpj ->
            val filial = filiais[cnpj] ?: return@map ResultadoFilialErp(cnpj, StatusFilialErp.NAO_ENCONTRADA, "CNPJ nao e filial do ERP")
            if (filial.problema != null) return@map ResultadoFilialErp(cnpj, StatusFilialErp.ERRO, filial.problema)
            synchronized(this) { cadastrar(erp, filial, cnpj in qive) }
        }
    }

    private fun cadastrar(erp: Erp, filial: FilialConsolidada, noQive: Boolean): ResultadoFilialErp {
        val erpPagamento = erp.takeIf { e -> catalogo.disponiveis().any { it.erp == e } }
        val codigo = filial.codigoDealer
        val existente = repository.findById(filial.cnpj).orElse(null)
        if (codigo != null && repository.findAll().any { it.cnpj != filial.cnpj && it.codigoDealer == codigo })
            return ResultadoFilialErp(filial.cnpj, StatusFilialErp.ERRO, "Codigo do Dealer $codigo ja usado por outra empresa")
        if (existente != null) {
            val preenchido = existente.copy(
                codigoDealer = existente.codigoDealer ?: codigo,
                erpPagamento = existente.erpPagamento ?: erpPagamento,
            )
            if (preenchido == existente) return ResultadoFilialErp(filial.cnpj, StatusFilialErp.JA_CADASTRADA, null)
            salvar(preenchido)
            return ResultadoFilialErp(filial.cnpj, StatusFilialErp.ATUALIZADA, listOfNotNull(
                "codigo do Dealer".takeIf { existente.codigoDealer == null && codigo != null },
                "ERP de pagamento".takeIf { existente.erpPagamento == null && erpPagamento != null },
            ).joinToString(" e ", prefix = "Preenchido: "))
        }
        salvar(Company(filial.cnpj, filial.nome.take(200), erpPagamento = erpPagamento, vinculadoQive = noQive, codigoDealer = codigo))
        return ResultadoFilialErp(filial.cnpj, StatusFilialErp.CRIADA, listOfNotNull(
            if (noQive) "Vinculada ao Qive" else "Sem vinculo com o Qive: sem jobs de busca de notas no Qive",
            if (erpPagamento == null) "ERP de pagamento vazio: consulta de pagamento no $erp ainda nao implementada" else null,
        ).joinToString("; "))
    }

    private fun salvar(empresa: Company) {
        repository.save(empresa)
        events.publishEvent(CompanyChanged(empresa))
    }

    private fun client(erp: Erp): NotasEntradaErpClient {
        val client = porErp[erp] ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "ERP sem cadastro de filiais")
        if (!client.situacao().configurado) throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
            "Integracao com o ${client.nome} nao configurada")
        return client
    }

    /** CNPJs da Qive; null quando a Qive nao respondeu (a tela avisa e a empresa nasce sem vinculo). */
    private fun cnpjsQive(): Set<String>? = try {
        qiveApi.company.empresas().data.map(::normalizaCnpj).filter { it.length == 14 }.toSet()
    } catch (e: Exception) {
        log.warn("Consulta de empresas na Qive falhou: {}", e.javaClass.simpleName)
        null
    }

    internal data class FilialConsolidada(val cnpj: String, val nome: String, val codigoDealer: Int?, val ativa: Boolean, val problema: String?)

    companion object {
        const val MAX_IMPORTACAO = 200

        /**
         * Uma linha por CNPJ (a empresa do Nota Agil e o CNPJ). Varias filiais SAP com o mesmo CNPJ
         * viram uma; no Dealer, CNPJ com mais de um codigo e ambiguo e nao pode ser cadastrado.
         */
        internal fun consolidar(filiais: List<FilialErp>): List<FilialConsolidada> =
            filiais.groupBy { it.cnpj }.map { (cnpj, grupo) ->
                val codigos = grupo.mapNotNull { it.codigoDealer }.distinct()
                FilialConsolidada(
                    cnpj = cnpj,
                    nome = grupo.first().nome,
                    codigoDealer = codigos.singleOrNull(),
                    ativa = grupo.any { it.ativa },
                    problema = if (codigos.size > 1) "CNPJ com mais de um codigo no Dealer (${codigos.sorted().joinToString()})" else null,
                )
            }.sortedBy { it.nome }
    }
}

data class ListagemFiliaisErp(val erp: Erp, val qiveRespondeu: Boolean, val filiais: List<FilialErpItem>)

data class FilialErpItem(
    val cnpj: String,
    val nome: String,
    val codigoDealer: Int?,
    val ativa: Boolean,
    val cadastrada: Boolean,
    /** Vinculo da empresa ja cadastrada; null quando nao cadastrada. */
    val vinculadoQive: Boolean?,
    /** CNPJ existe na Qive; null quando a Qive nao respondeu. */
    val disponivelNoQive: Boolean?,
    val problema: String?,
)

data class ImportarFiliaisErpRequest(val erp: Erp, val cnpjs: List<String> = emptyList())

data class ResultadoFilialErp(val cnpj: String, val status: StatusFilialErp, val mensagem: String?)

enum class StatusFilialErp { CRIADA, ATUALIZADA, JA_CADASTRADA, NAO_ENCONTRADA, ERRO }
