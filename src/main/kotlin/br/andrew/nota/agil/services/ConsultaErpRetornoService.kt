package br.andrew.nota.agil.services

import br.andrew.nota.agil.erp.ConsultaErpConfiguration
import br.andrew.nota.agil.erp.sap.SapConsultaErpClient
import br.andrew.nota.agil.model.erp.ConsultaErpTask
import br.andrew.nota.agil.softexpert.service.WorkFlowService
import org.springframework.stereotype.Service

@Service
class ConsultaErpRetornoService(private val workflow: WorkFlowService) {
    fun registrarResultado(task: ConsultaErpTask) {
        val resultado = checkNotNull(task.resultado)
        // Resultados sem `dados` (CNPJ_NAO_MAPEADO, falha tecnica fora do client, Dealer) usam o
        // conjunto padrao de chaves vazias: campo mapeado vira "", sem travar a entrega.
        val valores = CHAVES_DADOS_PADRAO.associateWith { "" } + resultado.dados + mapOf(
            "sucesso" to resultado.sucesso.toString(),
            "codigo" to resultado.codigo,
            "mensagem" to resultado.mensagem,
            "erp" to (task.erp?.name ?: ""),
        )
        val campos = task.camposRetorno.map { (chave, campo) ->
            campo to checkNotNull(valores[chave]) { "Campo de retorno ERP desconhecido: $chave" }
        }.toMap()
        if (campos.isNotEmpty()) workflow.atualizaFormulario(task.requisicao.workflowId, task.tabelaFormulario, campos)
        val texto = buildString {
            appendLine("Nota Agil - consulta ERP ${task.requisicao.solicitacaoId}")
            appendLine("Resultado: ${if (resultado.sucesso) "SUCESSO" else "FALHA"} | ERP: ${task.erp ?: "NAO MAPEADO"}")
            appendLine("${resultado.codigo}: ${resultado.mensagem}")
            // Reconsultas nao comentam no SE; o comentario final resume quantas houve.
            val ultimaEm = listOfNotNull(task.ultimaConsultaEm, task.ultimaFalhaTecnicaEm).maxOrNull()
            if (task.consultas > 1) appendLine("Consultas ao ERP: ${task.consultas} (ultima em $ultimaEm)")
            if (task.falhasTecnicas > 0 && resultado.codigo != ConsultaErpConfiguration.CODIGO_FALHA_TECNICA) {
                appendLine("Atencao: ${task.falhasTecnicas} consulta(s) mais recente(s) ao ERP falharam tecnicamente " +
                    "(ultima em ${task.ultimaFalhaTecnicaEm}); o resultado acima e o da ultima consulta valida, " +
                    "em ${task.ultimaConsultaEm}.")
            }
            // Chaves vazias existem para os campos mapeados; no comentario so poluiriam.
            resultado.dados.forEach { (chave, valor) -> if (valor.isNotBlank()) appendLine("$chave: $valor") }
        }
        workflow.registraComentario(task.requisicao.workflowId, task.atividadeId, texto)
    }

    fun avancar(task: ConsultaErpTask) {
        val resultado = checkNotNull(task.resultado)
        val acao = if (resultado.sucesso) task.acaoSucesso else task.acaoFalha
        val resposta = workflow.executaAtividade(
            task.requisicao.workflowId, task.atividadeId, acao, task.requisicao.activityOrder,
        )
        check(resposta.status == "SUCCESS") { "SoftExpert recusou acao $acao: ${resposta.detail}" }
    }

    companion object {
        /** Chaves de `dados` que um resultado pode trazer; ausentes valem "" no mapeamento de campos. */
        val CHAVES_DADOS_PADRAO: List<String> = SapConsultaErpClient.CHAVES_DADOS
    }
}
