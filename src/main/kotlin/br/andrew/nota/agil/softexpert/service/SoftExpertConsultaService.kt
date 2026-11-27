package br.andrew.nota.agil.softexpert.service

import br.andrew.nota.agil.softexpert.infrastructure.SoftExpertConsultaParser
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.SoftExpertConsultaSoap
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.SoftExpertUser
import br.andrew.nota.agil.softexpert.infrastructure.interfaces.WorkflowDetails
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class SoftExpertConsultaService(
    private val soap: SoftExpertConsultaSoap,
    private val parser: SoftExpertConsultaParser,
) {
    fun usuario(matricula: String): SoftExpertUser {
        val id = identificador(matricula, "matricula")
        return parser.usuario(soap.usuario(parser.envelope("urn:admin", "getUserData", "iduser", id)))
    }

    fun instancia(workflowId: String): WorkflowDetails {
        val id = identificador(workflowId, "workflowId")
        return parser.instancia(soap.instancia(parser.envelope("urn:workflow", "getWorkflow", "WorkflowID", id)), id)
    }

    private fun identificador(valor: String, nome: String): String = valor.trim().also {
        if (it.isBlank() || it.length > 100) throw ResponseStatusException(HttpStatus.BAD_REQUEST,
            "$nome deve conter de 1 a 100 caracteres")
    }
}
