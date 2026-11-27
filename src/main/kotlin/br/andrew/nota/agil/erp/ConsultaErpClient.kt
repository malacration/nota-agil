package br.andrew.nota.agil.erp

import br.andrew.nota.agil.model.erp.Erp
import br.andrew.nota.agil.model.erp.RegistrarConsultaErpRequest
import br.andrew.nota.agil.model.erp.ResultadoConsultaErp
import br.andrew.nota.agil.model.erp.normalizaCnpj
import br.andrew.nota.agil.repository.CompanyRepository
import org.springframework.stereotype.Component

interface ConsultaErpClient {
    val erp: Erp
    /** Habilitar apenas quando consultar possuir uma implementacao real. */
    val consultaPagamentoImplementada: Boolean get() = false
    fun consultar(requisicao: RegistrarConsultaErpRequest): ResultadoConsultaErp
}

// SAP: implementacao real em erp/sap/SapConsultaErpClient.kt (via sap-rovema).

@Component
class DealerConsultaErpClient : ConsultaErpClient {
    override val erp = Erp.DEALER
    override fun consultar(requisicao: RegistrarConsultaErpRequest) = ResultadoConsultaErp(
        false, "ERP_NAO_IMPLEMENTADO", "Consulta de pagamento no Dealer ainda nao implementada",
    )
}

@Component
class ConsultaErpResolver(
    configuration: ConsultaErpConfiguration,
    clients: List<ConsultaErpClient>,
    private val empresas: CompanyRepository,
) {
    private val porCnpj = configuration.cnpjs.entries.associate { normalizaCnpj(it.key) to it.value }
    private val porErp = clients.associateBy { it.erp }

    init {
        require(porCnpj.keys.all { it.length == 14 }) { "CNPJ invalido em integracao.erp.cnpjs" }
        require(porCnpj.size == configuration.cnpjs.size) { "CNPJ duplicado em integracao.erp.cnpjs" }
        require(porErp.size == clients.size) { "Mais de uma implementacao para o mesmo ERP" }
    }

    fun resolver(cnpj: String): ConsultaErpClient? {
        val normalizado = normalizaCnpj(cnpj)
        val empresa = empresas.findById(normalizado).orElse(null)
        // Nenhum ERP no cadastro e uma escolha explicita; nao restaurar o legado.
        val erp = if (empresa != null) empresa.erpPagamento else porCnpj[normalizado]
        return erp?.let { porErp[it] }
    }
}
