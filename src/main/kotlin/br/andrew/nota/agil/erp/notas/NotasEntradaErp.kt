package br.andrew.nota.agil.erp.notas

import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.model.erp.Erp
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Fonte de notas fiscais de entrada (contas a pagar) de um ERP, alternativa ao Qive. Sob demanda:
 * so lista e busca quando o usuario pede. Cada ERP tem a sua implementacao (SAP, Dealer).
 */
interface NotasEntradaErpClient {
    val erp: Erp
    val nome: String

    /** Desligada por `notas-erp.<erp>.enabled=false`; nunca disponivel sem as variaveis de conexao. */
    fun situacao(): SituacaoNotasErp

    /** Empresa pode listar notas neste ERP? Null = pode; senao, o motivo (ex.: sem codigo no Dealer). */
    fun motivoEmpresaIndisponivel(empresa: Company): String? = null

    /** Notas lancadas no periodo (ate 31 dias), com filtros opcionais de fornecedor e numero. */
    fun listar(empresa: Company, filtro: FiltroNotasErp): List<NotaEntradaErp>

    /** Notas pelos [ids] devolvidos na listagem (releitura na importacao). */
    fun buscar(empresa: Company, ids: List<String>): List<NotaEntradaErp>

    /** Filiais/empresas do ERP com CNPJ, para o cadastro de empresas. Exige so a conexao configurada. */
    fun filiais(): List<FilialErp>
}

/** Filial do ERP. [codigoDealer] so no Dealer (Titulo_EmpresaCod). */
data class FilialErp(val cnpj: String, val nome: String, val codigoDealer: Int? = null, val ativa: Boolean = true)

data class FiltroNotasErp(
    val dataInicial: String,
    val dataFinal: String,
    val fornecedor: String? = null,
    val numero: String? = null,
)

data class SituacaoNotasErp(val habilitado: Boolean, val configurado: Boolean) {
    val disponivel: Boolean get() = habilitado && configurado
    val motivo: String?
        get() = when {
            !habilitado -> "Desativado por variavel de ambiente"
            !configurado -> "Variaveis de conexao ausentes"
            else -> null
        }
}

/** Nota de entrada normalizada, igual para todos os ERPs. */
data class NotaEntradaErp(
    /** Id estavel no ERP (SAP: DocEntry; Dealer: chave da nota agrupada). */
    val id: String,
    val erp: Erp,
    val numero: Long?,
    val serie: String? = null,
    /** Modelo/tipo do documento como o ERP descreve (ex.: modelo SAP "39"). */
    val modelo: String? = null,
    val tipo: TipoDuplicata = TipoDuplicata.NaoDetectado,
    val chaveAcesso: String? = null,
    val dataEmissao: String? = null,
    val dataLancamento: String? = null,
    val nomeFornecedor: String? = null,
    /** So digitos: CNPJ (14) ou CPF (11). */
    val cpfCnpjFornecedor: String? = null,
    val valorTotal: BigDecimal? = null,
    val valorBruto: BigDecimal? = null,
    val valorLiquido: BigDecimal? = null,
    /** Quantas parcelas/titulos a nota tem no ERP (Dealer agrupa os titulos da mesma nota). */
    val parcelas: Int = 1,
    /** PDFs anexados a nota no ERP (so SAP; o Dealer nao expoe anexo). */
    val anexosPdf: Int = 0,
    /** Motivo do proprio ERP para a nota nao poder ser importada (ex.: titulo sem NF). */
    val problema: String? = null,
)

/**
 * Liga/desliga a importacao de notas por ERP (padrao: tudo ligado). So afeta a listagem/importacao;
 * a consulta de pagamento (retorno ao SE) segue a propria configuracao.
 * Variaveis: `NOTAS_ERP_SAP_ENABLED`, `NOTAS_ERP_DEALER_ENABLED`.
 */
@Component
@ConfigurationProperties(prefix = "notas-erp")
class NotasErpProperties {
    var sap: Flag = Flag()
    var dealer: Flag = Flag()

    class Flag {
        var enabled: Boolean = true
    }
}
