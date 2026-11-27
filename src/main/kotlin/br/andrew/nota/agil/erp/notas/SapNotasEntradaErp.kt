package br.andrew.nota.agil.erp.notas

import br.andrew.nota.agil.erp.sap.ListagemNotasEntradaSapRequest
import br.andrew.nota.agil.erp.sap.NotaEntradaSap
import br.andrew.nota.agil.erp.sap.SapContasPagarProperties
import br.andrew.nota.agil.erp.sap.SapNotasEntradaClient
import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.TipoDuplicata
import br.andrew.nota.agil.model.erp.Erp
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/** Notas de entrada do SAP (OPCH) via sap-rovema. Conexao: `INTEGRACAO_ERP_SAP_*`. */
@Component
class SapNotasEntradaErp(
    private val client: SapNotasEntradaClient,
    private val flags: NotasErpProperties,
) : NotasEntradaErpClient {
    override val erp = Erp.SAP
    override val nome = "SAP"

    override fun situacao() = SituacaoNotasErp(flags.sap.enabled, client.disponivel)

    override fun listar(empresa: Company, filtro: FiltroNotasErp): List<NotaEntradaErp> =
        client.listar(ListagemNotasEntradaSapRequest(empresa.cnpj, filtro.dataInicial, filtro.dataFinal,
            filtro.fornecedor, filtro.numero)).map(::converter)

    override fun buscar(empresa: Company, ids: List<String>): List<NotaEntradaErp> {
        val docEntries = ids.map { it.toIntOrNull()?.takeIf { n -> n > 0 }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Id de nota SAP invalido") }
        return client.listar(ListagemNotasEntradaSapRequest(empresa.cnpj, docEntries = docEntries)).map(::converter)
    }

    override fun filiais(): List<FilialErp> = client.filiais()
        .mapNotNull { f -> f.cnpj?.filter(Char::isDigit)?.takeIf { it.length == 14 }?.let { FilialErp(it, f.nome?.trim().orEmpty().ifEmpty { "Filial ${f.bplId}" }, ativa = f.ativa) } }

    companion object {
        fun converter(n: NotaEntradaSap) = NotaEntradaErp(
            id = n.docEntry.toString(),
            erp = Erp.SAP,
            numero = n.numero,
            serie = n.serie,
            modelo = n.modelo,
            tipo = when (n.tipo?.uppercase()) {
                "NFE" -> TipoDuplicata.Nfe
                "CTE" -> TipoDuplicata.Cte
                "NFSE" -> TipoDuplicata.Nfse
                else -> TipoDuplicata.NaoDetectado
            },
            chaveAcesso = n.chaveAcesso,
            dataEmissao = n.dataEmissao,
            dataLancamento = n.dataLancamento,
            nomeFornecedor = n.nomeFornecedor ?: n.cardCode,
            cpfCnpjFornecedor = n.cpfCnpjFornecedor,
            valorTotal = n.valorTotal,
            valorBruto = n.valorBruto,
            valorLiquido = n.valorLiquido,
            anexosPdf = n.anexosPdf,
        )
    }
}
