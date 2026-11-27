package br.andrew.nota.agil.erp

import br.andrew.nota.agil.model.erp.Erp
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

@Component
class CatalogoConsultaErp(private val clients: List<ConsultaErpClient>) {
    fun disponiveis(): List<ErpDisponivel> = clients.filter { it.consultaPagamentoImplementada }
        .map { ErpDisponivel(it.erp, when (it.erp) { Erp.SAP -> "SAP"; Erp.DEALER -> "Dealer" }) }
        .sortedBy { it.nome }

    fun validar(erp: Erp?) {
        if (erp != null && clients.none { it.erp == erp && it.consultaPagamentoImplementada }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Consulta de pagamento no ERP $erp ainda nao implementada")
        }
    }
}

data class ErpDisponivel(val erp: Erp, val nome: String)
