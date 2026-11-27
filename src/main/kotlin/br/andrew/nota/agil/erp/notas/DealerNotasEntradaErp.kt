package br.andrew.nota.agil.erp.notas

import br.andrew.nota.agil.erp.dealer.DealerleniumClient
import br.andrew.nota.agil.erp.dealer.ListagemNotasDealerRequest
import br.andrew.nota.agil.erp.dealer.NotaEntradaDealerlenium
import br.andrew.nota.agil.model.Company
import br.andrew.nota.agil.model.erp.Erp
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * Notas de contas a pagar do Dealer via dealerlenium (`POST /api/contas-pagar/notas-entrada/listagem`).
 * O dealerlenium consulta o WS do Dealer (metodo TITULO, movimento P) e agrupa as parcelas da mesma
 * nota; aqui so se converte para o modelo comum. O Dealer nao informa chave, tipo nem serie: o tipo
 * fica `NaoDetectado` e o PDF so vem do Qive.
 *
 * A empresa e filtrada pelo [Company.codigoDealer] (Titulo_EmpresaCod).
 */
@Component
class DealerNotasEntradaErp(
    private val client: DealerleniumClient,
    private val flags: NotasErpProperties,
) : NotasEntradaErpClient {
    override val erp = Erp.DEALER
    override val nome = "Dealer"

    override fun situacao() = SituacaoNotasErp(flags.dealer.enabled, client.configurado)

    override fun motivoEmpresaIndisponivel(empresa: Company): String? =
        if ((empresa.codigoDealer ?: 0) <= 0) "Empresa sem codigo no Dealer (cadastre em Empresas)" else null

    override fun listar(empresa: Company, filtro: FiltroNotasErp): List<NotaEntradaErp> =
        client.listar(ListagemNotasDealerRequest(codigo(empresa), filtro.dataInicial, filtro.dataFinal,
            filtro.fornecedor, filtro.numero)).map(::converter)

    override fun buscar(empresa: Company, ids: List<String>): List<NotaEntradaErp> {
        if (ids.any { !ID.matches(it) }) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Id de nota Dealer invalido")
        return client.listar(ListagemNotasDealerRequest(codigo(empresa), ids = ids.distinct())).map(::converter)
    }

    override fun filiais(): List<FilialErp> = client.empresas()
        .mapNotNull { e -> e.cnpj.filter(Char::isDigit).takeIf { it.length == 14 && e.codigoDealer > 0 }
            ?.let { FilialErp(it, e.nome?.trim().orEmpty().ifEmpty { "Dealer ${e.codigoDealer}" }, codigoDealer = e.codigoDealer) } }

    private fun codigo(empresa: Company): Int = empresa.codigoDealer?.takeIf { it > 0 }
        ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Empresa sem codigo no Dealer (cadastre em Empresas)")

    companion object {
        /** Ids que o dealerlenium devolve: nf{cod}, p{pessoa}-n{numero} ou t{titulo}. */
        private val ID = Regex("nf[1-9][0-9]{0,17}|p[1-9][0-9]{0,17}-n[1-9][0-9]{0,17}|t[1-9][0-9]{0,9}")

        fun converter(n: NotaEntradaDealerlenium) = NotaEntradaErp(
            id = n.id,
            erp = Erp.DEALER,
            numero = n.numero,
            dataEmissao = n.dataEmissao,
            dataLancamento = n.dataLancamento,
            nomeFornecedor = n.nomeFornecedor,
            cpfCnpjFornecedor = n.cpfCnpjFornecedor?.filter(Char::isDigit)?.takeIf { it.length == 11 || it.length == 14 },
            valorTotal = n.valorTotal,
            valorBruto = n.valorTotal,
            valorLiquido = n.valorTotal,
            parcelas = n.parcelas.size.coerceAtLeast(1),
            problema = n.problema,
        )
    }
}
